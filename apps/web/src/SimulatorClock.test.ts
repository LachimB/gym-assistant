import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { compileTemplate, initialState } from "@engine";
import { SimulatorClock } from "./SimulatorClock";

const root = resolve(import.meta.dirname, "../../../examples");
const plan = compileTemplate(readFileSync(resolve(root, "templates/composite-pyramid.yaml"), "utf8"), [
  readFileSync(resolve(root, "exercises/bridge-band-family.yaml"), "utf8"),
  readFileSync(resolve(root, "exercises/plank-family.yaml"), "utf8"),
]);

function fixture() {
  let now = 0;
  const clock = new SimulatorClock(plan, () => now);
  return { clock, at: (ms: number) => { now = ms; } };
}

describe("simulator clock with the real reducer", () => {
  it("settles running and paused time at button presses between frames", () => {
    const { clock, at } = fixture();
    clock.setWallClock(true);
    at(10_000);
    clock.send({ op: "start" });
    at(11_025);
    const paused = clock.send({ op: "pause" });
    expect(paused).toMatchObject({ scheduledElapsedMs: 1025, observedRunMs: 1025, pauseMs: 0 });
    at(14_025);
    expect(clock.tick().pauseMs).toBe(3000);
    at(16_025);
    expect(clock.send({ op: "resume" })).toMatchObject({
      scheduledElapsedMs: 1025, observedRunMs: 1025, pauseMs: 5000, completedPhaseIndexes: [],
    });
    at(19_000);
    expect(clock.tick()).toMatchObject({
      scheduledElapsedMs: 4000, observedRunMs: 4000, pauseMs: 5000, unobservedGapMs: 0,
      completedPhaseIndexes: [0],
    });
    expect(clock.state.events.find((event) => event.type === "phaseCompleted"))
      .toMatchObject({ observedRunMs: 4000, pauseMs: 5000 });
  });

  it("produces identical counters and events with coarse or fractional frame intervals", () => {
    function run(step: number) {
      const { clock, at } = fixture();
      let now = 0;
      clock.setWallClock(true);
      clock.send({ op: "start" });
      for (const end of [1025, 6025, 9000]) {
        while (now < end) {
          now = Math.min(now + step, end);
          at(now);
          clock.tick();
        }
        if (end === 1025) clock.send({ op: "pause" });
        if (end === 6025) clock.send({ op: "resume" });
      }
      return clock.state;
    }
    expect(run(16.25)).toEqual(run(50_000));
    expect(run(1000 / 60)).toEqual(run(50_000));
  });

  it("carries fractions rather than rounding every animation frame", () => {
    const { clock, at } = fixture();
    clock.setWallClock(true);
    clock.send({ op: "start" });
    for (let frame = 1; frame <= 1000; frame++) {
      at(frame * 16.25);
      clock.tick();
    }
    expect(clock.state.observedRunMs).toBe(16_250); // Per-frame rounding would give 16,000.
    expect(clock.state.scheduledElapsedMs).toBe(16_250);
  });

  it("keeps fractional running and paused time in separate buckets", () => {
    const { clock, at } = fixture();
    clock.setWallClock(true);
    clock.send({ op: "start" });
    at(0.75);
    clock.send({ op: "pause" });
    at(1.5);
    clock.send({ op: "resume" });
    at(1.75);
    expect(clock.tick()).toMatchObject({ observedRunMs: 1, pauseMs: 0 });
    clock.send({ op: "pause" });
    at(2);
    expect(clock.tick()).toMatchObject({ observedRunMs: 1, pauseMs: 1 });
  });

  it("keeps a 60 Hz preview aligned with cumulative elapsed time", () => {
    const { clock, at } = fixture();
    clock.setWallClock(true);
    clock.send({ op: "start" });
    for (let frame = 1; frame <= 600; frame++) {
      at(frame * 1000 / 60);
      clock.tick();
    }
    expect(clock.state.observedRunMs).toBe(10_000);
  });

  it("excludes time while wall clock is off and retains virtual advances", () => {
    const { clock, at } = fixture();
    clock.send({ op: "start" });
    at(10_000);
    expect(clock.tick().observedRunMs).toBe(0);
    clock.send({ op: "advance", ms: 1000 });
    clock.setWallClock(true);
    at(11_025);
    expect(clock.setWallClock(false).observedRunMs).toBe(2025);
    at(20_000);
    clock.setWallClock(true);
    at(21_000);
    expect(clock.tick().observedRunMs).toBe(3025);
  });

  it("settles elapsed time before navigation and early end", () => {
    const { clock, at } = fixture();
    clock.setWallClock(true);
    clock.send({ op: "start" });
    at(1025);
    expect(clock.send({ op: "stepForward" })).toMatchObject({
      scheduledElapsedMs: 4000, observedRunMs: 1025, completedPhaseIndexes: [],
    });
    at(1050);
    const ended = clock.send({ op: "endEarly" });
    expect(ended).toMatchObject({ scheduledElapsedMs: 4025, observedRunMs: 1050 });
    at(20_000);
    expect(clock.tick()).toEqual(ended);
  });

  it("never assigns an explicitly unobserved interval to running or pause time", () => {
    for (const pauseBeforeGap of [false, true]) {
      const { clock, at } = fixture();
      clock.setWallClock(true);
      clock.send({ op: "start" });
      at(1000);
      clock.tick();
      if (pauseBeforeGap) clock.send({ op: "pause" });
      at(9000);
      const recovered = clock.send({ op: "noteUnobserved", ms: 8000 });
      expect(recovered).toMatchObject({
        scheduledElapsedMs: 1000, observedRunMs: 1000, unobservedGapMs: 8000, completedPhaseIndexes: [],
      });
      at(15_000);
      expect(clock.tick()).toEqual(recovered);
      clock.send({ op: "resume" });
      at(18_000);
      expect(clock.tick()).toMatchObject({ observedRunMs: 4000, pauseMs: 0 });
    }
  });

  it("resets clock mode, anchors, and fractions on restart", () => {
    const { clock, at } = fixture();
    clock.setWallClock(true);
    clock.send({ op: "start" });
    at(0.75);
    clock.tick();
    expect(clock.reset()).toEqual(initialState());
    expect(clock.wallClock).toBe(false);
    at(1000);
    clock.setWallClock(true);
    clock.send({ op: "start" });
    at(1000.5);
    expect(clock.tick().observedRunMs).toBe(0);
  });
});
