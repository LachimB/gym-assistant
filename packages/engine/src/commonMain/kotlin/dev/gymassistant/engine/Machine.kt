package dev.gymassistant.engine

fun initialState(): EngineState = EngineState()

fun reduce(plan: CompiledPlan, state: EngineState, command: EngineCommand): EngineState {
    require(command !is EngineCommand.Advance || command.ms >= 0) { "negative advance" }
    require(command !is EngineCommand.NoteUnobserved || command.ms >= 0) { "negative gap" }
    return when (command) {
        EngineCommand.Start -> start(plan, state)
        EngineCommand.Pause -> pause(state)
        EngineCommand.Resume -> resume(state)
        is EngineCommand.Advance -> advance(plan, state, command.ms)
        EngineCommand.StepForward -> step(plan, state, forward = true)
        EngineCommand.StepBackward -> step(plan, state, forward = false)
        is EngineCommand.NoteUnobserved -> noteUnobserved(state, command.ms)
        EngineCommand.Acknowledge -> acknowledge(state)
        EngineCommand.EndEarly -> endEarly(state)
    }
}

fun reduceAll(plan: CompiledPlan, commands: List<EngineCommand>): EngineState =
    commands.fold(initialState()) { state, command -> reduce(plan, state, command) }

fun snapshot(plan: CompiledPlan, state: EngineState): PhaseSnapshot {
    val phase = activePhase(plan, state.scheduledElapsedMs)
    val remaining = if (phase == null || state.status == SessionStatus.Completed) {
        0L
    } else {
        phase.endMs - state.scheduledElapsedMs
    }
    val cue = if (state.status == SessionStatus.Running && phase != null) {
        "Simulated cue: ${phase.name}"
    } else {
        null
    }
    return PhaseSnapshot(
        status = state.status.name.replaceFirstChar { it.lowercase() },
        scheduledElapsedMs = state.scheduledElapsedMs,
        observedRunMs = state.observedRunMs,
        pauseMs = state.pauseMs,
        unobservedGapMs = state.unobservedGapMs,
        activePhaseIndex = phase?.index,
        phaseName = phase?.name,
        stepId = phase?.stepId,
        setId = phase?.setId,
        repetitionIndex = phase?.repetitionIndex,
        exerciseId = phase?.exerciseId,
        variationId = phase?.variationId,
        resistanceLabel = phase?.resistanceLabel.orEmpty(),
        phaseRemainingMs = remaining,
        completedPhaseIndexes = state.completedPhaseIndexes.sorted(),
        simulatedCue = cue,
    )
}

private fun acknowledge(state: EngineState): EngineState {
    if (state.status != SessionStatus.Completed || state.completionAcknowledged) return state
    return state.copy(completionAcknowledged = true)
}

private fun endEarly(state: EngineState): EngineState {
    if (
        state.status != SessionStatus.Running &&
        state.status != SessionStatus.Paused &&
        state.status != SessionStatus.RecoveredPaused
    ) {
        return state
    }
    return state.copy(
        status = SessionStatus.Completed,
        completionAcknowledged = true,
    )
}

private fun start(plan: CompiledPlan, state: EngineState): EngineState {
    if (state.status != SessionStatus.Loaded) return state
    return state.copy(
        status = SessionStatus.Running,
        events = state.events + event(state, "sessionStarted", plan.phases.firstOrNull()?.index) +
            event(state, "phaseStarted", plan.phases.firstOrNull()?.index),
    )
}

private fun pause(state: EngineState): EngineState {
    if (state.status != SessionStatus.Running) return state
    return state.copy(status = SessionStatus.Paused, events = state.events + event(state, "pause", null))
}

private fun resume(state: EngineState): EngineState = when (state.status) {
    SessionStatus.Paused -> state.copy(
        status = SessionStatus.Running,
        events = state.events + event(state, "resume", null),
    )
    SessionStatus.RecoveredPaused -> state.copy(
        status = SessionStatus.Running,
        events = state.events + event(state, "resumeAfterGap", null),
    )
    else -> state
}

