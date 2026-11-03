package dev.gymassistant.session

import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.EngineEvent
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.initialState
import dev.gymassistant.engine.reduce
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Portable human history. No timer commands, Room rows, operation IDs or receipts. */
@Serializable
data class SessionHistory(
    val sessionId: String,
    val templateId: String,
    val revisionId: String,
    val executionPlanHash: String,
    val engineVersion: String,
    val startedAt: String,
    val timeZone: String,
    val localTrainingDate: String,
    val status: String,
    val completionKind: String,
    val sourcePlanSnapshot: HistorySource,
    val timing: HistoryTiming,
    val events: List<HistoryEvent>,
    val finishedAt: String? = null,
    val schemaVersion: String = "1.1.0",
)

@Serializable
data class HistorySource(
    val templateYaml: String,
    val exerciseYamls: List<String>,
    // Exact frozen UTF-8 hash input, rather than a cross-language reserialization promise.
    val compiledPlanJson: String,
    val planFormatVersion: String,
    val hashFormat: String = "sha256-kotlin-timed-1-json-utf8",
)

@Serializable
data class HistoryTiming(
    val plannedDurationMs: Long,
    val scheduledElapsedMs: Long,
    val observedRunMs: Long,
    val pauseMs: Long,
    val unobservedGapMs: Long,
    val unknownGapCount: Int,
)

@Serializable
data class HistoryEvent(
    val eventId: String,
    val type: String,
    // Observation/commit instant, not a claimed exact wall-clock phase boundary.
    val recordedAt: String,
    val scheduledElapsedMs: Long,
    val observedRunMs: Long,
    val pauseMs: Long,
    val unobservedGapMs: Long,
    val unknownGapCount: Int,
    val planStepId: String? = null,
    val phaseId: String? = null,
    val setId: String? = null,
    val detail: HistoryEventDetail? = null,
)

@Serializable
data class HistoryEventDetail(
    val gapDurationKnown: Boolean? = null,
    val gapDurationMs: Long? = null,
    val resumedAfterInterruption: Boolean? = null,
    val completionKind: String? = null,
)

// Nulls are absent in the portable contract; internal on-device serialization is unchanged.
internal val historyJson = kotlinx.serialization.json.Json { encodeDefaults = true; explicitNulls = false }

fun SessionHistory.toCanonicalJson(): String = historyJson.encodeToString(this)

