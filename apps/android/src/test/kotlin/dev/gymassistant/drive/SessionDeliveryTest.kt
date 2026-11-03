package dev.gymassistant.drive

import dev.gymassistant.session.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SessionDeliveryTest {
    private val at = "2026-11-03T10:00:00Z"
    private val marker = "c1d2a450-f45f-43c5-846e-df40845bd21e"
    private inner class Fixture : DeliveryPersistence, DeliveryRemote {
        var connection = DriveConnectionRow("binding", "googleDrive", "personal-id", "personal@example.test", SYNTHETIC_PARENT, marker, at, at)
        var job = SessionDeliveryRow("delivery", "binding", "sample", 3, "synthetic bytes", sha256Utf8("synthetic bytes"))
        var identity = DeliveryIdentity("personal-id", "personal@example.test")
        var folder = DeliveryFolder("Gym Assistant — SYNTHETIC M1 TEST — $marker", marker, "application/vnd.google-apps.folder")
        val files = mutableMapOf<String, RemoteDelivery>()
        val calls = mutableListOf<String>()
        var creates = 0
        var reservations = 0
        var loseCreateAck = false
        var transientIdentity = 0
        var identityFailure: String? = null
        var inspectFailure: String? = null
        var mutateReadback = false
        var cancelAfterCreate = false
        var failSaveAfterCreate = false
        var time = Instant.parse(at)
        val waits = mutableListOf<Long>()
        fun sender(allow: Boolean = true) = SessionDeliverySender(this, this, "personal@example.test",
            allowSynthetic = { allow }, now = { time }, wait = { waits += it; time = time.plusMillis(it) }, jitter = { it })
        override suspend fun load(id: String) = connection to job
        override suspend fun save(expected: SessionDeliveryRow, next: SessionDeliveryRow) {
            check(job == expected)
            if (failSaveAfterCreate && creates > 0) { failSaveAfterCreate = false; error("Local commit failure") }
            job = next
        }
        override suspend fun identity(): DeliveryIdentity {
            calls += "identity"
            if (transientIdentity-- > 0) throw DeliveryFailure("network")
            identityFailure?.let { throw DeliveryFailure(it) }
            return identity
        }
        override suspend fun folder(id: String): DeliveryFolder { calls += "folder"; return folder }
        override suspend fun reserveFileId(): String { calls += "reserve"; reservations++; return "provider-id" }
        override suspend fun inspect(id: String, expectedParent: String): RemoteDelivery? {
            calls += "inspect:$id"
            inspectFailure?.let { throw DeliveryFailure(it) }
            return files[id]?.let { if (mutateReadback) it.copy(content = "external change".toByteArray()) else it }
        }
        override suspend fun create(id: String, parent: String, name: String, canonical: ByteArray) {
            calls += "create:$id"; creates++
            assertEquals(id, job.remoteFileId, "Remote ID must be durable before create")
            files.putIfAbsent(id, RemoteDelivery(id, "application/json", listOf(parent), canonical))
            if (cancelAfterCreate) { cancelAfterCreate = false; throw CancellationException("Injected cancellation") }
            if (loseCreateAck) { loseCreateAck = false; throw DeliveryFailure("network") }
        }
    }
    @Test fun lostAcknowledgementVerifiesSameIdWithoutAnotherCreate() = runBlocking {
        val f = Fixture(); f.loseCreateAck = true
        assertEquals("delivered", f.sender().send("delivery"))
        assertEquals(1, f.reservations); assertEquals(1, f.creates); assertEquals(1, f.files.size)
        assertEquals("provider-id", f.job.remoteFileId); assertEquals(1, f.waits.size)
        val count = f.calls.size
        assertEquals("delivered", f.sender().send("delivery")); assertEquals(count, f.calls.size)
    }
    @Test fun restartAfterCancellationUsesPersistedIdAndIntent() = runBlocking {
        val f = Fixture(); f.cancelAfterCreate = true
        var cancelled = false
        try { f.sender().send("delivery") } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled); assertNotEquals("delivered", f.job.state)
        assertEquals("delivered", f.sender().send("delivery"))
        assertEquals(1, f.reservations); assertEquals(1, f.creates)
    }
    @Test fun failedLocalAcknowledgementDoesNotRequireNewRemoteFile() = runBlocking {
        val f = Fixture(); f.failSaveAfterCreate = true
        var rejected = false
        try { f.sender().send("delivery") } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected); assertNotEquals("delivered", f.job.state)
        assertEquals("delivered", f.sender().send("delivery")); assertEquals(1, f.creates)
    }
    @Test fun accountMismatchCannotReadDestination() = runBlocking {
        val f = Fixture(); f.identity = f.identity.copy(permissionId = "other")
        assertEquals("retry", f.sender().send("delivery"))
        assertEquals(listOf("identity"), f.calls); assertEquals("accountMismatch", f.job.errorCode)
    }
    @Test fun wrongFolderMarkerStopsBeforeIdReservation() = runBlocking {
        val f = Fixture(); f.folder = f.folder.copy(marker = "wrong")
        f.sender().send("delivery")
        assertEquals(listOf("identity", "folder"), f.calls); assertEquals("missingRoot", f.job.errorCode)
    }
    @Test fun liveSenderFailsClosedWithoutExactSyntheticEligibility() = runBlocking {
        val f = Fixture(); assertEquals("retry", f.sender(allow = false).send("delivery")); assertTrue(f.calls.isEmpty())
        assertEquals("contract", f.job.errorCode)
    }
    @Test fun permissionFailureIsNotAbsenceAndNeverCreatesReplacement() = runBlocking {
        val f = Fixture(); f.job = f.job.copy(remoteFileId = "provider-id", state = "ready"); f.inspectFailure = "permission"
        f.sender().send("delivery")
        assertEquals(0, f.creates); assertEquals(0, f.reservations); assertEquals("permission", f.job.errorCode)
    }
    @Test fun externalChangeIsPreservedAndNeverAcknowledged() = runBlocking {
        val f = Fixture(); f.mutateReadback = true
        assertEquals("conflict", f.sender().send("delivery"))
        assertEquals(1, f.creates); assertEquals("synthetic bytes", String(f.files.getValue("provider-id").content))
        val before = f.calls.size; assertEquals("conflict", f.sender().send("delivery")); assertEquals(before, f.calls.size)
    }
    @Test fun transientRetriesAreBoundedAndWaitSchedulePersists() = runBlocking {
        val f = Fixture(); f.transientIdentity = 100
        assertEquals("retry", f.sender().send("delivery")); assertEquals(3, f.calls.size)
        assertEquals(listOf(1000L, 2000L), f.waits); assertEquals("network", f.job.errorCode)
        assertNotNull(f.job.nextAttemptAt)
        f.sender().send("delivery"); assertEquals(3, f.calls.size)
    }
    @Test fun expiredAuthorizationAndBudgetAreNotAutomaticallyRetried() = runBlocking {
        for (reason in listOf("authorization", "budget")) {
            val f = Fixture(); f.identityFailure = reason
            f.sender().send("delivery"); assertEquals(1, f.calls.size); assertTrue(f.waits.isEmpty())
            assertEquals(reason, f.job.errorCode); assertEquals(0, f.creates)
        }
    }
}