private fun noteUnobserved(state: EngineState, ms: Long): EngineState {
    if (state.status != SessionStatus.Running && state.status != SessionStatus.Paused) return state
    val next = state.copy(
        status = SessionStatus.RecoveredPaused,
        unobservedGapMs = state.unobservedGapMs + ms,
    )
    return next.copy(events = state.events + event(next, "unobservedGap", null))
}

private fun advance(plan: CompiledPlan, state: EngineState, ms: Long): EngineState {
    if (ms == 0L) return state
    return when (state.status) {
        SessionStatus.Running -> advanceRunning(plan, state, ms)
        SessionStatus.Paused -> state.copy(pauseMs = state.pauseMs + ms)
        else -> state
    }
}

private fun advanceRunning(plan: CompiledPlan, state: EngineState, ms: Long): EngineState {
    val total = plan.expectedDurationMs
    var scheduled = state.scheduledElapsedMs
    val target = (scheduled + ms).coerceAtMost(total)
    val completed = state.completedPhaseIndexes.toMutableSet()
    val events = state.events.toMutableList()
    while (scheduled < target) {
        val phase = activePhase(plan, scheduled) ?: break
        if (target >= phase.endMs) {
            completed += phase.index
            scheduled = phase.endMs
            // Both events describe the boundary, not the start of this advance.
            val boundaryState = state.copy(
                scheduledElapsedMs = scheduled,
                observedRunMs = state.observedRunMs + (scheduled - state.scheduledElapsedMs),
            )
            events += event(
                boundaryState,
                "phaseCompleted",
                phase.index,
            )
            val next = activePhase(plan, scheduled)
            if (next != null) {
                events += event(
                    boundaryState,
                    "phaseStarted",
                    next.index,
                )
            }
        } else {
            scheduled = target
        }
    }
    val consumed = scheduled - state.scheduledElapsedMs
    val status = if (scheduled >= total) SessionStatus.Completed else SessionStatus.Running
    return state.copy(
        status = status,
        scheduledElapsedMs = scheduled,
        observedRunMs = state.observedRunMs + consumed,
        completedPhaseIndexes = completed,
        events = events,
    )
}

private fun step(plan: CompiledPlan, state: EngineState, forward: Boolean): EngineState {
    if (state.status != SessionStatus.Running && state.status != SessionStatus.Paused) return state
    val current = activePhase(plan, state.scheduledElapsedMs) ?: return state
    val destinationOrNull: Long? = if (forward) {
        plan.phases.getOrNull(current.index + 1)?.startMs
    } else if (current.index > 0) {
        plan.phases[current.index - 1].startMs
    } else {
        null
    }
    val destination = destinationOrNull ?: return state
    val landed = activePhase(plan, destination)
    val completed = if (forward) {
        state.completedPhaseIndexes
    } else {
        state.completedPhaseIndexes.filter { it < (landed?.index ?: 0) }.toSet()
    }
    val kind = if (forward) "skip" else "rewind"
    val moved = state.copy(
        scheduledElapsedMs = destination,
        completedPhaseIndexes = completed,
    )
    return moved.copy(events = state.events + event(moved, kind, landed?.index) + event(moved, "phaseStarted", landed?.index))
}

private fun activePhase(plan: CompiledPlan, scheduledElapsedMs: Long): CompiledPhase? =
    plan.phases.firstOrNull { scheduledElapsedMs >= it.startMs && scheduledElapsedMs < it.endMs }

private fun event(state: EngineState, type: String, phaseIndex: Int?): EngineEvent =
    EngineEvent(
        type = type,
        scheduledElapsedMs = state.scheduledElapsedMs,
        observedRunMs = state.observedRunMs,
        pauseMs = state.pauseMs,
        unobservedGapMs = state.unobservedGapMs,
        phaseIndex = phaseIndex,
    )
