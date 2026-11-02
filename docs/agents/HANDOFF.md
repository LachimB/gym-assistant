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
| M0-04 | Minimal Compose Android UI and debug install | Debug APK accepted as a first prototype on Pixel 10a. Complete Workout acknowledges the terminal state and does not save a Workout Session. Short screen-off/on smoke was reported passing after the PR #4 APK install. Long-duration reliability is not tested. |
| M0-05 | React browser simulator | Exercised on `http://127.0.0.1:5173`. No Workout Session write. Finish Simulation acknowledges the terminal state. |
| M0-06 | Background timing and clock-semantics notes on a real Android 12+ phone | Basic screen-lock background continuation passed on 2026-11-01. Clean restart after process termination passed the same day. Persistent recovery is not implemented. Full process-death recovery did not pass. Short screen-off/on smoke was reported passing after the PR #4 APK install. Long-duration reliability is not tested. |
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
| Android app installable on a physical Android 12+ phone, launch and run a fixture | Pass. Install succeeded on Pixel 10a / Android 17. Earlier launch was blocked by the lock screen; later fixture execution and the final guided pause/navigation/screen-off smoke passed by user report. See m0-device-notes.md for tested APK identity. |
| Web simulator runs in a browser with the same fixture | Pass. `http://127.0.0.1:5173` showed prepare, hip-lift variation, yellow band, set, and rep. The page states that it does not record a Workout Session. |
| Composite fixture alternates repetitions and has a timed rest plus differing resistance | Pass. Existing golden file: nine phases, 50,000 ms. Contract and both engine suites cover it. |
| Both show phase progression and pause, resume, step forward, and step backward; a skip does not complete a rep | Pass for the basic prototype scope. Browser smoke and real-reducer clock/event tests pass. After the tested PR #4 APK was installed, the user reported phone pause/resume and Forward/Back while paused passing. Automated tests verify skipped phases are not fabricated as complete. Numerical foreground counters subsequently verified with capture uncertainty; see m0-device-notes.md. |
| Virtual clock tests match the 50,000 ms phase order. Wall time and speech are not the clock source | Pass. JVM and TypeScript suites. Wall-clock preview drives the dry-run through the tested clock adapter; virtual time remains the conformance control. Acknowledgement does not change the 50,000 ms trace. |
| Written analysis chooses shared KMP/JS or separate engines | Pass. ADR-0002 chooses separate engines and keeps the JSON fixtures as the contract. |
| CI runs tests and rejects unexpected private fixtures or credentials | Pass on pull request 2. The workflow runs Python contracts, `:engine:jvmTest`, `:cloud-adapters:test`, and the two npm test suites. Hygiene check remains `tools/check_repo_hygiene.py`. |
| Installation and browser instructions plus physical-device smoke notes | Pass. Instructions are in `apps/android/README.md` and `apps/web/README.md`. Smoke notes are in `docs/milestones/m0-device-notes.md`. |
| Basic screen-lock background execution | Pass. Pixel 10a, Android 17, 2026-11-01. Workout execution continued while the screen was locked. This is not proof of process-death recovery or long-duration background reliability. Long-duration background reliability is **not tested**. Short screen-off/on while running and paused was subsequently reported passing on the tested APK. |
| Clean application restart after process termination | Pass. Pixel 10a, Android 17, 2026-11-01. Michal terminated an unfinished workout and reopened Gym Assistant. The app restarted cleanly and did not crash. |
| Persistent workout recovery | Unsupported / not implemented. The unfinished execution position was lost. M0 does not record a Workout Session. Reliable recovery is mandatory in M1 and is not an M0 gate. |
| Full process-death recovery | Not passed. Clean restart did not restore the previous execution position. |
| Completion acknowledgment | Pass. After the final phase, Android shows Complete Workout and the web simulator shows Finish Simulation. Start, Pause, and Resume are not shown in the terminal state. Acknowledging shows the total scheduled duration and the observed execution duration. Restart returns to the loaded prototype. No Workout Session is stored. |

