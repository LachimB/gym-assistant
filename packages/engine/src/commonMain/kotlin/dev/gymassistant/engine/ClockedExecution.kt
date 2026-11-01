package dev.gymassistant.engine

/** Process-local adapter; the injected monotonic clock is not a recovery source. */
class ClockedExecution(
    private val plan: CompiledPlan,
    private val nowMs: () -> Long,
) {
    var state: EngineState = initialState()
        private set
    private var lastSampleMs = nowMs()

    fun tick(): EngineState {
        val now = nowMs()
        require(now >= lastSampleMs) { "clock must be monotonic" }
        val elapsed = now - lastSampleMs
        lastSampleMs = now
        state = reduce(plan, state, EngineCommand.Advance(elapsed))
        return state
    }

    fun send(command: EngineCommand): EngineState {
        // Settle the old status before pause/resume/navigation changes it.
        if (command is EngineCommand.NoteUnobserved) {
            // The interval since the last sample is uncertain, not observed work.
            lastSampleMs = nowMs()
        } else {
            tick()
        }
        state = reduce(plan, state, command)
        return state
    }

    fun reset(): EngineState {
        lastSampleMs = nowMs()
        state = initialState()
        return state
    }
}
