package dev.gymassistant.session

import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.compileTemplate
import dev.gymassistant.engine.initialState
import dev.gymassistant.engine.reduce
import kotlinx.serialization.encodeToString

/** Entirely generated from pinned public fixtures; never constructed from a phone workout. */
internal class SyntheticDeliverySample private constructor(
    val saved: SavedWorkout, val rows: List<ActionRow>, val canonicalJson: String,
) {
    fun permits(job: SessionDeliveryRow) = job.sessionId == saved.start.sessionId &&
        job.terminalRevision == saved.revision && job.canonicalJson == canonicalJson &&
        job.contentSha256 == sha256Utf8(canonicalJson)
    companion object {
        fun create(template: String, exercises: List<String>): SyntheticDeliverySample {
            check(sha256Utf8((listOf(template) + exercises).joinToString("\u0000")) ==
                "9259bc092b92f59aaf27f3298b5cbb23621159afe6ce68e3376f7aff89824b3d") { "Synthetic fixture changed" }
            val at = "2026-11-03T10:00:00Z"
            val plan = compileTemplate(template, exercises)
            val source = SessionStart("synthetic_delivery_sample_v1", template, exercises.toList(), plan,
                hashPlan(plan), at, "Europe/Berlin", "2026-11-03")
            val state = reduce(plan, initialState(), EngineCommand.Start)
            val initial = Checkpoint.from(state)
            var saved = SavedWorkout(source, 1, initial)
            val rows = mutableListOf(ActionRow(source.sessionId, 1, "synthetic-start", 0,
                sessionJson.encodeToString(source), sessionJson.encodeToString(ActionPayload(null, state.events)), sessionJson.encodeToString(initial)))
            for (action in listOf(SessionAction("tick",50000,at), SessionAction("finish",0,at))) {
                val next = transition(saved,action)
                rows += ActionRow(source.sessionId,saved.revision+1,"synthetic-${action.kind}",saved.revision,
                    sessionJson.encodeToString(action),sessionJson.encodeToString(next.payload),sessionJson.encodeToString(next.checkpoint))
                saved = SavedWorkout(source,saved.revision+1,next.checkpoint)
            }
            return SyntheticDeliverySample(saved, rows.toList(), projectSession(saved,rows).toCanonicalJson())
        }
    }
}
