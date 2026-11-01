# Cross-agent handoff (maintain on every significant change)

## Current status
- Date: 2026-11-01
- Product specification: approved through Decision #97. Decisions #1–#78 remain binding unless a later decision refines one. Decisions #79–#97 add optional Training Season, Training Program, immutable Training Template revisions, and Workout Session provenance.
- Current milestone: M0 prototype is in the tree on branch `m0-prototype`. It is a feasibility build, not a daily-use app.
- Working code: Kotlin reducer in `packages/engine`, independent TypeScript reducer in `packages/engine-ts`, localhost React simulator in `apps/web`, Compose debug app in `apps/android`, in-memory `TrainingStore` in `packages/cloud-adapters`.
- GitHub: private `LachimB/gym-assistant`. Repository-local author is `MichalB <<private-contact-redacted>>`. Do not change global Git config. GitHub writes must unset `GITHUB_TOKEN` so the keyring login stays `LachimB`. Withdrawn logins: `michal-buzek` and `MichalB`.
- Cloud resources / OAuth / hosting: none provisioned. No Google API calls.
- Engine choice: ADR-0002. Kotlin/JS compiles and a Node require reproduced the navigation trace, but the bundle is a multi-file CommonJS factory. The React app uses the TypeScript engine. Android compiles the Kotlin common source. The plan's rule was to share Kotlin/JS only when that import is clean, so the prototype does not share it.

## Task identifiers

Use [`docs/milestones/m0.md`](../milestones/m0.md). Do not reuse these IDs for other work.

| ID | Task | Status |
|---|---|---|
| M0-01 | Repository skeleton, personal GitHub preflight, CI, local docs | Private repo exists. CI workflow also runs the Kotlin JVM tests, the in-memory store tests, and the web unit tests. Emulator CI stays out of M0. |
| M0-02 | Schema/fixture confirmation and virtual-clock verifier for the 50,000 ms plan | JVM and TypeScript tests cover the golden timeline, pause, step, and unobserved-gap recovery. |
| M0-03 | KMP Kotlin/JS versus independent Kotlin and TypeScript engines; ADR-0002 | Measured. Separate engines for this prototype. |
| M0-04 | Minimal Compose Android UI and debug install | Debug APK accepted as a first prototype on Pixel 10a. Complete Workout acknowledges the terminal state and does not save a Workout Session. Screen-off and long-duration background reliability are not tested. |
| M0-05 | React browser simulator | Exercised on `http://127.0.0.1:5173`. No Workout Session write. Finish Simulation acknowledges the terminal state. |
| M0-06 | Background timing and clock-semantics notes on a real Android 12+ phone | Basic screen-lock background continuation passed on 2026-11-01. Clean restart after process termination passed the same day. Persistent recovery is not implemented. Full process-death recovery did not pass. Screen-off and long-duration background reliability are not tested. |
| M0-07 | Provider-neutral in-memory store and a written Drive scope proposal | `InMemoryTrainingStore` tests and `drive.file` proposal. No OAuth. |
| M0-08 | Architecture findings, demo, and explicit pass/fail matrix | This file. |

## Session to-dos

| ID | Status | Boundary |
|---|---|---|
| m0-01-repo | done | Private `LachimB/gym-assistant` created and `main` plus `m0-01-identity` pushed with the token override removed. |
| m0-03-spike | done | ADR-0002. Kotlin/JS is not the web runtime. |
| engine-checkpoint | done for this prototype | The written rule selected separate engines. A later rejection of sharing does not require removing the two engines already in use. Sharing them later needs a new ADR. |
| m0-02-engine | done | Four time records stay separate. A skip does not add the current phase to `completedPhaseIndexes`. No Season or Program runtime. |
| m0-05-web | done | Localhost only. Virtual +1s is the conformance control. |
| m0-04-android | done as a debug build | `minSdk` 31, application id `dev.gymassistant.m0.debug`, simulated cue. Complete Workout acknowledges the terminal state and does not save a Workout Session. |
| m0-06-device | screen-lock pass; recovery not in M0 | Pixel 10a, Android 17, 2026-11-01. First launch was blocked by the lock screen. Later, execution continued with the screen locked. After process termination the app restarted cleanly and the unfinished position was lost. Full recovery is an M1 gate. |
| m0-07-store | done | In-memory compare-and-swap. Scope proposal only. |
| m0-08-close | matrix recorded, milestone open | Matrix below. M0 is not closed. Untested and unsupported lines stay marked that way. |

