package dev.gymassistant.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockedExecutionTest {
    private var now = 0L
    private fun clock(): ClockedExecution {
        val root = File("../../examples")
        val plan = compileTemplate(
            root.resolve("templates/composite-pyramid.yaml").readText(),
            listOf("bridge-band-family", "plank-family").map {
                root.resolve("exercises/$it.yaml").readText()
            },
        )
        return ClockedExecution(plan) { now }
    }

    @Test
    fun pauseAndResumeSettleElapsedTimeBetweenTimerCallbacks() {
        val clock = clock()
        now = 10_000 // Waiting before Start must not count.
        clock.send(EngineCommand.Start)
        now = 11_025
        val paused = clock.send(EngineCommand.Pause) // No intervening timer tick.
        assertEquals(1_025L, paused.scheduledElapsedMs)
        assertEquals(1_025L, paused.observedRunMs)
        now = 14_025
        assertEquals(3_000L, clock.tick().pauseMs)
        now = 16_025
        val resumed = clock.send(EngineCommand.Resume)
        assertEquals(5_000L, resumed.pauseMs)
        assertEquals(1_025L, resumed.scheduledElapsedMs)
        assertEquals(1_025L, resumed.observedRunMs)
        assertEquals(emptySet(), resumed.completedPhaseIndexes)
        now = 19_000
        val boundary = clock.tick()
        assertEquals(4_000L, boundary.scheduledElapsedMs)
        assertEquals(4_000L, boundary.observedRunMs)
        assertEquals(5_000L, boundary.pauseMs)
        assertEquals(0L, boundary.unobservedGapMs)
        assertEquals(setOf(0), boundary.completedPhaseIndexes)
        val event = boundary.events.first { it.type == "phaseCompleted" }
        assertEquals(4_000L, event.observedRunMs)
        assertEquals(5_000L, event.pauseMs)
    }

    @Test
    fun timerFrequencyDoesNotChangePauseAccountingOrEventRecords() {
        fun run(tickEveryMs: Long): EngineState {
            now = 0
            val clock = clock()
            clock.send(EngineCommand.Start)
            for (end in listOf(1_025L, 6_025L, 9_000L)) {
                while (now < end) {
                    now = minOf(now + tickEveryMs, end)
                    clock.tick()
                }
                when (end) {
                    1_025L -> clock.send(EngineCommand.Pause)
                    6_025L -> clock.send(EngineCommand.Resume)
                }
            }
            return clock.state
        }
        assertEquals(run(50_000), run(50))
    }

    @Test
    fun navigationAndEarlyEndSettleTimeBeforeChangingThePlanPosition() {
        val clock = clock()
        clock.send(EngineCommand.Start)
        now = 1_025
        val skipped = clock.send(EngineCommand.StepForward)
        assertEquals(4_000L, skipped.scheduledElapsedMs)
        assertEquals(1_025L, skipped.observedRunMs)
        assertEquals(emptySet(), skipped.completedPhaseIndexes)
        now = 1_050
        val ended = clock.send(EngineCommand.EndEarly)
        assertEquals(4_025L, ended.scheduledElapsedMs)
        assertEquals(1_050L, ended.observedRunMs)
        now = 20_000
        assertEquals(ended, clock.tick())
    }

    @Test
    fun explicitUnobservedGapDoesNotBecomeRunningOrPauseTime() {
        for (pauseBeforeGap in listOf(false, true)) {
            now = 0
            val clock = clock()
            clock.send(EngineCommand.Start)
            now = 1_000
            clock.tick()
            if (pauseBeforeGap) clock.send(EngineCommand.Pause)
            now = 9_000
            val recovered = clock.send(EngineCommand.NoteUnobserved(8_000))
            assertEquals(1_000L, recovered.observedRunMs)
            assertEquals(1_000L, recovered.scheduledElapsedMs)
            assertEquals(8_000L, recovered.unobservedGapMs)
            assertEquals(emptySet(), recovered.completedPhaseIndexes)
            now = 15_000
            assertEquals(recovered, clock.tick())
            clock.send(EngineCommand.Resume)
            now = 18_000
            assertEquals(4_000L, clock.tick().observedRunMs)
            assertEquals(0L, clock.state.pauseMs)
        }
    }

    @Test
    fun restartDropsThePreviousClockAnchor() {
        val clock = clock()
        clock.send(EngineCommand.Start)
        now = 60_000
        assertEquals(SessionStatus.Completed, clock.tick().status)
        now = 70_000
        assertEquals(initialState(), clock.reset())
        now = 80_000
        clock.send(EngineCommand.Start)
        now = 81_000
        assertEquals(1_000L, clock.tick().observedRunMs)
    }
}
