import { useEffect, useRef, useState } from "react";
import templateYaml from "../../../examples/templates/composite-pyramid.yaml?raw";
import bridgeYaml from "../../../examples/exercises/bridge-band-family.yaml?raw";
import plankYaml from "../../../examples/exercises/plank-family.yaml?raw";
import { compileTemplate, initialState, reduce, type EngineCommand, type EngineState } from "@engine";
import { SimulatorScreen } from "./SimulatorScreen";

const plan = compileTemplate(templateYaml, [bridgeYaml, plankYaml]);

export function App() {
  const [state, setState] = useState<EngineState>(initialState);
  const [wallClock, setWallClock] = useState(false);
  const lastTick = useRef<number | null>(null);

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
    <SimulatorScreen
      plan={plan}
      state={state}
      wallClock={wallClock}
      onCommand={send}
      onToggleWallClock={() => setWallClock((value) => !value)}
      onRestart={() => {
        setWallClock(false);
        setState(initialState());
      }}
    />
  );
}
