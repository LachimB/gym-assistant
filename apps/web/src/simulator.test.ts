import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  compileTemplate,
  initialState,
  reduce,
  reduceAll,
  snapshot,
  timelineProjection,
  type EngineState,
} from "@engine";
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

  it("shows End Simulation after start and the same final screen after an early end", () => {
    const plan = compileTemplate(readFileSync(resolve(root, "templates/composite-pyramid.yaml"), "utf8"), [
      readFileSync(resolve(root, "exercises/bridge-band-family.yaml"), "utf8"),
      readFileSync(resolve(root, "exercises/plank-family.yaml"), "utf8"),
    ]);
    expect(renderScreen(initialState())).not.toContain("End Simulation");

    const running = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 4174 },
    ]);
    const inProgress = renderScreen(running);
    expect(inProgress).toContain("End Simulation");
    expect(inProgress).toContain(">Start<");
    expect(inProgress).toContain(">Pause<");
    expect(inProgress).toContain(">Resume<");
    expect(inProgress).not.toContain("Finish Simulation");

    const ended = reduce(plan, running, { op: "endEarly" });
    expect(ended.status).toBe("completed");
    expect(ended.completionAcknowledged).toBe(true);
    expect(ended.scheduledElapsedMs).toBe(running.scheduledElapsedMs);
    expect(ended.observedRunMs).toBe(running.observedRunMs);
    expect(ended.completedPhaseIndexes).toEqual(running.completedPhaseIndexes);
    expect(ended.completedPhaseIndexes.length).toBeLessThan(plan.phases.length);

    const finalScreen = renderScreen(ended);
    expect(finalScreen).toContain("Simulation finished");
    expect(finalScreen).toContain(`Scheduled duration ${running.scheduledElapsedMs} ms`);
    expect(finalScreen).toContain(`Observed execution ${running.observedRunMs} ms`);
    expect(finalScreen).toContain("Restart simulation");
    expect(finalScreen).toContain("does not record a Workout Session");
    expect(finalScreen).not.toContain("End Simulation");
    expect(finalScreen).not.toContain("Finish Simulation");
    expect(finalScreen).not.toContain(">Start<");
    expect(finalScreen).not.toContain(">Pause<");
    expect(finalScreen).not.toContain(">Resume<");

    const full = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 50000 },
    ]);
    expect(full.scheduledElapsedMs).toBe(50000);
    expect(full.observedRunMs).toBe(50000);
    expect(timelineProjection(plan).at(-1)?.endMs).toBe(50000);
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
