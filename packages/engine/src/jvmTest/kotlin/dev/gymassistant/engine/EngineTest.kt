package dev.gymassistant.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class EngineTest {
    private val root = File("../../examples")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun goldenTimelineIsFiftySeconds() {
        val plan = loadPlan()
        assertEquals(50_000L, plan.expectedDurationMs)
        assertEquals(50_000L, plan.phases.sumOf { it.endMs - it.startMs })
        val expected = json.decodeFromString<ExpectedTimeline>(
            root.resolve("expected/composite-pyramid.timeline.json").readText(),
        )
        assertEquals(expected.expectedDurationMs, plan.expectedDurationMs)
        assertEquals(expected.phases, timelineProjection(plan))
    }

    @Test
    fun pauseSkipRewindAndUnobservedGapStaySeparate() {
        val plan = loadPlan()
        val state = reduceAll(
            plan,
            listOf(
                EngineCommand.Start,
                EngineCommand.Advance(1_000),
                EngineCommand.Pause,
                EngineCommand.Advance(5_000),
                EngineCommand.Resume,
                EngineCommand.Advance(3_000),
                EngineCommand.StepForward,
                EngineCommand.StepBackward,
                EngineCommand.NoteUnobserved(8_000),
                EngineCommand.Resume,
            ),
        )
        val view = snapshot(plan, state)
        assertEquals("running", view.status)
        assertEquals(4_000L, view.scheduledElapsedMs)
        assertEquals(4_000L, view.observedRunMs)
        assertEquals(5_000L, view.pauseMs)
        assertEquals(8_000L, view.unobservedGapMs)
        assertEquals(1, view.activePhaseIndex)
        assertEquals(listOf(0), view.completedPhaseIndexes)
        assertEquals(
            listOf(
                "sessionStarted",
                "phaseStarted",
                "pause",
                "resume",
                "phaseCompleted",
                "phaseStarted",
                "skip",
                "phaseStarted",
                "rewind",
                "phaseStarted",
                "unobservedGap",
                "resumeAfterGap",
            ),
            state.events.map { it.type },
        )
    }

    @Test
    fun uninterruptedAdvanceCompletesEveryPhase() {
        val plan = loadPlan()
        val state = reduceAll(plan, listOf(EngineCommand.Start, EngineCommand.Advance(50_000)))
        assertEquals(SessionStatus.Completed, state.status)
        assertEquals(plan.phases.indices.toSet(), state.completedPhaseIndexes)
        assertEquals(50_000L, state.observedRunMs)
        assertEquals(0L, state.pauseMs)
        assertEquals(false, state.completionAcknowledged)
    }

    @Test
    fun completedStateIgnoresLiveCommands() {
        val plan = loadPlan()
        val finished = reduceAll(plan, listOf(EngineCommand.Start, EngineCommand.Advance(50_000)))
        assertEquals(SessionStatus.Completed, finished.status)
        listOf(
            EngineCommand.Start,
            EngineCommand.Pause,
            EngineCommand.Resume,
            EngineCommand.Advance(1_000),
            EngineCommand.StepForward,
            EngineCommand.StepBackward,
            EngineCommand.NoteUnobserved(2_000),
        ).forEach { command ->
            assertEquals(finished, reduce(plan, finished, command))
        }
    }

    @Test
    fun acknowledgePreservesTimingAndTrace() {
        val plan = loadPlan()
        val running = reduceAll(plan, listOf(EngineCommand.Start, EngineCommand.Advance(1_000)))
        assertEquals(running, reduce(plan, running, EngineCommand.Acknowledge))

        val finished = reduceAll(
            plan,
            listOf(EngineCommand.Start, EngineCommand.StepForward, EngineCommand.Advance(50_000)),
        )
        assertEquals(SessionStatus.Completed, finished.status)
        val acknowledged = reduce(plan, finished, EngineCommand.Acknowledge)
        assertEquals(true, acknowledged.completionAcknowledged)
        assertEquals(SessionStatus.Completed, acknowledged.status)
        assertEquals(finished.scheduledElapsedMs, acknowledged.scheduledElapsedMs)
        assertEquals(finished.observedRunMs, acknowledged.observedRunMs)
        assertEquals(finished.pauseMs, acknowledged.pauseMs)
        assertEquals(finished.unobservedGapMs, acknowledged.unobservedGapMs)
        assertEquals(finished.completedPhaseIndexes, acknowledged.completedPhaseIndexes)
        assertEquals(finished.events, acknowledged.events)
        assertEquals(50_000L, acknowledged.scheduledElapsedMs)
        assertTrue(acknowledged.observedRunMs < acknowledged.scheduledElapsedMs)
        assertEquals(acknowledged, reduce(plan, acknowledged, EngineCommand.Acknowledge))
    }

    @Test
    fun endEarlyAcknowledgesPartialRunWithoutCompletingRemainingPhases() {
        val plan = loadPlan()
        val running = reduceAll(plan, listOf(EngineCommand.Start, EngineCommand.Advance(4_174)))
        val ended = reduce(plan, running, EngineCommand.EndEarly)
        assertEquals(SessionStatus.Completed, ended.status)
        assertEquals(true, ended.completionAcknowledged)
        assertEquals(running.scheduledElapsedMs, ended.scheduledElapsedMs)
        assertEquals(running.observedRunMs, ended.observedRunMs)
        assertEquals(running.pauseMs, ended.pauseMs)
        assertEquals(running.unobservedGapMs, ended.unobservedGapMs)
        assertEquals(running.completedPhaseIndexes, ended.completedPhaseIndexes)
        assertEquals(running.events, ended.events)
        assertTrue(ended.completedPhaseIndexes.size < plan.phases.size)
        assertEquals(ended, reduce(plan, ended, EngineCommand.Advance(10_000)))
        assertEquals(ended, reduce(plan, ended, EngineCommand.EndEarly))

        val paused = reduce(plan, running, EngineCommand.Pause)
        val endedPaused = reduce(plan, paused, EngineCommand.EndEarly)
        assertEquals(SessionStatus.Completed, endedPaused.status)
        assertEquals(true, endedPaused.completionAcknowledged)
        assertEquals(paused.scheduledElapsedMs, endedPaused.scheduledElapsedMs)
        assertEquals(paused.observedRunMs, endedPaused.observedRunMs)
        assertEquals(paused.pauseMs, endedPaused.pauseMs)
        assertEquals(paused.unobservedGapMs, endedPaused.unobservedGapMs)
        assertEquals(paused.completedPhaseIndexes, endedPaused.completedPhaseIndexes)
        assertEquals(endedPaused, reduce(plan, endedPaused, EngineCommand.Advance(5_000)))

        assertEquals(initialState(), reduce(plan, initialState(), EngineCommand.EndEarly))

        val full = reduceAll(plan, listOf(EngineCommand.Start, EngineCommand.Advance(50_000)))
        assertEquals(50_000L, full.scheduledElapsedMs)
        assertEquals(50_000L, full.observedRunMs)
        assertEquals(plan.phases.indices.toSet(), full.completedPhaseIndexes)
        assertEquals(false, full.completionAcknowledged)
        assertEquals(full, reduce(plan, full, EngineCommand.EndEarly))
    }

    private fun loadPlan(): CompiledPlan {
        val template = root.resolve("templates/composite-pyramid.yaml").readText()
        val exercises = listOf(
            root.resolve("exercises/bridge-band-family.yaml").readText(),
            root.resolve("exercises/plank-family.yaml").readText(),
        )
        return compileTemplate(template, exercises)
    }

}

@Serializable
private data class ExpectedTimeline(
    val expectedDurationMs: Long,
    val phases: List<TimelinePhase>,
)