## M0 acceptance matrix

| Checkbox in `docs/milestones/m0.md` | Result |
|---|---|
| Android app installable on a physical Android 12+ phone, launch and run a fixture | Pass. Install succeeded on Pixel 10a / Android 17. The first launch on 2026-11-01 was blocked by the lock screen, so that attempt did not show the workout. Later the same day the fixture ran. Screen-off was not separately tested. |
| Web simulator runs in a browser with the same fixture | Pass. `http://127.0.0.1:5173` showed prepare, hip-lift variation, yellow band, set, and rep. The page states that it does not record a Workout Session. |
| Composite fixture alternates repetitions and has a timed rest plus differing resistance | Pass. Existing golden file: eight phases, 50,000 ms. Contract check still covers it. |
| Both show phase progression and pause, resume, step forward, and step backward; a skip does not complete a rep | Partial. Web pass. Pause held scheduled time at 3,000 ms and raised pause to 1,000 ms. Step forward landed on hold at scheduled 4,000 ms without moving observed time. Step back returned to prepare at scheduled 0 ms and kept observed 3,000 ms and pause 1,000 ms. Android buttons exist in the debug UI. Their on-device behavior is **not tested**. Unit tests cover skip and unobserved-gap recovery on both engines. |
| Virtual clock tests match the 50,000 ms phase order. Wall time and speech are not the clock source | Pass. JVM and TypeScript suites. The browser wall-clock toggle is display-only. Acknowledgement does not change the 50,000 ms trace. |
| Written analysis chooses shared KMP/JS or separate engines | Pass. ADR-0002 chooses separate engines and keeps the JSON fixtures as the contract. |
| CI runs tests and rejects unexpected private fixtures or credentials | Pass on pull request 2. The workflow runs Python contracts, `:engine:jvmTest`, `:cloud-adapters:test`, and the two npm test suites. Hygiene check remains `tools/check_repo_hygiene.py`. |
| Installation and browser instructions plus physical-device smoke notes | Pass. Instructions are in `apps/android/README.md` and `apps/web/README.md`. Smoke notes are in `docs/milestones/m0-device-notes.md`. |
| Basic screen-lock background execution | Pass. Pixel 10a, Android 17, 2026-11-01. Workout execution continued while the screen was locked. This is not proof of process-death recovery or long-duration background reliability. Long-duration background reliability is **not tested**. Screen-off was not separately tested. |
| Clean application restart after process termination | Pass. Pixel 10a, Android 17, 2026-11-01. Michal terminated an unfinished workout and reopened Gym Assistant. The app restarted cleanly and did not crash. |
| Persistent workout recovery | Unsupported / not implemented. The unfinished execution position was lost. M0 does not record a Workout Session. Reliable recovery is mandatory in M1 and is not an M0 gate. |
| Full process-death recovery | Not passed. Clean restart did not restore the previous execution position. |
| Completion acknowledgment | Pass. After the final phase, Android shows Complete Workout and the web simulator shows Finish Simulation. Start, Pause, and Resume are not shown in the terminal state. Acknowledging shows the total scheduled duration and the observed execution duration. Restart returns to the loaded prototype. No Workout Session is stored. |

## Next actions
1. Review the clock-accounting slice on `codex/m0-clock-accounting`, then complete the remaining phone smoke below. Android/web now use injected-clock adapters to settle elapsed time at timer callbacks and commands, preserve pause counters, and carry browser fractional milliseconds. M0 closure still requires user approval and the outstanding device evidence.
2. Finish the remaining phone smoke in `docs/milestones/m0-device-notes.md`: on-device pause, resume, and step, plus screen-off and on. Do not add a foreground service unless that smoke shows the activity timer is insufficient and there is a separate yes. Do not treat the clean process restart as recovery. Long-duration background reliability is still untested. Unfinished-session recovery waits for M1.
3. Keep GitHub writes on `LachimB` with token variables unset. Author stays `MichalB <<private-contact-redacted>>` in the repository only.
4. Do not start Google OAuth, hosting, Play signing, or a billable resource without a separate approval.
5. Early end is available in the prototype; detailed partial-performance summaries stay later.

