import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { compileTemplate, initialState, reduce, snapshot, type EngineCommand, type EngineEvent } from "./engine";

type ExpectedEventState = Pick<ReturnType<typeof snapshot>,
  "status" | "scheduledElapsedMs" | "observedRunMs" | "pauseMs" | "unobservedGapMs" | "completedPhaseIndexes">;

interface EventFixture {
  template: string;
  exercises: string[];
  advancePartitionsMs: number[][];
  cases: {
    name: string;
    commands: EngineCommand[];
    expectedEvents: EngineEvent[];
    expectedState: ExpectedEventState;
    checkpoints?: { afterCommand: number; expectedState: ExpectedEventState }[];
  }[];
}

const root = resolve(import.meta.dirname, "../../../examples");
const fixture: EventFixture = JSON.parse(
  readFileSync(resolve(root, "expected/composite-pyramid.events.json"), "utf8"),
);
const plan = compileTemplate(
  readFileSync(resolve(root, fixture.template), "utf8"),
  fixture.exercises.map((path) => readFileSync(resolve(root, path), "utf8")),
);

describe("shared full-event conformance", () => {
  for (const testCase of fixture.cases) {
    for (const partition of fixture.advancePartitionsMs) {
      it(`${testCase.name}, partition=${partition}`, () => {
        let state = initialState();
        for (const [commandIndex, command] of testCase.commands.entries()) {
          if (command.op === "advance") {
            let remaining = command.ms;
            let index = 0;
            while (remaining > 0) {
              const delta = Math.min(remaining, partition[index++ % partition.length]);
              state = reduce(plan, state, { op: "advance", ms: delta });
              remaining -= delta;
            }
          } else {
            state = reduce(plan, state, command);
          }
          for (const checkpoint of testCase.checkpoints ?? []) {
            if (checkpoint.afterCommand === commandIndex + 1) {
              expect(snapshot(plan, state), `after command ${checkpoint.afterCommand}`)
                .toMatchObject(checkpoint.expectedState);
            }
          }
        }
        expect(state.events).toEqual(testCase.expectedEvents);
        expect(snapshot(plan, state)).toMatchObject(testCase.expectedState);
      });
    }
  }
});