/** Caller must supply one atomically read committed prefix, never independently fetched revisions. */
internal fun projectSession(saved: SavedWorkout, rows: List<ActionRow>): SessionHistory {
    val start = saved.start
    start.validate()
    val identifier = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{2,127}")
    check(listOf(start.plan.templateId, start.plan.revisionId).all { it.matches(identifier) }) { "Invalid source identity" }
    check(start.exerciseYamls.isNotEmpty()) { "Missing frozen exercises" }
    var boundary = 0L
    start.plan.phases.forEachIndexed { index, phase ->
        check(phase.index == index && phase.startMs == boundary && phase.endMs > boundary) { "Invalid frozen timeline" }
        check(listOfNotNull(phase.stepId, phase.phaseId, phase.setId).all { it.matches(identifier) }) { "Invalid source reference" }
        boundary = phase.endMs
    }
    check(boundary == start.plan.expectedDurationMs) { "Invalid frozen duration" }
    check(rows.isNotEmpty() && rows.size.toLong() == saved.revision) { "Incomplete session history" }
    val started = reduce(start.plan, initialState(), EngineCommand.Start)
    var previous = SavedWorkout(start, 1, Checkpoint.from(started))
    var completionKind = "unfinished"
    val operations = mutableSetOf<String>()
    val events = mutableListOf<HistoryEvent>()

    fun add(type: String, at: String, checkpoint: Checkpoint, engine: EngineEvent? = null,
            detail: HistoryEventDetail? = null) {
        val phase = engine?.phaseIndex?.let { index ->
            check(index in start.plan.phases.indices) { "Invalid recorded phase" }
            start.plan.phases[index]
        }
        val phaseSpecific = type in setOf("phaseStarted", "phaseCompleted", "skip", "rewind")
        check(!phaseSpecific || phase != null) { "Missing recorded phase" }
        // Semantic ordinal is independent of the number of intervening timer saves.
        val identity = "${start.sessionId}:history-1:${events.size}"
        val id = "event_" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        events += HistoryEvent(id, type, at,
            engine?.scheduledElapsedMs ?: checkpoint.scheduledElapsedMs,
            engine?.observedRunMs ?: checkpoint.observedRunMs,
            engine?.pauseMs ?: checkpoint.pauseMs,
            engine?.unobservedGapMs ?: checkpoint.unobservedGapMs,
            checkpoint.unknownGapCount,
            if (phaseSpecific) phase?.stepId else null,
            if (phaseSpecific) phase?.phaseId else null,
            if (phaseSpecific) phase?.setId else null, detail)
    }

    rows.forEachIndexed { index, row ->
        val sequence = index.toLong() + 1
        check(row.sessionId == start.sessionId && row.sequence == sequence && row.expectedRevision == sequence - 1) {
            "Noncontiguous session history"
        }
        check(row.operationId.isNotBlank() && operations.add(row.operationId)) { "Invalid repeated operation" }
        val receipt = sessionJson.decodeFromString<Checkpoint>(row.checkpointJson)
        check(listOf(receipt.scheduledElapsedMs, receipt.observedRunMs, receipt.pauseMs, receipt.unobservedGapMs).all { it >= 0 } &&
            receipt.scheduledElapsedMs <= start.plan.expectedDurationMs && receipt.unknownGapCount >= 0) { "Invalid recorded timing" }
        val payload = sessionJson.decodeFromString<ActionPayload>(row.payloadJson)
        val action: SessionAction?
        val expected: Transition
        if (index == 0) {
            check(sessionJson.decodeFromString<SessionStart>(row.intentJson) == start) { "Start intent differs" }
            action = null
            expected = Transition(previous.checkpoint, ActionPayload(null, started.events))
        } else {
            action = sessionJson.decodeFromString<SessionAction>(row.intentJson)
            expected = transition(previous, action)
        }
        check(payload == expected.payload && receipt == expected.checkpoint) { "Recorded transition differs" }
        val at = action?.recordedAt ?: start.startedAt
        payload.engineEvents.forEach { engine ->
            val type = when (engine.type) {
                "sessionStarted", "phaseStarted", "phaseCompleted", "pause", "resume", "skip", "rewind" -> engine.type
                "resumeAfterGap" -> "resume"
                "unobservedGap" -> "interruption"
                else -> error("Unsupported recorded event")
            }
            val detail = when (engine.type) {
                "resumeAfterGap" -> HistoryEventDetail(resumedAfterInterruption = true)
                "unobservedGap" -> HistoryEventDetail(gapDurationKnown = action?.gapMs != null, gapDurationMs = action?.gapMs)
                else -> null
            }
            add(type, at, receipt, engine, detail)
        }
        // A further restart while already recovered-paused can add unknown-gap evidence
        // without the current reducer emitting another engine event.
        if (index > 0 && receipt.unknownGapCount > previous.checkpoint.unknownGapCount &&
            payload.engineEvents.none { it.type == "unobservedGap" }) {
            add("interruption", at, receipt, detail = HistoryEventDetail(gapDurationKnown = false))
        }
        if (index > 0 && receipt.status == SessionStatus.Completed.name &&
            previous.checkpoint.status != SessionStatus.Completed.name && receipt.outcome == "inProgress") {
            add("planEndReached", at, receipt)
        }
        if (receipt.outcome != "inProgress") {
            completionKind = when (action?.kind) {
                "finish" -> "plannedEnd"
                "end" -> "partial"
                "discard" -> "discarded"
                else -> error("Unsupported finalization")
            }
            add(if (completionKind == "discarded") "sessionDiscarded" else "sessionEnded", at, receipt,
                detail = HistoryEventDetail(completionKind = completionKind))
        }
        previous = SavedWorkout(start, sequence, receipt)
    }
    check(previous == saved) { "Checkpoint revision differs from recorded prefix" }
    val checkpoint = saved.checkpoint
    val status = when {
        checkpoint.outcome == "discarded" -> "discarded"
        checkpoint.outcome == "completed" -> "completed"
        saved.state.status == SessionStatus.Completed -> "awaitingCompletion"
        saved.state.status in setOf(SessionStatus.Paused, SessionStatus.RecoveredPaused) -> "paused"
        saved.state.status == SessionStatus.Running -> "inProgress"
        else -> error("Unsupported session state")
    }
    return SessionHistory(start.sessionId, start.plan.templateId, start.plan.revisionId,
        start.planHash, start.engineVersion, start.startedAt, start.timeZone, start.localTrainingDate,
        status, completionKind,
        HistorySource(start.templateYaml, start.exerciseYamls, sessionJson.encodeToString(start.plan), start.plan.planFormatVersion),
        HistoryTiming(start.plan.expectedDurationMs, checkpoint.scheduledElapsedMs, checkpoint.observedRunMs,
            checkpoint.pauseMs, checkpoint.unobservedGapMs, checkpoint.unknownGapCount), events, checkpoint.finishedAt)
}
