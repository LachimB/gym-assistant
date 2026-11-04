package dev.gymassistant.drive

import dev.gymassistant.session.DriveConnectionRow
import dev.gymassistant.session.SessionDeliveryRow
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal interface DeliveryPersistence {
    suspend fun load(id: String): Pair<DriveConnectionRow, SessionDeliveryRow>
    suspend fun save(expected: SessionDeliveryRow, next: SessionDeliveryRow)
}
internal data class DeliveryIdentity(val permissionId: String, val email: String)
internal data class DeliveryFolder(val name: String, val marker: String, val mimeType: String)
internal data class RemoteDelivery(val id: String, val mimeType: String, val parents: List<String>, val content: ByteArray)
internal class DeliveryFailure(val reason: String, val retryAfterMs: Long? = null) : Exception(reason)
internal interface DeliveryRemote {
    suspend fun identity(): DeliveryIdentity
    suspend fun folder(id: String): DeliveryFolder
    suspend fun reserveFileId(): String
    /** Null means an exact-ID404 only. Permission/transport failures must throw. */
    suspend fun inspect(id: String, expectedParent: String): RemoteDelivery?
    /** 409 means verify the same ID; never select a replacement. */
    suspend fun create(id: String, parent: String, name: String, canonical: ByteArray)
}

internal data class PersonalDeliveryTarget4(val binding:DriveConnectionRow) {
    fun permits(connection:DriveConnectionRow,job:SessionDeliveryRow):Boolean =
        connection==binding && connection.provider=="googleDrive" && connection.rootFolderId!=SYNTHETIC_PARENT &&
        connection.namespaceMarker.matches(Regex("personal-m1-[A-Za-z0-9_-]+")) && job.connectionId==connection.connectionId &&
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(job.canonicalJson).let { value ->
            value is kotlinx.serialization.json.JsonObject && value["schemaVersion"]?.toString()=="\"1.2.0\""
        } }.getOrDefault(false)
}

