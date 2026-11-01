# ADR-0002 — Milestone 0 execution engine sharing

- Status: **Accepted for the Milestone 0 prototype**
- Date: 2026-11-01
- The plan's rule was to share a Kotlin/JS engine with React only if that interop was clean. This measurement says it is not. Android compiles the Kotlin common source. The browser runs an independent TypeScript engine. Both are checked against the same fixtures.

## Context

Milestone 0 has to execute `examples/templates/composite-pyramid.yaml` with a virtual clock on Android and in the browser. Decision #52 allows either a shared Kotlin engine or two conformant engines, as long as the versioned timeline stays the contract.

## What was measured

- Kotlin common code in `packages/engine` compiles to the JVM and to Kotlin/JS IR.
- `./gradlew :engine:jvmTest` matches `examples/expected/composite-pyramid.timeline.json` (50,000 ms, nine phases) and the pause / step / unobserved-gap script.
- `packages/engine-ts` Vitest matches the same golden phases and the same script: scheduled 4,000 ms, observed 4,000 ms, pause 5,000 ms, unobserved 8,000 ms, only phase 0 completed after a skip and rewind.
- `./gradlew :engine:jsNodeProductionLibrary` succeeds. A Node process can `require` the generated CommonJS file and `compileTimelineJson` returns the golden phases. The same command script returns the numbers above.
- The generated output is not a single ESM module. It is a CommonJS factory plus separate files for kaml, okio, kotlinx-serialization, and the Kotlin stdlib, with dependency names such as `gym-assistant:engine`. The TypeScript declaration is a namespace, not a typed reducer the React code can import directly.
- Wiring that output into Vite would be a handwritten bridge. That fails the "clean interop" bar in `M0_START_PROMPT.md`.

## Decision

Keep two engines for Milestone 0:

- Android compiles `packages/engine/src/commonMain` and drives it with `SystemClock.elapsedRealtime()` deltas.
- The web simulator imports `packages/engine-ts`.
- The JSON fixtures remain the cross-platform contract.

Do not make the React app depend on the Kotlin/JS bundle in this milestone.

## Consequences

- A behavior change must be applied in both engines and proven by the shared fixtures.
- Revisit a shared engine only if the Kotlin/JS output becomes an ordinary ESM package that Vite can import without a custom loader. That would be a new ADR, not a silent switch.
- Scheduled time, observed running time, pause duration, and unobserved gaps stay separate fields in both engines. A step forward does not mark the skipped phase complete. An unobserved gap does not advance the scheduled clock and requires an explicit resume.
