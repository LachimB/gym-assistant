package dev.gymassistant.session

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

@Entity(tableName = "drive_connections", indices = [Index(
    value = ["provider", "permissionId", "rootFolderId", "namespaceMarker"], unique = true,
)])
data class DriveConnectionRow(
    @PrimaryKey val connectionId: String,
    val provider: String,
    val permissionId: String,
    val email: String,
    val rootFolderId: String,
    val namespaceMarker: String,
    val createdAt: String,
    val verifiedAt: String,
)

@Entity(tableName = "session_deliveries", indices = [
    Index(value = ["connectionId", "sessionId"], unique = true), Index(value = ["sessionId"]),
], foreignKeys = [
    ForeignKey(entity = DriveConnectionRow::class, parentColumns = ["connectionId"], childColumns = ["connectionId"]),
    ForeignKey(entity = StartRow::class, parentColumns = ["sessionId"], childColumns = ["sessionId"]),
])
data class SessionDeliveryRow(
    @PrimaryKey val deliveryId: String,
    val connectionId: String,
    val sessionId: String,
    val terminalRevision: Long,
    val canonicalJson: String,
    val contentSha256: String,
    val remoteFileId: String? = null,
    val state: String = "queued",
    val attemptCount: Int = 0,
    val nextAttemptAt: String? = null,
    val errorCode: String? = null,
)

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM drive_connections WHERE connectionId = :id") suspend fun connection(id: String): DriveConnectionRow?
    @Query("SELECT * FROM drive_connections WHERE provider = :provider AND permissionId = :permission AND rootFolderId = :root AND namespaceMarker = :marker")
    suspend fun binding(provider: String, permission: String, root: String, marker: String): DriveConnectionRow?
    @Insert suspend fun insertConnection(row: DriveConnectionRow)
    @Query("UPDATE drive_connections SET email = :email, verifiedAt = :at WHERE connectionId = :id AND permissionId = :permission")
    suspend fun reverify(id: String, permission: String, email: String, at: String): Int
    @Query("SELECT * FROM session_deliveries WHERE deliveryId = :id") suspend fun delivery(id: String): SessionDeliveryRow?
    @Query("SELECT * FROM session_deliveries WHERE connectionId = :connection AND sessionId = :session")
    suspend fun forSession(connection: String, session: String): SessionDeliveryRow?
    @Query("SELECT * FROM session_deliveries WHERE sessionId = :session ORDER BY rowid DESC LIMIT 1")
    suspend fun latestForSample(session: String): SessionDeliveryRow?
    @Insert suspend fun insertDelivery(row: SessionDeliveryRow)
    @Update suspend fun updateDelivery(row: SessionDeliveryRow): Int
    @Query("UPDATE outbox SET status = 'delivered' WHERE sessionId = :session AND revision <= :revision")
    suspend fun acknowledgeThrough(session: String, revision: Long): Int
}

