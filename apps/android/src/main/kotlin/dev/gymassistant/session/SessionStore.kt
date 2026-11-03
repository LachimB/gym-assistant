package dev.gymassistant.session

import dev.gymassistant.engine.CompiledPlan
import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.EngineEvent
import dev.gymassistant.engine.EngineState
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.compileTemplate
import dev.gymassistant.engine.initialState
import dev.gymassistant.engine.reduce
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Internal, versioned on-device format. Not the canonical synchronized session schema.
internal val sessionJson = Json { encodeDefaults = true }

@Serializable
data class SessionStart(
    val sessionId: String,
    val templateYaml: String,
    val exerciseYamls: List<String>,
    val plan: CompiledPlan,
    val planHash: String,
    val startedAt: String,
    val timeZone: String,
    val localTrainingDate: String,
    val formatVersion: Int = 1,
    val engineVersion: String = "kotlin-timed-1",
) {
    fun validate() {
        require(formatVersion == 1 && engineVersion == "kotlin-timed-1") { "Unsupported local format/engine" }
        require(sessionId.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{2,127}"))) { "Invalid session ID" }
        require(plan.schemaVersion == "1.0.0" && plan.planFormatVersion == "1.0.0") { "Unsupported plan" }
        require(plan.phases.isNotEmpty() && plan.expectedDurationMs > 0) { "Empty plan" }
        require(compileTemplate(templateYaml, exerciseYamls) == plan) { "Source and frozen plan differ" }
        require(hashPlan(plan) == planHash) { "Frozen plan hash mismatch" }
        require(Instant.parse(startedAt).atZone(ZoneId.of(timeZone)).toLocalDate().toString() == localTrainingDate) {
            "Local date does not match start instant and time zone"
        }
    }
}

fun hashPlan(plan: CompiledPlan): String = "sha256:" + MessageDigest.getInstance("SHA-256")
    .digest(sessionJson.encodeToString(plan).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

@Serializable
data class Checkpoint(
    val status: String,
    val scheduledElapsedMs: Long,
    val observedRunMs: Long,
    val pauseMs: Long,
    val unobservedGapMs: Long,
    val completedPhaseIndexes: Set<Int>,
    val completionAcknowledged: Boolean,
    val unknownGapCount: Int = 0,
    val outcome: String = "inProgress",
    val finishedAt: String? = null,
) {
    fun engineState() = EngineState(
        status = SessionStatus.valueOf(status), scheduledElapsedMs = scheduledElapsedMs,
        observedRunMs = observedRunMs, pauseMs = pauseMs, unobservedGapMs = unobservedGapMs,
        completedPhaseIndexes = completedPhaseIndexes, completionAcknowledged = completionAcknowledged,
    )

    companion object {
        fun from(state: EngineState, gaps: Int = 0, outcome: String = "inProgress", finishedAt: String? = null) =
            Checkpoint(state.status.name, state.scheduledElapsedMs, state.observedRunMs, state.pauseMs,
                state.unobservedGapMs, state.completedPhaseIndexes, state.completionAcknowledged,
                gaps, outcome, finishedAt)
    }
}

@Serializable
data class SessionAction(val kind: String, val elapsedMs: Long = 0, val recordedAt: String, val gapMs: Long? = null) {
    init {
        require(elapsedMs >= 0) { "Negative elapsed time" }
        require(kind in setOf("tick", "pause", "resume", "back", "forward", "finish", "end", "recover", "discard")) {
            "Unsupported session action"
        }
        Instant.parse(recordedAt)
        require(gapMs == null || (kind == "recover" && gapMs >= 0)) { "Invalid observed gap" }
        require(kind !in setOf("recover", "discard") || elapsedMs == 0L) { "Unobserved time cannot advance exercise" }
    }
}

data class SavedWorkout(val start: SessionStart, val revision: Long, val checkpoint: Checkpoint) {
    // Historical engine events are stored separately, not copied into each checkpoint.
    val state: EngineState get() = checkpoint.engineState()
}

@Serializable
data class ActionPayload(val action: SessionAction?, val engineEvents: List<EngineEvent>)

data class Transition(val checkpoint: Checkpoint, val payload: ActionPayload)

fun transition(workout: SavedWorkout, action: SessionAction): Transition {
    check(workout.checkpoint.outcome == "inProgress") { "Session is already finalized" }
    val plan = workout.start.plan
    var state = workout.state
    if (action.kind !in setOf("recover", "discard")) {
        state = reduce(plan, state, EngineCommand.Advance(action.elapsedMs))
    }
    state = when (action.kind) {
        "tick", "discard" -> state
        "pause" -> reduce(plan, state, EngineCommand.Pause)
        "resume" -> reduce(plan, state, EngineCommand.Resume)
        "back" -> reduce(plan, state, EngineCommand.StepBackward)
        "forward" -> reduce(plan, state, EngineCommand.StepForward)
        "finish" -> {
            check(state.status == SessionStatus.Completed) { "Cannot acknowledge an unfinished workout" }
            reduce(plan, state, EngineCommand.Acknowledge)
        }
        "end" -> reduce(plan, state, EngineCommand.EndEarly)
        "recover" -> reduce(plan, state, EngineCommand.NoteUnobserved(action.gapMs ?: 0))
        else -> error("Unsupported action")
    }
    val outcome = when (action.kind) {
        "discard" -> "discarded"
        "finish", "end" -> if (state.completionAcknowledged) "completed" else "inProgress"
        else -> "inProgress"
    }
    return Transition(
        Checkpoint.from(state,
            gaps = workout.checkpoint.unknownGapCount + if (action.kind == "recover" && action.gapMs == null && state.status != SessionStatus.Completed) 1 else 0,
            outcome = outcome, finishedAt = if (outcome == "inProgress") null else action.recordedAt),
        ActionPayload(action, state.events),
    )
}

interface SessionStore {
    suspend fun history(sessionId: String): SessionHistory?
    suspend fun unfinished(): SavedWorkout?
    suspend fun load(sessionId: String): SavedWorkout?
    suspend fun start(snapshot: SessionStart, operationId: String): SavedWorkout
    suspend fun commit(sessionId: String, expectedRevision: Long, operationId: String, action: SessionAction): SavedWorkout
}

class SessionConflict(message: String) : IllegalStateException(message)
