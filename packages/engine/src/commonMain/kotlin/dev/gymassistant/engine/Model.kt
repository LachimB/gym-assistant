package dev.gymassistant.engine

import kotlinx.serialization.Serializable

@Serializable
data class CompiledPhase(
    val index: Int,
    val stepId: String,
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val phaseId: String? = null,
    val setId: String? = null,
    val repetitionIndex: Int? = null,
    val exerciseId: String? = null,
    val variationId: String? = null,
    val resistanceLabel: String = "",
)

@Serializable
data class CompiledPlan(
    val schemaVersion: String,
    val templateId: String,
    val revisionId: String,
    val planFormatVersion: String = "1.0.0",
    val expectedDurationMs: Long,
    val phases: List<CompiledPhase>,
)

enum class SessionStatus {
    Loaded,
    Running,
    Paused,
    RecoveredPaused,
    Completed,
}

@Serializable
data class EngineEvent(
    val type: String,
    val scheduledElapsedMs: Long,
    val observedRunMs: Long,
    val pauseMs: Long,
    val unobservedGapMs: Long,
    val phaseIndex: Int? = null,
)

data class EngineState(
    val status: SessionStatus = SessionStatus.Loaded,
    val scheduledElapsedMs: Long = 0,
    val observedRunMs: Long = 0,
    val pauseMs: Long = 0,
    val unobservedGapMs: Long = 0,
    val completedPhaseIndexes: Set<Int> = emptySet(),
    val completionAcknowledged: Boolean = false,
    val events: List<EngineEvent> = emptyList(),
)

sealed class EngineCommand {
    data object Start : EngineCommand()
    data object Pause : EngineCommand()
    data object Resume : EngineCommand()
    data class Advance(val ms: Long) : EngineCommand()
    data object StepForward : EngineCommand()
    data object StepBackward : EngineCommand()
    data class NoteUnobserved(val ms: Long) : EngineCommand()
    data object Acknowledge : EngineCommand()
}

@Serializable
data class PhaseSnapshot(
    val status: String,
    val scheduledElapsedMs: Long,
    val observedRunMs: Long,
    val pauseMs: Long,
    val unobservedGapMs: Long,
    val activePhaseIndex: Int?,
    val phaseName: String?,
    val stepId: String?,
    val setId: String?,
    val repetitionIndex: Int?,
    val exerciseId: String?,
    val variationId: String?,
    val resistanceLabel: String,
    val phaseRemainingMs: Long,
    val completedPhaseIndexes: List<Int>,
    val simulatedCue: String?,
)
