package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.compileTemplate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSessionStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WorkoutDatabase
    private lateinit var databaseName: String
    private lateinit var store: RoomSessionStore
    private val utc = "2026-11-02T10:00:00Z"

    @Before fun prepare() {
        databaseName = "recovery-test-${UUID.randomUUID()}.db"
        reopen()
    }

    private fun reopen() {
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, databaseName).build()
        store = RoomSessionStore(database)
    }

    @After fun cleanup() {
        database.close()
        // Only this test's disposable, synthetic database; never the app's workouts-v1.db.
        context.deleteDatabase(databaseName)
    }

    private fun snapshot(id: String = UUID.randomUUID().toString()): SessionStart {
        val template = context.assets.open("composite-pyramid.yaml").bufferedReader().use { it.readText() }
        val exercises = listOf("bridge-band-family.yaml", "plank-family.yaml").map { name ->
            context.assets.open(name).bufferedReader().use { it.readText() }
        }
        val plan = compileTemplate(template, exercises)
        return SessionStart(id, template, exercises, plan, hashPlan(plan), utc, "Europe/Berlin", "2026-11-02")
    }

    private fun action(kind: String, elapsed: Long = 0) = SessionAction(kind, elapsed, utc)

    @Test fun frozenSnapshotAndCheckpointSurviveDatabaseReopen() = runBlocking {
        val original = snapshot()
        var saved = store.start(original, "start")
        saved = store.commit(original.sessionId, saved.revision, "tick", action("tick", 4500))
        saved = store.commit(original.sessionId, saved.revision, "pause", action("pause", 500))
        saved = store.commit(original.sessionId, saved.revision, "paused", action("tick", 1200))
        assertEquals(5000L, saved.state.scheduledElapsedMs)
        assertEquals(5000L, saved.state.observedRunMs)
        assertEquals(1200L, saved.state.pauseMs)
        assertEquals(setOf(0), saved.state.completedPhaseIndexes)
        database.close()
        reopen()
        val restored = checkNotNull(store.unfinished())
        assertEquals(original, restored.start)
        assertEquals(saved, restored)
        // Simulate an externally edited library revision; recovery never accepts it as input.
        val changed = original.copy(templateYaml = original.templateYaml.replace("revision_demo_v1", "external_revision"))
        assertNotEquals(changed.templateYaml, restored.start.templateYaml)
        val recovered = store.commit(original.sessionId, restored.revision, "recover", action("recover"))
        assertEquals(SessionStatus.RecoveredPaused, recovered.state.status)
        assertEquals(restored.state.scheduledElapsedMs, recovered.state.scheduledElapsedMs)
        assertEquals(restored.state.observedRunMs, recovered.state.observedRunMs)
        assertEquals(restored.state.pauseMs, recovered.state.pauseMs)
        assertEquals(1, recovered.checkpoint.unknownGapCount)
        assertEquals(0L, recovered.state.unobservedGapMs)
    }

    @Test fun startAndCommitRollbackIncludeEventsSlotCheckpointAndOutbox() = runBlocking {
        val original = snapshot()
        val failing = RoomSessionStore(database) { throw IllegalStateException("Injected write failure") }
        assertFails { failing.start(original, "start-failure") }
        assertNull(store.load(original.sessionId))
        assertNull(store.unfinished())
        assertTrue(database.workouts().actions(original.sessionId).isEmpty())
        assertTrue(database.workouts().outbox(original.sessionId).isEmpty())
        val saved = store.start(original, "start")
        assertFails { failing.commit(original.sessionId, saved.revision, "failed-action", action("tick", 4000)) }
        assertEquals(saved, store.load(original.sessionId))
        assertEquals(1, database.workouts().actions(original.sessionId).size)
        assertEquals(1, database.workouts().outbox(original.sessionId).size)
    }

    @Test fun duplicateOperationAndLostAcknowledgementDoNotDuplicateHistory() = runBlocking {
        val original = snapshot()
        val first = store.start(original, "start")
        assertEquals(first, store.start(original, "start"))
        val second = store.commit(original.sessionId, first.revision, "tick", action("tick", 1000))
        val third = store.commit(original.sessionId, second.revision, "pause", action("pause", 100))
        assertEquals(second, store.commit(original.sessionId, first.revision, "tick", action("tick", 1000)))
        assertEquals(third, store.load(original.sessionId))
        assertEquals(3, database.workouts().actions(original.sessionId).size)
        assertEquals(3, database.workouts().outbox(original.sessionId).size)
        assertFails { store.commit(original.sessionId, first.revision, "tick", action("tick", 2000)) }
        assertFails { store.commit(original.sessionId, first.revision, "stale", action("pause")) }
    }

    @Test fun secondUnfinishedSessionIsRejectedAndDiscardRetainsPartialHistory() = runBlocking {
        val original = snapshot()
        val first = store.start(original, "start")
        assertFails { store.start(snapshot(), "other-start") }
        val progress = store.commit(original.sessionId, first.revision, "tick", action("tick", 1234))
        val discarded = store.commit(original.sessionId, progress.revision, "discard", action("discard"))
        assertNull(store.unfinished())
        assertEquals("discarded", discarded.checkpoint.outcome)
        assertEquals(1234L, checkNotNull(store.load(original.sessionId)).state.observedRunMs)
        assertEquals(3, database.workouts().actions(original.sessionId).size)
        assertEquals(3, database.workouts().outbox(original.sessionId).size)
        store.start(snapshot(), "new-start")
        assertFails { store.commit(original.sessionId, discarded.revision, "rewrite", action("tick", 1)) }
    }

    @Test fun completeAndPartialEndRemainDurableAfterRestart() = runBlocking {
        val original = snapshot()
        var saved = store.start(original, "start")
        saved = store.commit(original.sessionId, saved.revision, "finish-time", action("tick", 50000))
        assertEquals(SessionStatus.Completed, saved.state.status)
        assertNotNull(store.unfinished()) // User still needs to acknowledge completion.
        saved = store.commit(original.sessionId, saved.revision, "finish", action("finish"))
        assertNull(store.unfinished())
        assertEquals("completed", saved.checkpoint.outcome)
        val next = store.start(snapshot(), "next-start")
        val partial = store.commit(next.start.sessionId, next.revision, "partial-end", action("end", 1200))
        database.close()
        reopen()
        assertEquals(saved, store.load(original.sessionId))
        assertEquals(partial, store.load(next.start.sessionId))
        assertEquals(1200L, partial.state.observedRunMs)
        assertEquals("completed", partial.checkpoint.outcome)
        assertTrue(partial.state.completedPhaseIndexes.isEmpty())
    }

    @Test fun driverRequiresExplicitResumeAndNeverCountsRestartOrLongSampleGapAsWork() = runBlocking {
        var now = 0L
        val driver = SessionDriver(store, { now }, { utc })
        val original = snapshot()
        driver.start(original)
        now = 1000
        val first = driver.send("tick")
        val restarted = SessionDriver(store, { now }, { utc })
        now = 100000 // Even an enormous restart interval has no inferred duration.
        val recovered = checkNotNull(restarted.restore())
        assertTrue(restarted.needsRecovery)
        assertFalse(restarted.shouldTick())
        assertEquals(first.state.observedRunMs, recovered.state.observedRunMs)
        assertFails { restarted.send("forward") }
        restarted.send("resume")
        now += 500
        assertEquals(1500L, restarted.send("tick").state.observedRunMs)
        now += 5000 // Process-local stall: known monotonic gap, no exercise progression.
        val stalled = restarted.send("tick")
        assertEquals(1500L, stalled.state.observedRunMs)
        assertEquals(5000L, stalled.state.unobservedGapMs)
        assertTrue(restarted.needsRecovery)
        val payload = sessionJson.decodeFromString<ActionPayload>(database.workouts().actions(original.sessionId).last().payloadJson)
        assertEquals("unobservedGap", payload.engineEvents.single().type)
    }

    @Test fun failedDriverCommitDoesNotAdvanceVisibleState() = runBlocking {
        var fail = false
        var now = 0L
        val driver = SessionDriver(RoomSessionStore(database) { if (fail) error("Injected failure") }, { now }, { utc })
        val original = snapshot()
        val saved = driver.start(original)
        now = 1000
        fail = true
        assertFails { driver.send("pause") }
        assertEquals(saved, driver.saved)
        assertEquals(saved, store.load(original.sessionId))
        fail = false
        driver.restore()
        assertTrue(driver.needsRecovery)
        assertEquals(0L, checkNotNull(driver.saved).state.observedRunMs)
    }

    @Test fun rejectsInconsistentSourceSnapshotHashAndDateBeforeWriting() = runBlocking {
        val original = snapshot()
        assertFails { store.start(original.copy(planHash = "wrong"), "hash") }
        assertFails { store.start(original.copy(localTrainingDate = "2026-11-01"), "date") }
        assertFails { store.start(original.copy(templateYaml = original.templateYaml.replace("4000", "5000")), "plan") }
        assertNull(store.unfinished())
    }

    @Test fun timeZoneDateAndDstRemainExplicitWithoutReinterpretingUtc() = runBlocking {
        val original = snapshot().copy(startedAt = "2026-10-24T23:30:00Z", localTrainingDate = "2026-10-25")
        original.validate() // Local date differs from UTC date.
        original.copy(startedAt = "2026-10-25T00:30:00Z").validate()
        original.copy(startedAt = "2026-10-25T01:30:00Z").validate() // Repeated local hour.
        val saved = store.start(original, "dst-start")
        database.close()
        reopen()
        assertEquals(saved.start, checkNotNull(store.unfinished()).start)
    }

    @Test fun clockReversalIsAnUnknownGapWithoutAutomaticContinuation() = runBlocking {
        var now = 1000L
        val driver = SessionDriver(store, { now }, { utc })
        driver.start(snapshot())
        now = 500
        val recovered = driver.send("tick")
        assertEquals(0L, recovered.state.observedRunMs)
        assertEquals(0L, recovered.state.unobservedGapMs)
        assertEquals(1, recovered.checkpoint.unknownGapCount)
        assertTrue(driver.needsRecovery)
    }

    private suspend fun assertFails(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalStateException) { failed = true }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected a rejected operation", failed)
    }
}