/** Additive only. Existing execution rows and their Room schema remain unchanged. */
val DELIVERY_MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_connections` (`connectionId` TEXT NOT NULL, `provider` TEXT NOT NULL, `permissionId` TEXT NOT NULL, `email` TEXT NOT NULL, `rootFolderId` TEXT NOT NULL, `namespaceMarker` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `verifiedAt` TEXT NOT NULL, PRIMARY KEY(`connectionId`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_connections_provider_permissionId_rootFolderId_namespaceMarker` ON `drive_connections` (`provider`, `permissionId`, `rootFolderId`, `namespaceMarker`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `session_deliveries` (`deliveryId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `terminalRevision` INTEGER NOT NULL, `canonicalJson` TEXT NOT NULL, `contentSha256` TEXT NOT NULL, `remoteFileId` TEXT, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `nextAttemptAt` TEXT, `errorCode` TEXT, PRIMARY KEY(`deliveryId`), FOREIGN KEY(`connectionId`) REFERENCES `drive_connections`(`connectionId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`sessionId`) REFERENCES `session_starts`(`sessionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_session_deliveries_connectionId_sessionId` ON `session_deliveries` (`connectionId`, `sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_deliveries_sessionId` ON `session_deliveries` (`sessionId`)")
    }
}

internal fun sha256Utf8(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** No networking or automatic enqueue/backfill. Explicit finalized-session selection only. */
internal class RoomDeliveryStore(private val database: WorkoutDatabase) : dev.gymassistant.drive.DeliveryPersistence {
    private val dao get() = database.deliveries()
    // Adds only this generated test artifact. Never acquires/releases the real unfinished slot.
    suspend fun prepareSynthetic(sample: SyntheticDeliverySample) = database.withTransaction {
        val source = sample.saved.start
        val workouts = database.workouts()
        val encoded = sessionJson.encodeToString(SessionStart.serializer(), source)
        if (workouts.start(source.sessionId) == null) {
            workouts.insertStart(StartRow(source.sessionId, encoded))
            workouts.insertCheckpoint(CheckpointRow(source.sessionId, sample.saved.revision,
                sessionJson.encodeToString(Checkpoint.serializer(), sample.saved.checkpoint)))
            for (row in sample.rows) {
                workouts.insertAction(row)
                workouts.insertOutbox(OutboxRow(source.sessionId,row.sequence,row.operationId))
            }
        } else {
            check(workouts.start(source.sessionId)?.snapshotJson == encoded && workouts.actions(source.sessionId) == sample.rows) { "Synthetic sample identity already used" }
            check(RoomSessionStore(database).load(source.sessionId) == sample.saved)
        }
    }
    suspend fun syntheticStatus(sample: SyntheticDeliverySample): String? = database.withTransaction {
        val job = dao.latestForSample(sample.saved.start.sessionId) ?: return@withTransaction null
        check(sample.permits(job)) { "Synthetic delivery differs" }
        load(job.deliveryId).second.state
    }
    suspend fun bind(connection: DriveConnectionRow): DriveConnectionRow = database.withTransaction {
        require(connection.provider == "googleDrive" && listOf(connection.connectionId, connection.permissionId,
            connection.email, connection.rootFolderId, connection.namespaceMarker, connection.createdAt, connection.verifiedAt).all { it.isNotBlank() })
        val sameId = dao.connection(connection.connectionId)
        if (sameId != null) check(sameId.copy(email = connection.email, verifiedAt = connection.verifiedAt) == connection) { "Connection identity changed" }
        val prior = dao.binding(connection.provider, connection.permissionId, connection.rootFolderId, connection.namespaceMarker)
        if (prior == null) { dao.insertConnection(connection); connection }
        else {
            check(dao.reverify(prior.connectionId, prior.permissionId, connection.email, connection.verifiedAt) == 1)
            prior.copy(email = connection.email, verifiedAt = connection.verifiedAt)
        }
    }
    suspend fun enqueue(connectionId: String, sessionId: String, deliveryId: String): SessionDeliveryRow = database.withTransaction {
        require(deliveryId.isNotBlank())
        checkNotNull(dao.connection(connectionId)) { "Unknown connection" }
        val session = checkNotNull(RoomSessionStore(database).load(sessionId))
        check(session.checkpoint.outcome != "inProgress") { "Session is not finalized" }
        val canonical = checkNotNull(RoomSessionStore(database).history(sessionId)).toCanonicalJson()
        check(canonical.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) { "Delivery exceeds limit" }
        val proposed = SessionDeliveryRow(deliveryId, connectionId, sessionId, session.revision, canonical, sha256Utf8(canonical))
        val prior = dao.forSession(connectionId, sessionId)
        if (prior == null) {
            check(dao.delivery(deliveryId) == null) { "Delivery ID already used" }
            dao.insertDelivery(proposed); proposed
        } else {
            check(prior.terminalRevision == proposed.terminalRevision && prior.canonicalJson == canonical && prior.contentSha256 == proposed.contentSha256) { "Delivery intent changed" }
            prior
        }
    }
    override suspend fun load(id: String): Pair<DriveConnectionRow, SessionDeliveryRow> = database.withTransaction {
        val delivery = checkNotNull(dao.delivery(id))
        check(delivery.terminalRevision > 0 && delivery.attemptCount >= 0 && sha256Utf8(delivery.canonicalJson) == delivery.contentSha256) { "Delivery integrity failed" }
        val source = checkNotNull(RoomSessionStore(database).load(delivery.sessionId))
        check(source.revision == delivery.terminalRevision && source.checkpoint.outcome != "inProgress") { "Source revision changed" }
        check(RoomSessionStore(database).history(delivery.sessionId)?.toCanonicalJson() == delivery.canonicalJson) { "Frozen source differs" }
        checkNotNull(dao.connection(delivery.connectionId)) to delivery
    }
    override suspend fun save(expected: SessionDeliveryRow, next: SessionDeliveryRow) = database.withTransaction {
        check(expected.state !in setOf("delivered", "conflict") || expected == next) { "Terminal delivery cannot be reopened" }
        check(dao.delivery(expected.deliveryId) == expected) { "Delivery revision changed" }
        check(next.copy(remoteFileId = expected.remoteFileId, state = expected.state, attemptCount = expected.attemptCount,
            nextAttemptAt = expected.nextAttemptAt, errorCode = expected.errorCode) == expected) { "Frozen delivery changed" }
        check(expected.remoteFileId == null || expected.remoteFileId == next.remoteFileId) { "Remote ID changed" }
        check(next.state in setOf("queued", "awaitingAuthorization", "ready", "verifying", "retry", "conflict", "delivered") &&
            next.attemptCount >= expected.attemptCount && next.attemptCount >= 0)
        check(next.errorCode == null || next.errorCode in setOf("authorization", "network", "rateLimited", "providerUnavailable", "permission", "missingRoot", "accountMismatch", "contract", "conflict", "budget"))
        check(dao.updateDelivery(next) == 1)
        if (next.state == "delivered") {
            check(next.remoteFileId != null)
            dao.acknowledgeThrough(next.sessionId, next.terminalRevision)
        }
    }
}
