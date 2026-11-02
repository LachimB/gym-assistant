package dev.gymassistant.session

import dev.gymassistant.engine.SessionStatus
import java.time.Instant
import java.util.UUID

/** Single-owner platform adapter. All calls are serialized by its caller. */
class SessionDriver(
    private val store: SessionStore,
    private val nowMs: () -> Long,
    private val utc: () -> String = { Instant.now().toString() },
    private val operationId: () -> String = { UUID.randomUUID().toString() },
) {
    var saved: SavedWorkout? = null
        private set
    var needsRecovery = false
        private set
    private var anchor = nowMs()

    suspend fun restore(): SavedWorkout? {
        val prior = store.unfinished()
        if (prior == null) {
            saved = null
            needsRecovery = false
            anchor = nowMs()
            return null
        }
        // Restart/reboot gaps have unknown duration; never compare old monotonic anchors.
        val next = store.commit(prior.start.sessionId, prior.revision, operationId(),
            SessionAction("recover", recordedAt = utc()))
        saved = next
        needsRecovery = true
        anchor = nowMs()
        return next
    }

    suspend fun start(snapshot: SessionStart): SavedWorkout {
        val next = store.start(snapshot, operationId())
        saved = next
        needsRecovery = false
        anchor = nowMs()
        return next
    }

    suspend fun send(kind: String): SavedWorkout {
        val prior = checkNotNull(saved)
        check(!needsRecovery || kind in setOf("resume", "discard", "finish", "end")) { "Explicit recovery decision required" }
        val now = nowMs()
        val delta = if (now >= anchor) now - anchor else 0L
        val timed = prior.state.status in setOf(SessionStatus.Running, SessionStatus.Paused)
        val discontinuity = timed && (now < anchor || delta > MAX_OBSERVED_INTERVAL_MS)
        val recovering = needsRecovery || discontinuity
        val action = if (discontinuity && !needsRecovery && kind != "discard") {
            // Do not silently apply a queued resume/navigation to an unobserved interval.
            SessionAction("recover", recordedAt = utc(), gapMs = if (now >= anchor) delta else null)
        } else {
            SessionAction(kind, elapsedMs = if (recovering || !timed || kind == "discard") 0 else delta, recordedAt = utc())
        }
        val next = store.commit(prior.start.sessionId, prior.revision, operationId(), action)
        saved = next
        needsRecovery = action.kind == "recover" || (needsRecovery && action.kind !in setOf("resume", "discard", "finish", "end"))
        anchor = now
        return next
    }

    fun shouldTick(): Boolean = !needsRecovery && saved?.checkpoint?.outcome == "inProgress" &&
        saved?.state?.status in setOf(SessionStatus.Running, SessionStatus.Paused)

    companion object {
        // Conservative sampling policy, not a real-time guarantee or background-service substitute.
        const val MAX_OBSERVED_INTERVAL_MS = 2_000L
    }
}
