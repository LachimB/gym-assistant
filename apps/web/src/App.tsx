import { useEffect, useMemo, useRef, useState } from "react";
import templateYaml from "../../../examples/templates/composite-pyramid.yaml?raw";
import bridgeYaml from "../../../examples/exercises/bridge-band-family.yaml?raw";
import plankYaml from "../../../examples/exercises/plank-family.yaml?raw";
import {
  compileTemplate,
  reduce,
  snapshot,
  initialState,
  type EngineCommand,
  type EngineState,
} from "@engine";

const plan = compileTemplate(templateYaml, [bridgeYaml, plankYaml]);

function formatMs(ms: number): string {
  const seconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${rest.toString().padStart(2, "0")}`;
}

export function App() {
  const [state, setState] = useState<EngineState>(initialState);
  const [wallClock, setWallClock] = useState(false);
  const lastTick = useRef<number | null>(null);
  const view = useMemo(() => snapshot(plan, state), [state]);

  useEffect(() => {
    if (!wallClock || state.status !== "running") {
      lastTick.current = null;
      return;
    }
    let frame = 0;
    const tick = (now: number) => {
      const previous = lastTick.current ?? now;
      lastTick.current = now;
      const delta = Math.round(now - previous);
      if (delta > 0) {
        setState((current) => reduce(plan, current, { op: "advance", ms: delta }));
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [wallClock, state.status]);

  function send(command: EngineCommand) {
    setState((current) => reduce(plan, current, command));
  }

  return (
    <main>
      <h1>Gym Assistant simulator</h1>
      <p className="meta">Synthetic composite workout. This page does not record a Workout Session.</p>
      <p className="phase">{view.phaseName ?? (view.status === "completed" ? "Complete" : "Ready")}</p>
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
        <button type="button" onClick={() => send({ op: "start" })}>Start</button>
        <button type="button" onClick={() => send({ op: "pause" })}>Pause</button>
        <button type="button" onClick={() => send({ op: "resume" })}>Resume</button>
        <button type="button" onClick={() => send({ op: "stepBackward" })}>Step back</button>
        <button type="button" onClick={() => send({ op: "stepForward" })}>Step forward</button>
        <button type="button" onClick={() => send({ op: "advance", ms: 1000 })}>Virtual +1s</button>
        <button type="button" onClick={() => setWallClock((value) => !value)}>
          {wallClock ? "Stop wall clock" : "Use wall clock"}
        </button>
      </div>
    </main>
  );
}
