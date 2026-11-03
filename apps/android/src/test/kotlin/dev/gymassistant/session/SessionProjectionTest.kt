package dev.gymassistant.session

import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.compileTemplate
import dev.gymassistant.engine.initialState
import dev.gymassistant.engine.reduce
import java.io.File
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class SessionProjectionTest {
    private val root = File(checkNotNull(System.getProperty("gym.root")))
    private val at = "2026-11-03T10:00:00Z"
    private fun start(id: String = "synthetic_history_projection"): SessionStart {
        val template = File(root, "examples/templates/composite-pyramid.yaml").readText()
        val exercises = listOf("bridge-band-family.yaml", "plank-family.yaml").map {
            File(root, "examples/exercises/$it").readText()
        }
        val plan = compileTemplate(template, exercises)
        return SessionStart(id, template, exercises, plan, hashPlan(plan), at, "Europe/Berlin", "2026-11-03")
    }
    private inner class Recording(val source: SessionStart = start()) {
        val rows = mutableListOf<ActionRow>()
        var saved: SavedWorkout
        init {
            val state = reduce(source.plan, initialState(), EngineCommand.Start)
            val checkpoint = Checkpoint.from(state)
            saved = SavedWorkout(source, 1, checkpoint)
            rows += ActionRow(source.sessionId, 1, "private-start-receipt", 0,
                sessionJson.encodeToString(source), sessionJson.encodeToString(ActionPayload(null, state.events)),
                sessionJson.encodeToString(checkpoint))
        }
        fun send(kind: String, ms: Long = 0, gap: Long? = null) {
            val action = SessionAction(kind, ms, at, gap)
            val next = transition(saved, action)
            rows += ActionRow(source.sessionId, saved.revision + 1, "private-receipt-${rows.size}", saved.revision,
                sessionJson.encodeToString(action), sessionJson.encodeToString(next.payload), sessionJson.encodeToString(next.checkpoint))
            saved = SavedWorkout(source, saved.revision + 1, next.checkpoint)
        }
        fun history() = projectSession(saved, rows)
    }

    @Test fun frequentTicksDoNotBecomeHumanEventsAndDoNotExposeReceipts() {
        val recording = Recording()
        repeat(12) { recording.send("tick", 250) }
        val history = recording.history()
        assertEquals(listOf("sessionStarted", "phaseStarted"), history.events.map { it.type })
        assertEquals(3000, history.timing.observedRunMs)
        val json = history.toCanonicalJson()
        val fields = Json.parseToJsonElement(json).jsonObject
        for (key in listOf("recording", "actions", "checkpoint", "revision", "outbox")) assertFalse(key in fields)
        assertFalse("private-receipt" in json)
        assertFalse("private-start-receipt" in json)
        assertNull(history.finishedAt)
        assertNull(history.events.first().planStepId) // Session-wide event has no invented phase.
        val oneTick = Recording().apply { send("tick", 3000) }.history()
        assertEquals(oneTick, history) // Same human facts irrespective of tick fragmentation.
    }

    @Test fun partialHistoryPreservesPauseKnownUnknownGapsAndNavigationWithoutFakeRepetitions() {
        val recording = Recording()
        recording.send("tick", 4500)
        recording.send("pause", 500)
        recording.send("tick", 1200)
        recording.send("recover")
        recording.send("recover") // A second restart has no reducer event, but is human evidence.
        recording.send("resume")
        recording.send("recover", gap = 900)
        recording.send("resume")
        recording.send("tick", 750)
        recording.send("pause")
        recording.send("forward")
        recording.send("back")
        recording.send("end")
        val history = recording.history()
        assertEquals("completed", history.status)
        assertEquals("partial", history.completionKind)
        assertEquals(5750, history.timing.observedRunMs)
        assertEquals(1200, history.timing.pauseMs)
        assertEquals(900, history.timing.unobservedGapMs)
        assertEquals(2, history.timing.unknownGapCount)
        assertEquals(4000, history.timing.scheduledElapsedMs) // Cursor changed; observed time did not.
        assertEquals(3, history.events.count { it.type == "interruption" })
        assertEquals(listOf(false, false, true), history.events.filter { it.type == "interruption" }.map { it.detail?.gapDurationKnown })
        assertEquals(1, history.events.count { it.type == "phaseCompleted" })
        assertEquals("sessionEnded", history.events.last().type)
        assertEquals("partial", history.events.last().detail?.completionKind)
        assertTrue(history.events.all { it.recordedAt == at }) // No fabricated earlier timestamps.
        assertEquals(history, recording.history())
        golden("synthetic-human-partial-session", history)
    }

    @Test fun naturalEndAndAcknowledgementAreDistinctFromPartialCompletion() {
        val recording = Recording()
        recording.send("tick", 50000)
        val pending = recording.history()
        assertEquals("awaitingCompletion", pending.status)
        assertEquals("unfinished", pending.completionKind)
        assertEquals("planEndReached", pending.events.last().type)
        assertNull(pending.finishedAt)
        recording.send("finish")
        val finalized = recording.history()
        assertEquals("plannedEnd", finalized.completionKind)
        assertEquals("completed", finalized.status)
        assertEquals(at, finalized.finishedAt)
        assertEquals(50000, finalized.timing.observedRunMs)
        assertEquals(1, finalized.events.count { it.type == "planEndReached" })
        assertEquals(1, finalized.events.count { it.type == "sessionEnded" })
        assertEquals(pending.events, finalized.events.dropLast(1))
        golden("synthetic-human-completed-session", finalized)
    }

    @Test fun reachingEndAfterSkipDoesNotClaimSkippedWorkWasPerformed() {
        val recording = Recording()
        recording.send("forward")
        recording.send("tick", 50000)
        recording.send("finish")
        val history = recording.history()
        assertEquals("plannedEnd", history.completionKind)
        assertEquals(46000, history.timing.observedRunMs)
        assertEquals(50000, history.timing.scheduledElapsedMs)
        assertEquals(1, history.events.count { it.type == "skip" })
        assertEquals(8, history.events.count { it.type == "phaseCompleted" })
    }

    @Test fun discardRetainsTimingAndExplicitHumanOutcome() {
        val recording = Recording()
        recording.send("tick", 1234)
        recording.send("discard")
        val history = recording.history()
        assertEquals("discarded", history.completionKind)
        assertEquals("discarded", history.status)
        assertEquals(1234, history.timing.observedRunMs)
        assertEquals("sessionDiscarded", history.events.last().type)
    }

    @Test fun snapshotIsFrozenAndExactPlanHashInputRemainsVerifiable() {
        val recording = Recording()
        val history = recording.history()
        assertEquals(recording.source.templateYaml, history.sourcePlanSnapshot.templateYaml)
        assertEquals(recording.source.exerciseYamls, history.sourcePlanSnapshot.exerciseYamls)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(history.sourcePlanSnapshot.compiledPlanJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals("sha256:$digest", history.executionPlanHash)
        assertFalse("organizationContext" in Json.parseToJsonElement(history.toCanonicalJson()).jsonObject)
    }

    @Test fun stableEventIdsFitContractEvenForMaximumLengthSessionId() {
        val recording = Recording(start("s".repeat(128)))
        recording.send("tick", 50000)
        val ids = recording.history().events.map { it.eventId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{2,127}")) })
    }

    @Test fun overflowedInternalTimingIsRejectedInsteadOfExportingNegativeHumanTime() {
        val recording = Recording()
        recording.send("pause")
        recording.send("tick", Long.MAX_VALUE)
        recording.send("tick", 1)
        assertFails { recording.history() }
    }

    @Test fun corruptOrIncompleteInternalRecordsFailWithoutEmittingPartialHistory() {
        val recording = Recording().apply { send("tick", 1500); send("pause") }
        val rows = recording.rows
        fun rejects(changed: List<ActionRow>) = assertFails { projectSession(recording.saved, changed) }
        rejects(rows.dropLast(1))
        rejects(rows.reversed())
        rejects(rows + rows.last())
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(sequence = 8) else r })
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(operationId = rows[0].operationId) else r })
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(expectedRevision = 0) else r })
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(sessionId = "different_session") else r })
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(checkpointJson = rows[0].checkpointJson) else r })
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(payloadJson = rows[0].payloadJson) else r })
        rejects(rows.mapIndexed { i, r -> if (i == 1) r.copy(intentJson = "{}") else r })
        assertFails { projectSession(recording.saved.copy(revision = 1), rows) }
        assertFails { projectSession(recording.saved.copy(start = recording.source.copy(planHash = "wrong")), rows) }
        assertFails { projectSession(recording.saved.copy(start = recording.source.copy(formatVersion = 2)), rows) }
        assertFails { projectSession(recording.saved.copy(start = recording.source.copy(engineVersion = "future")), rows) }
    }

    private fun golden(name: String, history: SessionHistory) {
        val json = history.toCanonicalJson()
        val output = File(checkNotNull(System.getProperty("gym.history.outputs")), "$name.json")
        checkNotNull(output.parentFile).mkdirs()
        output.writeText(json)
        val expected = File(root, "examples/sessions/$name.json")
        assertTrue(expected.isFile, "Missing synthetic contract fixture; actual generated in build outputs")
        assertEquals(Json.parseToJsonElement(expected.readText()), Json.parseToJsonElement(json))
    }
}
