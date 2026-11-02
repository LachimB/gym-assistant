import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { compileTemplate, reduce, reduceAll, snapshot, timelineProjection } from "./engine";

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

  it("treats completion as terminal and acknowledges without changing the trace", () => {
    const plan = loadPlan();
    const finished = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 50000 },
    ]);
    expect(finished.status).toBe("completed");
    expect(finished.completionAcknowledged).toBe(false);
    expect(finished.scheduledElapsedMs).toBe(50000);
    expect(finished.observedRunMs).toBe(50000);
    for (const command of [
      { op: "start" } as const,
      { op: "pause" } as const,
      { op: "resume" } as const,
      { op: "advance", ms: 1000 } as const,
      { op: "stepForward" } as const,
      { op: "stepBackward" } as const,
      { op: "noteUnobserved", ms: 2000 } as const,
    ]) {
      expect(reduce(plan, finished, command)).toEqual(finished);
    }
    const running = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 1000 },
    ]);
    expect(reduce(plan, running, { op: "acknowledge" })).toEqual(running);

    const skipped = reduceAll(plan, [
      { op: "start" },
      { op: "stepForward" },
      { op: "advance", ms: 50000 },
    ]);
    const acknowledged = reduce(plan, skipped, { op: "acknowledge" });
    expect(acknowledged.completionAcknowledged).toBe(true);
    expect(acknowledged.status).toBe("completed");
    expect(acknowledged.scheduledElapsedMs).toBe(skipped.scheduledElapsedMs);
    expect(acknowledged.observedRunMs).toBe(skipped.observedRunMs);
    expect(acknowledged.pauseMs).toBe(skipped.pauseMs);
    expect(acknowledged.unobservedGapMs).toBe(skipped.unobservedGapMs);
    expect(acknowledged.completedPhaseIndexes).toEqual(skipped.completedPhaseIndexes);
    expect(acknowledged.events).toEqual(skipped.events);
    expect(acknowledged.scheduledElapsedMs).toBe(50000);
    expect(acknowledged.observedRunMs).toBeLessThan(acknowledged.scheduledElapsedMs);
    expect(reduce(plan, acknowledged, { op: "acknowledge" })).toEqual(acknowledged);
  });

  it("ends a partial run early without completing the remaining phases", () => {
    const plan = loadPlan();
    const running = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 4174 },
    ]);
    const ended = reduce(plan, running, { op: "endEarly" });
    expect(ended.status).toBe("completed");
    expect(ended.completionAcknowledged).toBe(true);
    expect(ended.scheduledElapsedMs).toBe(running.scheduledElapsedMs);
    expect(ended.observedRunMs).toBe(running.observedRunMs);
    expect(ended.pauseMs).toBe(running.pauseMs);
    expect(ended.unobservedGapMs).toBe(running.unobservedGapMs);
    expect(ended.completedPhaseIndexes).toEqual(running.completedPhaseIndexes);
    expect(ended.events).toEqual(running.events);
    expect(ended.completedPhaseIndexes.length).toBeLessThan(plan.phases.length);
    expect(reduce(plan, ended, { op: "advance", ms: 10000 })).toEqual(ended);
    expect(reduce(plan, ended, { op: "endEarly" })).toEqual(ended);

    const paused = reduce(plan, running, { op: "pause" });
    const endedPaused = reduce(plan, paused, { op: "endEarly" });
    expect(endedPaused.status).toBe("completed");
    expect(endedPaused.completionAcknowledged).toBe(true);
    expect(endedPaused.scheduledElapsedMs).toBe(paused.scheduledElapsedMs);
    expect(endedPaused.observedRunMs).toBe(paused.observedRunMs);
    expect(endedPaused.pauseMs).toBe(paused.pauseMs);
    expect(endedPaused.unobservedGapMs).toBe(paused.unobservedGapMs);
    expect(endedPaused.completedPhaseIndexes).toEqual(paused.completedPhaseIndexes);
    expect(reduce(plan, endedPaused, { op: "advance", ms: 5000 })).toEqual(endedPaused);

    const full = reduceAll(plan, [
      { op: "start" },
      { op: "advance", ms: 50000 },
    ]);
    expect(full.scheduledElapsedMs).toBe(50000);
    expect(full.observedRunMs).toBe(50000);
    expect(full.completedPhaseIndexes).toEqual(plan.phases.map((phase) => phase.index));
    expect(full.completionAcknowledged).toBe(false);
    expect(reduce(plan, full, { op: "endEarly" })).toEqual(full);
  });
});
