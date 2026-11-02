import { useMemo } from "react";
import { snapshot, type CompiledPlan, type EngineCommand, type EngineState } from "@engine";

function formatMs(ms: number): string {
  const seconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${rest.toString().padStart(2, "0")}`;
}

export function SimulatorScreen({
  plan,
  state,
  wallClock,
  onCommand,
  onToggleWallClock,
  onRestart,
}: {
  plan: CompiledPlan;
  state: EngineState;
  wallClock: boolean;
  onCommand: (command: EngineCommand) => void;
  onToggleWallClock: () => void;
  onRestart: () => void;
}) {
  const view = useMemo(() => snapshot(plan, state), [plan, state]);
  const terminal = state.status === "completed";
  const started =
    state.status === "running" || state.status === "paused" || state.status === "recoveredPaused";

  if (state.completionAcknowledged) {
    return (
      <main>
        <h1>Simulation finished</h1>
        <p className="meta">Synthetic composite workout. This page does not record a Workout Session.</p>
        <p>Scheduled duration {state.scheduledElapsedMs} ms</p>
        <p>Observed execution {state.observedRunMs} ms</p>
        <div className="controls">
          <button type="button" onClick={onRestart}>Restart simulation</button>
        </div>
      </main>
    );
  }

  return (
    <main>
      <h1>Gym Assistant simulator</h1>
      <p className="meta">Synthetic composite workout. This page does not record a Workout Session.</p>
      <p className="phase">{view.phaseName ?? (terminal ? "Complete" : "Ready")}</p>
      <p className="remaining">{formatMs(view.phaseRemainingMs)}</p>
      <p>
        {view.exerciseId ?? "—"} · {view.variationId ?? "—"} · set {view.setId ?? "—"} · rep{" "}
        {view.repetitionIndex ?? "—"}
      </p>
      <p>{view.resistanceLabel || "No resistance"}</p>
      <p className="clocks">
        Scheduled {view.scheduledElapsedMs} ms · observed {view.observedRunMs} ms · pause {view.pauseMs} ms ·
        unobserved {view.unobservedGapMs} ms
      </p>
      {view.simulatedCue ? <p className="cue">{view.simulatedCue}. This is not device audio.</p> : null}
      {state.status === "recoveredPaused" ? (
        <p className="warning">
          Timing was unobserved. The gap was not counted as exercise. Resume only if you want to continue from the
          last checkpoint.
        </p>
      ) : null}
      <div className="controls">
        {terminal ? (
          <button type="button" onClick={() => onCommand({ op: "acknowledge" })}>Finish Simulation</button>
        ) : (
          <>
            <button type="button" onClick={() => onCommand({ op: "start" })}>Start</button>
            <button type="button" onClick={() => onCommand({ op: "pause" })}>Pause</button>
            <button type="button" onClick={() => onCommand({ op: "resume" })}>Resume</button>
            <button type="button" onClick={() => onCommand({ op: "stepBackward" })}>Step back</button>
            <button type="button" onClick={() => onCommand({ op: "stepForward" })}>Step forward</button>
            <button type="button" onClick={() => onCommand({ op: "advance", ms: 1000 })}>Virtual +1s</button>
            <button type="button" onClick={onToggleWallClock}>
              {wallClock ? "Stop wall clock" : "Use wall clock"}
            </button>
            {started ? (
              <button type="button" onClick={() => onCommand({ op: "endEarly" })}>End Simulation</button>
            ) : null}
          </>
        )}
      </div>
    </main>
  );
}
