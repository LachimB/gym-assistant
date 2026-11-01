import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { compileTemplate, reduceAll, snapshot, timelineProjection } from "./engine";

const root = resolve(import.meta.dirname, "../../../examples");

function loadPlan() {
  return compileTemplate(readFileSync(resolve(root, "templates/composite-pyramid.yaml"), "utf8"), [
    readFileSync(resolve(root, "exercises/bridge-band-family.yaml"), "utf8"),
    readFileSync(resolve(root, "exercises/plank-family.yaml"), "utf8"),
  ]);
}

describe("typescript engine", () => {
  it("matches the 50000 ms golden timeline", () => {
    const plan = loadPlan();
    const expected = JSON.parse(readFileSync(resolve(root, "expected/composite-pyramid.timeline.json"), "utf8"));
    expect(plan.expectedDurationMs).toBe(50000);
    expect(timelineProjection(plan)).toEqual(expected.phases);
  });

  it("keeps scheduled time, pause, and unobserved gaps separate", () => {
    const plan = loadPlan();
    const state = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 1000 },
      { op: "pause" },
      { op: "advance", ms: 5000 },
      { op: "resume" },
      { op: "advance", ms: 3000 },
      { op: "stepForward" },
      { op: "stepBackward" },
      { op: "noteUnobserved", ms: 8000 },
      { op: "resume" },
    ]);
    const view = snapshot(plan, state);
    expect(view).toMatchObject({
      status: "running",
      scheduledElapsedMs: 4000,
      observedRunMs: 4000,
      pauseMs: 5000,
      unobservedGapMs: 8000,
      activePhaseIndex: 1,
      completedPhaseIndexes: [0],
    });
    expect(state.events.map((item) => item.type)).toEqual([
      "sessionStarted",
      "phaseStarted",
      "pause",
      "resume",
      "phaseCompleted",
      "phaseStarted",
      "skip",
      "phaseStarted",
      "rewind",
      "phaseStarted",
      "unobservedGap",
      "resumeAfterGap",
    ]);
  });
});
