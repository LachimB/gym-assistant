package dev.gymassistant.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