/** Foreground only. No token, UI, network scheduler or execution-state mutation here. */
internal class SessionDeliverySender(
    private val local: DeliveryPersistence,
    private val remote: DeliveryRemote,
    private val expectedPersonalEmail: String,
    private val allowSynthetic: (SessionDeliveryRow) -> Boolean = { false },
    private val now: () -> Instant = Instant::now,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val jitter: (Long) -> Long = { Random.nextLong(it / 2, it + 1) },
    private val personalTarget: PersonalDeliveryTarget4? = null,
) {
    private val mutex = Mutex()
    suspend fun send(id: String): String = mutex.withLock {
        var (_, job) = local.load(id)
        if(job.state=="delivered" && personalTarget!=null) {
            val (connection, frozen)=local.load(id)
            if(expectedPersonalEmail.isBlank() || !personalTarget.permits(connection,frozen))throw DeliveryFailure("contract")
            val identity=remote.identity()
            if(identity.permissionId!=connection.permissionId || !identity.email.equals(expectedPersonalEmail,ignoreCase=true))throw DeliveryFailure("accountMismatch")
            val folder=remote.folder(connection.rootFolderId)
            if(folder.marker!=connection.namespaceMarker || folder.name!=PERSONAL_FOLDER_NAME_4 || folder.mimeType!="application/vnd.google-apps.folder")throw DeliveryFailure("missingRoot")
            val fileId=frozen.remoteFileId?:throw DeliveryFailure("contract")
            verify(remote.inspect(fileId,connection.rootFolderId)?:throw DeliveryFailure("conflict"),fileId,connection.rootFolderId,frozen.canonicalJson.toByteArray(Charsets.UTF_8))
            return@withLock "delivered"
        }
        if (job.state in setOf("delivered", "conflict")) return@withLock job.state
        val retryDelay=job.nextAttemptAt?.let { java.time.Duration.between(now(),Instant.parse(it)).toMillis() }
        // A clock correction must not defer an explicit foreground retry for days/years.
        if (retryDelay != null && retryDelay in 1..30000) return@withLock job.state
        var transientFailures = 0
        while (true) {
            var (connection, current) = local.load(id)
            job = current
            suspend fun update(next: SessionDeliveryRow) { local.save(job, next); job = next }
            suspend fun <T> request(call: suspend () -> T): T {
                update(job.copy(attemptCount = Math.addExact(job.attemptCount, 1), nextAttemptAt = null, errorCode = null))
                return call()
            }
            try {
                val personal=personalTarget
                if (expectedPersonalEmail.isBlank() || if(personal!=null) !personal.permits(connection,job) else
                    connection.provider != "googleDrive" || connection.rootFolderId != SYNTHETIC_PARENT ||
                    connection.namespaceMarker != SYNTHETIC_MARKER || !allowSynthetic(job)) throw DeliveryFailure("contract")
                val identity = request { remote.identity() }
                if (identity.permissionId != connection.permissionId ||
                    !identity.email.equals(expectedPersonalEmail, ignoreCase = true)) throw DeliveryFailure("accountMismatch")
                val folder = request { remote.folder(connection.rootFolderId) }
                if (folder.marker != connection.namespaceMarker ||
                    folder.name != (if(personal!=null) PERSONAL_FOLDER_NAME_4 else "Gym Assistant — SYNTHETIC M1 TEST — ${connection.namespaceMarker}") ||
                    folder.mimeType != "application/vnd.google-apps.folder") throw DeliveryFailure("missingRoot")
                if (job.remoteFileId == null) {
                    val reserved = request { remote.reserveFileId() }
                    if (!reserved.matches(Regex("[-A-Za-z0-9_]+"))) throw DeliveryFailure("contract")
                    update(job.copy(remoteFileId = reserved, state = "ready"))
                }
                val fileId = checkNotNull(job.remoteFileId)
                val existing = request { remote.inspect(fileId, connection.rootFolderId) }
                val bytes = job.canonicalJson.toByteArray(Charsets.UTF_8)
                if (existing == null) {
                    request { remote.create(fileId, connection.rootFolderId, (if(personal!=null) "PERSONAL-session-${job.sessionId}.json" else "SYNTHETIC-session-${job.sessionId}.json"), bytes) }
                    update(job.copy(state = "verifying"))
                    val created = request { remote.inspect(fileId, connection.rootFolderId) }
                        ?: throw DeliveryFailure("network") // Possibly not yet visible; keep this ID.
                    verify(created, fileId, connection.rootFolderId, bytes)
                } else verify(existing, fileId, connection.rootFolderId, bytes)
                update(job.copy(state = "delivered", nextAttemptAt = null, errorCode = null))
                return@withLock "delivered"
            } catch (cancelled: CancellationException) {
                throw cancelled // Persisted ID/intent survives; do not convert cancellation to success.
            } catch (failure: DeliveryFailure) {
                val transient = failure.reason in setOf("network", "rateLimited", "providerUnavailable")
                if (!transient) {
                    val state = when (failure.reason) { "authorization" -> "awaitingAuthorization"; "conflict" -> "conflict"; else -> "retry" }
                    update(job.copy(state = state, errorCode = failure.reason, nextAttemptAt = null))
                    return@withLock state
                }
                transientFailures++
                val backoff = failure.retryAfterMs?.coerceIn(0, 30000)
                    ?: jitter(1000L shl (transientFailures - 1)).coerceIn(0, 30000)
                update(job.copy(state = "retry", errorCode = failure.reason, nextAttemptAt = now().plusMillis(backoff).toString()))
                if (transientFailures >= 3) return@withLock "retry"
                wait(backoff)
            }
        }
        @Suppress("UNREACHABLE_CODE") "retry"
    }
    private fun verify(remote: RemoteDelivery, id: String, parent: String, bytes: ByteArray) {
        if (remote.id != id || remote.mimeType != "application/json" || remote.parents != listOf(parent) ||
            !remote.content.contentEquals(bytes)) throw DeliveryFailure("conflict")
    }
}
