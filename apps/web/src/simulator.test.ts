import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { compileTemplate, reduceAll, snapshot, timelineProjection } from "@engine";

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
});
