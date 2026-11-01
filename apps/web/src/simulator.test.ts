import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { compileTemplate, reduce, reduceAll, snapshot, timelineProjection, type EngineState } from "@engine";
import { SimulatorScreen } from "./SimulatorScreen";

const root = resolve(import.meta.dirname, "../../../examples");

describe("web simulator engine", () => {
  it("uses the same 50000 ms trace as the conformance fixture", () => {
    const plan = compileTemplate(readFileSync(resolve(root, "templates/composite-pyramid.yaml"), "utf8"), [
      readFileSync(resolve(root, "exercises/bridge-band-family.yaml"), "utf8"),
      readFileSync(resolve(root, "exercises/plank-family.yaml"), "utf8"),
    ]);
    const expected = JSON.parse(readFileSync(resolve(root, "expected/composite-pyramid.timeline.json"), "utf8"));
    expect(timelineProjection(plan)).toEqual(expected.phases);
    const skipped = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 1000 },
      { op: "stepForward" },
    ]);
    expect(snapshot(plan, skipped).completedPhaseIndexes).toEqual([]);
  });

  it("shows Finish Simulation at the terminal state and a final screen after acknowledgment", () => {
    const plan = compileTemplate(readFileSync(resolve(root, "templates/composite-pyramid.yaml"), "utf8"), [
      readFileSync(resolve(root, "exercises/bridge-band-family.yaml"), "utf8"),
      readFileSync(resolve(root, "exercises/plank-family.yaml"), "utf8"),
    ]);
    const finished = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 50000 },
    ]);
    const awaiting = renderScreen(finished);
    expect(awaiting).toContain("Finish Simulation");
    expect(awaiting).not.toContain(">Start<");
    expect(awaiting).not.toContain(">Pause<");
    expect(awaiting).not.toContain(">Resume<");
    expect(awaiting).toContain("does not record a Workout Session");

    const acknowledged = reduce(plan, finished, { op: "acknowledge" });
    const finalScreen = renderScreen(acknowledged);
    expect(finalScreen).toContain("Simulation finished");
    expect(finalScreen).toContain("Scheduled duration 50000 ms");
    expect(finalScreen).toContain("Observed execution 50000 ms");
    expect(finalScreen).toContain("Restart simulation");
    expect(finalScreen).toContain("does not record a Workout Session");
    expect(finalScreen).not.toContain("Finish Simulation");
    expect(finalScreen).not.toContain(">Start<");
    expect(finalScreen).not.toContain(">Pause<");
    expect(finalScreen).not.toContain(">Resume<");
    expect(acknowledged.scheduledElapsedMs).toBe(finished.scheduledElapsedMs);
    expect(acknowledged.observedRunMs).toBe(finished.observedRunMs);
    expect(acknowledged.events).toEqual(finished.events);
  });
});

function renderScreen(state: EngineState) {
  const plan = compileTemplate(readFileSync(resolve(root, "templates/composite-pyramid.yaml"), "utf8"), [
    readFileSync(resolve(root, "exercises/bridge-band-family.yaml"), "utf8"),
    readFileSync(resolve(root, "exercises/plank-family.yaml"), "utf8"),
  ]);
  return renderToStaticMarkup(
    createElement(SimulatorScreen, {
      plan,
      state,
      wallClock: false,
      onCommand: () => {},
      onToggleWallClock: () => {},
      onRestart: () => {},
    }),
  );
}
