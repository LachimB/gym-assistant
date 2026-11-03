package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import android.database.Cursor
import org.json.JSONObject
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
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, databaseName).addMigrations(DELIVERY_MIGRATION_1_2).build()
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

    @Test fun humanHistoryIsReadOnlyStableAcrossReopenAndDuplicateRetry() = runBlocking {
        val original = snapshot()
        var saved = store.start(original, "start")
        repeat(12) { index -> saved = store.commit(original.sessionId, saved.revision, "tick-$index", action("tick", 250)) }
        val beforeEnd = saved
        saved = store.commit(original.sessionId, saved.revision, "end", action("end"))
        val rows = database.workouts().actions(original.sessionId)
        val pending = database.workouts().outbox(original.sessionId)
        val history = checkNotNull(store.history(original.sessionId))
        assertEquals(listOf("sessionStarted", "phaseStarted", "sessionEnded"), history.events.map { it.type })
        assertEquals(3000L, history.timing.observedRunMs)
        assertEquals("partial", history.completionKind)
        assertEquals(saved, store.commit(original.sessionId, beforeEnd.revision, "end", action("end")))
        database.close()
        reopen()
        assertEquals(history.toCanonicalJson(), checkNotNull(store.history(original.sessionId)).toCanonicalJson())
        assertEquals(rows, database.workouts().actions(original.sessionId))
        assertEquals(pending, database.workouts().outbox(original.sessionId))
        assertEquals(saved, store.load(original.sessionId))
        assertNull(store.history("missing-session"))
    }

    @Test fun humanHistoryRejectsCorruptReceiptWithoutChangingSavedDataOrOutbox() = runBlocking {
        val original = snapshot()
        val first = store.start(original, "start")
        val saved = store.commit(original.sessionId, first.revision, "tick", action("tick", 1200))
        val pending = database.workouts().outbox(original.sessionId)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE actions SET payloadJson = ? WHERE sessionId = ? AND sequence = 2",
            arrayOf("{}", original.sessionId))
        var rejected = false
        try { store.history(original.sessionId) } catch (_: Exception) { rejected = true }
        assertTrue("Corrupt receipts must not produce plausible human history", rejected)
        assertEquals(saved, store.load(original.sessionId))
        assertEquals(pending, database.workouts().outbox(original.sessionId))
        assertEquals("{}", database.workouts().actions(original.sessionId).last().payloadJson)
    }

    private fun originalRows(): Map<String, List<List<Any?>>> = listOf(
        "session_starts", "checkpoints", "actions", "unfinished_slot", "outbox",
    ).associateWith { table ->
        database.openHelper.writableDatabase.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        else -> cursor.getString(index)
                    }
                })
            }
        }
    }

    private fun legacyHelper(rows: Map<String, List<List<Any?>>>): SupportSQLiteOpenHelper {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("dev.gymassistant.session.WorkoutDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    for ((table, records) in rows) for (record in records) {
                        db.execSQL("INSERT INTO `$table` VALUES (${record.joinToString(",") { "?" }})", record.toTypedArray())
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) { error("Unexpected fixture upgrade") }
            }).build())
    }

    @Test fun versionOneMigrationPreservesEveryOriginalRowAndPausedRecovery() = runBlocking {
        val completed = snapshot()
        var done = store.start(completed, "completed-start")
        done = store.commit(completed.sessionId, done.revision, "completed-end", action("end", 1100))
        val paused = snapshot()
        var saved = store.start(paused, "paused-start")
        saved = store.commit(paused.sessionId, saved.revision, "paused-tick", action("tick", 2500))
        saved = store.commit(paused.sessionId, saved.revision, "paused-pause", action("pause", 100))
        val before = originalRows()
        val human = store.history(paused.sessionId)
        database.close()
        context.deleteDatabase(databaseName)
        legacyHelper(before).use { assertEquals(1, it.writableDatabase.version) }
        reopen()
        assertEquals(2, database.openHelper.writableDatabase.version)
        assertEquals(before, originalRows())
        assertEquals(saved, store.unfinished())
        assertEquals(done, store.load(completed.sessionId))
        assertEquals(human, store.history(paused.sessionId))
        assertEquals(0, database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM session_deliveries").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM drive_connections").use { it.moveToFirst(); it.getInt(0) })
        database.close(); reopen()
        assertEquals(before, originalRows())
        assertEquals(saved, store.unfinished())
    }

    @Test fun failedMigrationRollsBackNewTablesAndKeepsVersionOneData() = runBlocking {
        val first = store.start(snapshot(), "start")
        store.commit(first.start.sessionId, first.revision, "pause", action("pause", 100))
        val before = originalRows()
        database.close(); context.deleteDatabase(databaseName)
        legacyHelper(before).use { it.writableDatabase }
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, databaseName)
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    DELIVERY_MIGRATION_1_2.migrate(db)
                    error("Injected migration failure")
                }
            }).build()
        var failed = false
        try { database.openHelper.writableDatabase } catch (_: Exception) { failed = true }
        assertTrue(failed)
        database.close()
        legacyHelper(before).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1, db.version)
            assertEquals(0, db.query("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('session_deliveries','drive_connections')").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(before.getValue("actions").size, db.query("SELECT COUNT(*) FROM actions").use { it.moveToFirst(); it.getInt(0) })
        }
        reopen()
        assertEquals(before, originalRows())
    }

    @Test fun deliveryIntentIsFrozenDeduplicatedAndOnlyVerifiedRevisionAcknowledged() = runBlocking {
        val source = snapshot()
        var saved = store.start(source, "start")
        val deliveryStore = RoomDeliveryStore(database)
        val binding = DriveConnectionRow("connection", "googleDrive", "personal-id", "fixture@example.test", "root", "synthetic", utc, utc)
        deliveryStore.bind(binding)
        assertFails { deliveryStore.enqueue(binding.connectionId, source.sessionId, "job") }
        saved = store.commit(source.sessionId, saved.revision, "end", action("end", 1234))
        val job = deliveryStore.enqueue(binding.connectionId, source.sessionId, "job")
        assertEquals(job, deliveryStore.enqueue(binding.connectionId, source.sessionId, "retry-job"))
        assertTrue(database.workouts().outbox(source.sessionId).all { it.status == "pending" })
        assertEquals(store.history(source.sessionId)?.toCanonicalJson(), job.canonicalJson)
        assertFalse(job.canonicalJson.contains("operationId"))
        deliveryStore.save(job, job.copy(remoteFileId = "reserved", state = "ready"))
        val reserved = deliveryStore.load(job.deliveryId).second
        assertFails { deliveryStore.save(reserved, reserved.copy(remoteFileId = "different")) }
        assertFails { deliveryStore.bind(binding.copy(permissionId = "another-account")) }
        database.close(); reopen()
        val reopened = RoomDeliveryStore(database)
        assertEquals(reserved, reopened.load(job.deliveryId).second)
        reopened.save(reserved, reserved.copy(state = "delivered"))
        assertTrue(database.workouts().outbox(source.sessionId).all { it.status == "delivered" })
        assertEquals(saved, store.load(source.sessionId))
        assertEquals(2, database.workouts().actions(source.sessionId).size)
    }

    @Test fun generatedDeliverySampleDoesNotChangeTheUnfinishedWorkout() = runBlocking {
        val source=snapshot()
        var paused=store.start(source,"real-fixture-start")
        paused=store.commit(source.sessionId,paused.revision,"pause",action("pause",200))
        val originalActions=database.workouts().actions(source.sessionId)
        val originalOutbox=database.workouts().outbox(source.sessionId)
        val sample=SyntheticDeliverySample.create(source.templateYaml,source.exerciseYamls)
        val local=RoomDeliveryStore(database)
        local.prepareSynthetic(sample); local.prepareSynthetic(sample)
        assertEquals(paused,store.unfinished())
        assertEquals(originalActions,database.workouts().actions(source.sessionId))
        assertEquals(originalOutbox,database.workouts().outbox(source.sessionId))
        assertEquals(sample.saved,store.load(sample.saved.start.sessionId))
        assertEquals(sample.canonicalJson,store.history(sample.saved.start.sessionId)?.toCanonicalJson())
        assertEquals(3,database.workouts().actions(sample.saved.start.sessionId).size)
        assertNull(local.syntheticStatus(sample))
        val binding=local.bind(DriveConnectionRow("generated-binding","googleDrive","personal-id","fixture@example.test","root","synthetic",utc,utc))
        local.enqueue(binding.connectionId,sample.saved.start.sessionId,"generated-job")
        assertEquals("queued",local.syntheticStatus(sample))
        database.close(); reopen()
        assertEquals("queued",RoomDeliveryStore(database).syntheticStatus(sample))
        assertEquals(paused,store.unfinished())
    }

    private suspend fun assertFails(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalStateException) { failed = true }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected a rejected operation", failed)
    }
}