## Next actions
1. Review the closeout evidence in `docs/milestones/m0.md` and `m0-device-notes.md`, including the sanitized numerical timing log. All eight M0 evidence boxes are checked; milestone approval is still pending. Keep PR #2 open until the user approves closure and chooses to merge it.
2. PRs #3–#8 are merged into `m0-prototype`. Web production build, dependency audits, and all three CI jobs pass. The closeout branch `codex/m0-closeout` contains documentation/evidence only; review its PR before final PR #2 approval.
3. Do not claim long-duration background reliability, precise numerical drift guarantees, or other-OS/audio behavior. No foreground service without separate approval. Unfinished-session persistence/recovery and emulator coverage are M1 work; do not start M1 automatically.
4. Follow the approved automatic debug-APK installation workflow after local Android builds. Do not uninstall or clear data. No Android rebuild is needed for this docs-only slice.
5. GitHub writes must use `LachimB` and personal preflight; no new service or billable resource without the required separate approvals.

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
  Merged by the user; contracts, engine, and web checks passed before merge.
  Personal credential preflight passed as
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

## CI maintenance — 2026-11-01

- Approved separate slice: `codex/ci-maintenance`, based on `origin/m0-prototype`
  at `a55c63e`, including merged PR #4. PR #2 stays open against main.
  Published implementation `1cc3520b113de737441259ffc0b8ccf7f9bfbcf9` is in
  [PR #5](https://github.com/LachimB/gym-assistant/pull/5), targeting `m0-prototype`.
  Merged by the user. Final CI head `fed5d432a366cad0fb083c3df4f022465a10291b` passed contracts, engine, and web; full annotation endpoints were empty for all three jobs.
- Replaces deprecated Node-20-based actions with reviewed stable releases:
  checkout 7.0.1, setup-python 7.1.0, setup-java 6.1.0, setup-node 7.1.0.
  Official tagged action manifests declare Node 24. Test toolchains remain
  Python 3.12, Temurin 21, and Node 22. Explicitly disables setup-node automatic
  package-manager caching to preserve the previous setup behavior.
- All jobs use `ubuntu-24.04` to avoid the ubuntu-latest release migration.
  This fixes the OS release, not the hosted image's patch level. Triggers,
  repository permissions, test commands, and application code are unchanged.
- Validation must include all three remote jobs and their complete annotations;
  check passes alone do not prove that deprecation/migration warnings disappeared.
  Local workflow parsing and comparison verify the preserved settings.
- No new external service, permission, billable resource, dependency, deployment,
  or data/schema change. Existing web production-build typing failure, Android
  tooling warning, and outstanding device smoke remain outside this slice.
  M0 remains open. Do not auto-merge.

## Final phone smoke and remaining M0 review — 2026-11-01

- Documentation branch: `codex/m0-device-evidence`, from merged `m0-prototype`
  `a7c596f`. PR #2 remains open. No application or test behavior changed.
- The first reported pause-counter failure was on an APK whose checksum did not
  match the tested artifact. After `adb install -r` succeeded, the user reported
  all four guided manual tests passed. Rechecking through ADB confirmed the
  installed artifact matches the local APK. Phone: Pixel 10a, Android 17/API 37.
- APK SHA-256: `7ff9c889489561e1c6ff7c0592070cf562cca88ead1fd38d6d385ea8fb301138`.
  Application source contains PR #4 (`5aa176f`); later CI/docs changes are not app
  behavior changes. Device scenarios and limits are in `m0-device-notes.md`.
- Passes are user-reported: pause/resume after about 10 seconds; Forward/Back while
  paused; screen off/on while running; screen off/on while paused. Codex verified
  install/build/device identity, not the physical interactions or numeric drift.
- No new counter logs, long-duration/continuous background/audio observations,
  emulator matrix, or process recovery evidence. Historical earlier "not tested"
  notes remain chronological; this section supplies the latest short-smoke result.
- Existing automated evidence: 73 tests and Android assembly passed for PR #4;
  subsequent PR #5 changed only CI/docs and passed all remote checks without
  annotations. Documentation-only changes do not require rebuilding/reinstalling.
- Persisted the user's request to auto-install successful local Android debug
  builds when exactly one authorized device is connected. See working agreement.
- Proposed next small task: repair web production build and run it in CI. No fix
  implemented here. Review remaining gaps, then seek explicit M0 closure approval;
  do not merge PR #2 or claim daily-use readiness automatically.

## Web production build — 2026-11-01

- Approved slice: `codex/m0-web-build`, from merged `m0-prototype` at `193f3bd`
  (PR #6 merged). PR #2 stays open. No runtime application or engine changes.
- Reproduced six compiler errors before the fix: the two web fixture tests could
  not resolve `node:fs`, `node:path`, or `import.meta.dirname`. Vitest passed because
  it transpiles without the production build's TypeScript check.
- Added direct dev dependency `@types/node` 22.20.5, matching project/CI Node 22,
  and its declaration-only `undici-types` dependency. No existing package versions
  changed or were removed. npm regenerated some peer metadata in the lockfile.
  No test exclusion, type-check relaxation, tsconfig change, or source workaround.
- CI now runs `npm run build` after web tests in the existing `web` job. The build
  includes `tsc --noEmit` and Vite bundling; triggers/permissions/jobs are unchanged.
- User requested explicit communication in every project chat. Persisted in
  `AGENTS.md`: say when no input is needed; otherwise state the exact action,
  location, expected response, and whether it blocks work. Final task updates
  must explicitly name the user's next action or say no input is needed.
  Requests needing a response use the exact bold three-line red-circle banner
  defined in `AGENTS.md` (updated at the user's request); ordinary output stays
  concise without repeated no-input-needed updates.
- Local evidence: clean `npm ci`, 12 web tests, 25 TypeScript engine tests, 23 Python
  tests, schema/traceability validator, and production build all pass. Built 108
  modules successfully. Kotlin/Android source is unchanged; no Android build or
  installation was needed for this slice. CI reruns all existing suites remotely.
- Production-bundle browser smoke on localhost port 4173 passed: start, virtual
  advance, pause (scheduled/observed/pause 1,000/1,000/1,000 ms), resume,
  Forward/Back (scheduled 0, observed/pause 1,000 ms), and End Simulation. The final
  screen still states that it does not record a Workout Session.
- npm audit found three existing development dependency findings: moderate
  `@vitest/mocker`, critical `tinypool`, critical `vitest`. Baseline and updated
  lockfile audits were identical. Recorded upstream advisories in testing docs;
  no force-upgrade or exploitability claim. Separate dependency review recommended.
- Existing Android tooling warning, missing numerical device drift logs, and
  long-duration/other-OS/audio evidence remain. M0 closure still needs explicit
  approval; do not merge PR #2 or advance to M1 automatically.

## Test dependency security — 2026-11-01

- Approved slice: `codex/m0-test-dependency-security`, from merged `m0-prototype`
  at `7f00e33` (PR #7 merged). PR #2 remains open. No engine/application source,
  test assertions, fixture, schema, permission, or Android change.
- Both npm package baselines reported moderate mocker, critical Tinypool, and
  critical aggregate Vitest findings. Reviewed all four linked upstream advisories
  and the Vitest 4 migration guide. Repository scripts use `vitest run`; no direct
  standalone mocker/interceptor plugin, browser-mode, or custom pool use found.
  This is configured exposure assessment, not proof of non-exploitability.
- Updated both packages to `vitest: ^4.1.12` (locked 4.1.12, including mocker), the
  smallest patched major line covering both mocker issues. Vitest 4 removes
  Tinypool. Existing Node 22 CI and Vite versions satisfy the supported range.
- Web retains Vite 6.4.5; engine retains Vite 7.3.7, now an explicit dev dependency
  to prevent an unrelated transitive Vite 8 upgrade. Existing TypeScript, React,
  React DOM, and YAML versions retained. Only the test-toolchain graph changes.
- Clean `npm ci` succeeds in both packages. Unchanged suites pass: engine TS 25,
  web 12; web type-check/production build succeeds with the same asset names and
  sizes as PR #7. Python contracts 23 and schema/traceability checks also pass.
  No local Android build/reinstall needed for development-toolchain-only changes.
- Both explicit audits including dev dependencies report zero findings at every
  severity. CI uses `npm ci` and adds two `npm audit --include=dev --audit-level=moderate`
  gates; existing tests and production build remain enabled. No forced upgrade,
  overrides, advisory suppression, test edits, or changed application semantics.
- Full rationale, upstream links, baseline counts, and repeatable commands:
  `docs/operations/npm-dependency-security.md`. Zero current audit findings do not
  guarantee absence of undiscovered vulnerabilities. CI advisory-feed failures
  require investigation rather than skipping the gate.
- Next: review this PR and remaining M0 acceptance gaps, including numerical phone
  drift evidence and the existing Android tooling warning. Long-duration/audio/
  other-OS/recovery requirements are still unproven or later milestone work.
  M0 closure and merging PR #2 still require explicit approval. Do not auto-merge.

## Approved domain extension: Decisions #79–#97

Read `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001. Canonical names: Training Season, Training Program, Training Template, Workout Session. Basic organization is P0 for the daily-use release. The M0 executable fixture stays a standalone template. Organization schemas and contract tests exist. The prototype does not resolve Program membership at runtime. Most important unresolved M2 detail: deterministic expansion of limited set-use adjustments within an explicit composite step order. Before implementing user-facing promotion, require explicit approval, a scope preview, and a stale-revision check.

## M0 closeout evidence — 2026-11-01

- Approved closeout branch `codex/m0-closeout`, based on `m0-prototype` merge
  `95ff463` (PR #8). No application, engine, schema, test, dependency or CI change.
- Codex verified six foreground UI samples on the same checksum-verified Pixel
  10a / Android 17 APK. Running increased 8,727 ms; paused running/scheduled stayed
  at 13,992 ms while pause increased 5,647 ms; resumed running increased 5,765 ms
  while pause stayed at 8,805 ms. All gaps were zero. Increments lie within the
  sampled uptime intervals. Capture/render latency prevents precise drift bounds.
- Reproducible method, limits, initial failed capture exclusion, and sanitized
  counters/command brackets: `docs/milestones/m0-device-notes.md` and
  `docs/milestones/m0-device-timing.json`. No raw device screenshots/identifiers
  or private history committed. This is foreground evidence; prior screen-off
  passes remain user-reported, not continuous callback/audio measurements.
- Reconciled M0 checklist, GAP-02, timing/operations traceability and ADR-0002's
  phase count (nine, not eight). No requirement or architecture decision weakened.
- Preserved the user's exact bold three-line input-request banner in AGENTS.md.
  Continue authorized work without waiting for another prompt; if actually blocked,
  give the concrete requested action under that banner.
- All M0 prototype evidence is assembled. Final milestone approval and PR #2
  merge remain the user's decision. M1 persistence/recovery, supported-OS emulator
  tests, long-duration/audio reliability, YAML hardening and quantified performance
  acceptance remain carried work. Existing AGP/compileSdk warning is non-failing.
- Closeout local verification: 23 Python tests and the starter validator passed
  (17 example files, 80 mapped requirements); whitespace check passed. CI reruns
  Kotlin/store, both npm suites, production build and audits on the closeout PR.
  No local Android rebuild/reinstall for this evidence/documentation-only change.
