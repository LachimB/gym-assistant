import { useEffect, useState } from "react";
import templateYaml from "../../../examples/templates/composite-pyramid.yaml?raw";
import bridgeYaml from "../../../examples/exercises/bridge-band-family.yaml?raw";
import plankYaml from "../../../examples/exercises/plank-family.yaml?raw";
import { compileTemplate, type EngineCommand } from "@engine";
import { SimulatorScreen } from "./SimulatorScreen";
import { SimulatorClock } from "./SimulatorClock";

const plan = compileTemplate(templateYaml, [bridgeYaml, plankYaml]);

export function App() {
  const [clock] = useState(() => new SimulatorClock(plan, () => performance.now()));
  const [state, setState] = useState(clock.state);
  const [wallClock, setWallClock] = useState(false);

  useEffect(() => {
    if (!wallClock || (state.status !== "running" && state.status !== "paused")) {
      return;
    }
    let frame = 0;
    const tick = () => {
      setState(clock.tick());
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [clock, wallClock, state.status]);

  function send(command: EngineCommand) {
    setState(clock.send(command));
  }

  return (
    <SimulatorScreen
      plan={plan}
      state={state}
      wallClock={wallClock}
      onCommand={send}
      onToggleWallClock={() => {
        const enabled = !clock.wallClock;
        setState(clock.setWallClock(enabled));
        setWallClock(enabled);
      }}
      onRestart={() => {
        setWallClock(false);
        setState(clock.reset());
      }}
    />
  );
}
