package dev.gymassistant.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class EventConformanceTest {
    @Test
    fun sharedEventRecordsAreIndependentOfAdvancePartitions() {
        val root = File("../../examples")
        val fixture = Json.decodeFromString<EventFixture>(
            root.resolve("expected/composite-pyramid.events.json").readText(),
        )
        val plan = compileTemplate(
            root.resolve(fixture.template).readText(),
            fixture.exercises.map { root.resolve(it).readText() },
        )
        for (case in fixture.cases) {
            for (partition in fixture.advancePartitionsMs) {
                var state = initialState()
                val context = "${case.name}, partition=$partition"
                for ((commandIndex, command) in case.commands.withIndex()) {
                    if (command.op == "advance") {
                        var remaining = command.ms
                        var index = 0
                        while (remaining > 0) {
                            val delta = minOf(remaining, partition[index++ % partition.size])
                            state = reduce(plan, state, EngineCommand.Advance(delta))
                            remaining -= delta
                        }
                    } else {
                        val input = when (command.op) {
                            "start" -> EngineCommand.Start
                            "pause" -> EngineCommand.Pause
                            "resume" -> EngineCommand.Resume
                            "noteUnobserved" -> EngineCommand.NoteUnobserved(command.ms)
                            "stepForward" -> EngineCommand.StepForward
                            "stepBackward" -> EngineCommand.StepBackward
                            else -> error("unknown fixture command ${command.op}")
                        }
                        state = reduce(plan, state, input)
                    }
                    for (checkpoint in case.checkpoints) {
                        if (checkpoint.afterCommand == commandIndex + 1) {
                            assertEquals(
                                checkpoint.expectedState, counterState(plan, state),
                                "$context, after command ${checkpoint.afterCommand}",
                            )
                        }
                    }
                }
                assertEquals(case.expectedEvents, state.events, context)
                assertEquals(case.expectedState, counterState(plan, state), context)
            }
        }
    }
}

private fun counterState(plan: CompiledPlan, state: EngineState): ExpectedEventState {
    val view = snapshot(plan, state)
    return ExpectedEventState(
        view.status, view.scheduledElapsedMs, view.observedRunMs,
        view.pauseMs, view.unobservedGapMs, view.completedPhaseIndexes,
    )
}

@Serializable
private data class EventFixture(
    val template: String,
    val exercises: List<String>,
    val advancePartitionsMs: List<List<Long>>,
    val cases: List<EventCase>,
)

@Serializable
private data class EventCase(
    val name: String,
    val commands: List<EventInput>,
    val expectedEvents: List<EngineEvent>,
    val expectedState: ExpectedEventState,
    val checkpoints: List<EventCheckpoint> = emptyList(),
)

@Serializable
private data class EventCheckpoint(val afterCommand: Int, val expectedState: ExpectedEventState)

@Serializable
private data class EventInput(val op: String, val ms: Long = 0)

@Serializable
private data class ExpectedEventState(
    val status: String,
    val scheduledElapsedMs: Long,
    val observedRunMs: Long,
    val pauseMs: Long,
    val unobservedGapMs: Long,
    val completedPhaseIndexes: List<Int>,
)