## Clock categories

Scheduled phase time, observed monotonic execution time, pause duration, and unobserved gaps stay separate. Never record an unobserved gap as completed exercise. Recovery shows the last checkpoint and waits for an explicit decision. Details: [`docs/architecture/execution.md`](../architecture/execution.md).

## Testing scope

The M0 engine spike does not include an Android emulator matrix. Automated emulator testing across supported Android versions remains mandatory from M1 onward (Decisions #48 and #54).

## Record each session
- Branch: `m0-prototype`, after `m0-01-identity`.
- Tests: Python contract tests passed (23). Kotlin `:engine:jvmTest` and `:cloud-adapters:test` passed in the prototype build. `packages/engine-ts` and `apps/web` Vitest passed, including the terminal state and the completion acknowledgment. Browser check on 2026-11-01 covered start, virtual +1s, pause, step forward, step back, Finish Simulation, the final scheduled and observed durations, and restart.
- Android: Pixel 10a, Android 17, 2026-11-01. First install attempt was blocked by the lock screen. Later, workout execution continued with the screen locked. That basic background pass is not long-duration evidence. Michal then terminated an unfinished workout: the app restarted cleanly and did not restore the previous position. Persistent recovery is not implemented. Screen-off remains not tested. These device results were reported by Michal; this session did not re-run the screen-lock or process-termination checks. Complete Workout does not save a Workout Session.
- Web URL: `http://127.0.0.1:5173` on the local Vite server. No hosted preview. The simulator does not record a Workout Session.
- New external services: none.
- Unresolved: screen-off, on-device pause/resume/step, long-duration background reliability, M1 unfinished-session recovery, and any later decision to share the engine despite the Kotlin/JS packaging. M0 stays open. Pull request 2 is https://github.com/LachimB/gym-assistant/pull/2.

## Event timestamp conformance review — 2026-11-01

- Branch: `codex/m0-event-conformance`, based on `m0-prototype` at `df7f3db`.
  User accepted the independent review and authorized commit, push, and a focused
  PR targeting `m0-prototype` after the P3 rewind coverage gap was addressed.
  Published implementation commit: `3d945be1dd790acb3dc0e5525bbcf888de913fcf`.
  Review: [PR #3](https://github.com/LachimB/gym-assistant/pull/3), merged by the
  user into `m0-prototype`; contracts, engine, and web CI passed before merge.
  Personal credential preflight passed as `LachimB`; push used the repository's
  credential wrapper. Do not merge automatically. M0 remains open.
- Scope: GA-EXE-003 / GA-WEB-003. Both reducers now snapshot the consumed observed
  running time at each crossed phase boundary for completion and next-phase start.
  Scheduled, pause, and unobserved counters retain their separate meanings. The
  event-counter contract is in `docs/architecture/execution.md`; separate Kotlin
  and TypeScript engines remain the ADR-0002 decision.
- Shared evidence: `examples/expected/composite-pyramid.events.json` contains seven
  independent expected full-event sequences and final counter/completion states.
  Both engines exercise all seven under three advance partitions (21 combinations
  per engine): coarse, 1,000 ms, and irregular 1/333/4,096 ms. Cases cover overshoot,
  pause/resume, running/paused gaps, explicit recovery, skip, and rewind. Gradle
  declares the example fixtures as test inputs so fixture edits invalidate results.
- Regression proof: before modifying the reducers, the first five shared cases
  caused 12 TypeScript failures and the new Kotlin conformance test failed. Old
  tests checked event names and final counters but missed stale event timestamps.
- Independent review: no blocking implementation defects reported by the user;
  one demonstrated P3 coverage gap concerned rewind after phases had completed.
  Added checkpoints before and immediately after rewind: `[0, 1]` at scheduled
  12,000 ms becomes `[0]` at 4,000 ms while observed remains 12,000 ms. Replayed
  hold completes at scheduled 12,000 ms / observed 20,000 ms. Both engines pass
  without additional production changes. An isolated TypeScript mutation that
  disabled completion clearing failed at the intermediate checkpoint in all
  three partitions; final-state-only checks could have missed that mutation.
- Validation: Python 23 tests; TypeScript engine 25 tests (21 new); web 3 tests;
  Kotlin engine 7 tests (one new test runs all 21 combinations); store 1 test.
  Fresh compilation of Kotlin production and test sources was verified with
  `./gradlew :engine:jvmTest :cloud-adapters:test :android:assembleDebug --rerun-tasks --no-build-cache`
  (43 tasks executed; no compiled-class cache reuse).
  Android debug build passes. Contract validator and repository hygiene pass.
  Web production build still fails on missing Node type declarations in
  `apps/web/src/simulator.test.ts`; deliberately outside this change. The existing
  Android Gradle plugin / compileSdk compatibility warning remains.
- Limits: no UI clock-adapter changes, persistent recovery, new services, device
  install, or new phone/browser observations. App pause accounting, runtime input
  validation, remaining device smoke, and M0 closure are still outstanding.

## Clock-adapter accounting — 2026-11-01

- Approved slice: `codex/m0-clock-accounting`, based on merged `origin/m0-prototype`
  at `d8bf65f`. PR #1 and #3 were merged by the user; PR #2 stays open against main.
  Published implementation: `5aa176fcd16d58052d241db2c33abe17324e8366` in
  [PR #4](https://github.com/LachimB/gym-assistant/pull/4), targeting `m0-prototype`.
  See PR checks for live CI results. Personal credential preflight passed as
  `LachimB`; the push used the repository's credential wrapper. Do not auto-merge.
- Android uses `ClockedExecution` (common Kotlin, injected elapsedRealtime clock);
  web uses independent `SimulatorClock` (injected performance.now). Both settle
  elapsed time in the prior status before actions. Paused time grows without
  consuming scheduled/observed running time. Browser running/pause fractions are
  separate, and wall-clock toggle/restart anchors exclude disabled/old intervals.
- Reducer semantics and shared full-event fixtures are unchanged. No persistence,
  M1 features, new service, permission, dependency, or schema migration was added.
  Explicit gap inputs reanchor without inventing progress; automatic gap detection
  and process recovery are not implemented.
- Tests: five Kotlin adapter integration tests and nine web adapter tests use the
  real reducers. They cover between-callback actions, coarse/fractional ticks,
  pause/resume, navigation/end, explicit running/paused gaps, toggles, and reset.
  Isolated TypeScript mutations dropping paused time and rounding every frame
  caused two and five assertion failures respectively; production files unchanged.
- Local validation: Kotlin engine 12 tests, store 1, TypeScript engine 25, web 12,
  Python contracts 23 (73 total). Fresh Kotlin production/test compilation plus
  Android assembly passed with `--rerun-tasks --no-build-cache`; 43 tasks executed.
  Contract validator and repository hygiene pass. Existing web build still fails
  on missing Node test type declarations (now also reported by the new fixture-
  loading test); the root build-configuration issue is deliberately not fixed.
- Browser smoke on localhost: during Pause, scheduled/observed stayed at 4,021 ms
  while pause grew from 138 ms to 24,424 ms. Resume plus stopping preview yielded
  scheduled/observed 4,136 ms and pause 27,176 ms. Browser smoke verifies React
  wiring; it is not Android, background-tab, or long-duration evidence.
- Remaining: physical-phone pause/resume/navigation and screen-off checks, runtime
  malformed-input handling, known web production build failure, and existing
  Android Gradle plugin / compileSdk warning. M0 remains open. Do not auto-merge.

## Approved domain extension: Decisions #79–#97

Read `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001. Canonical names: Training Season, Training Program, Training Template, Workout Session. Basic organization is P0 for the daily-use release. The M0 executable fixture stays a standalone template. Organization schemas and contract tests exist. The prototype does not resolve Program membership at runtime. Most important unresolved M2 detail: deterministic expansion of limited set-use adjustments within an explicit composite step order. Before implementing user-facing promotion, require explicit approval, a scope preview, and a stale-revision check.
