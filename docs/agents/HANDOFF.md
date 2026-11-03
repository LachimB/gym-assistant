# Cross-agent handoff (maintain on every significant change)

## Current status
- Date: 2026-11-02
- Product specification: approved through Decision #97. Decisions #1–#78 remain binding unless a later decision refines one. Decisions #79–#97 add optional Training Season, Training Program, immutable Training Template revisions, and Workout Session provenance.
- M0 baseline: approved and complete. The user confirmed the final merge; PR #2 merged into `main` at `edbdec5ff6ec8fbcb993bff82c5a26b76efc9b95` on 2026-11-02. M1 started in this chat on 2026-11-02. This is a feasibility build, not a daily-use app.
- Working code: Kotlin reducer in `packages/engine`, independent TypeScript reducer in `packages/engine-ts`, localhost React simulator in `apps/web`, Compose debug app in `apps/android`, in-memory `TrainingStore` in `packages/cloud-adapters`.
- GitHub: private `LachimB/gym-assistant`. Repository-local author is `LachimB <<private-contact-redacted>>`. Do not change global Git config. GitHub writes must unset `GITHUB_TOKEN` so the keyring login stays `LachimB`. Earlier login assumptions are withdrawn; `LachimB` is the user-confirmed owner.
- Corrected two legacy identity notes that accidentally described `LachimB` as withdrawn; only the earlier `michal-buzek`/`MichalB` assumptions are withdrawn.
- Cloud resources / OAuth / hosting: approved personal project `gym-assistant-m1-synthetic-dev` created with no organization and billing confirmed unlinked. Drive API enabled; OAuth Testing, sole selected personal test user and `drive.file` declaration. Localhost web and package/signature-bound Android debug clients created; user completed exactly drive.file consent and the app-created synthetic file probe passed; no real data upload or hosting. See the experiment ledger for the one-time secret exposure and server-side-use restriction.
- Engine choice: ADR-0002. Kotlin/JS compiles and a Node require reproduced the navigation trace, but the bundle is a multi-file CommonJS factory. The React app uses the TypeScript engine. Android compiles the Kotlin common source. The plan's rule was to share Kotlin/JS only when that import is clean, so the prototype does not share it.

## M1 recovery accepted slice and sync continuation — 2026-11-02

- User approved ADR-0003 in chat; status updated to Approved. No Drive/OAuth or
  billing/resource approval is implied. Work is on `codex/m1-recovery` from freshly
  fetched merged `main` commit `c641676`; prior PR #10 is merged with all CI green.
- Implemented device-private Room/SQLite version-1 session store behind Kotlin
  `SessionStore`: immutable start snapshot/source/plan, revisioned append-only
  actions/engine events, checkpoint, singleton unfinished slot and pending outbox
  commit in one transaction. No canonical JSON schema change or migration.
- Android UI now starts a saved synthetic ungrouped session, restores its frozen
  plan, requires resume/partial-end/confirmed-discard after interruption and keeps
  finalized logs. Save errors stop timing until explicit retry. No data clearing.
- Internal unknown gap count is distinct from measured monotonic gaps and from
  scheduled/observed/pause counters. A live-process sample gap above 2 s is
  conservatively unobserved. Restart gaps are not assigned fabricated durations.
- Room 2.8.5/KSP2 builds with the existing Kotlin toolchain. App and test APKs build;
  existing Kotlin engine/store tests and 55 Python tests pass. Android 12 ARM64
  instrumentation passed all 10 real-database tests in 0.944 s. The final UI host
  force-stop/relaunch test passed (observed 1061 ms restored, then 2177 ms after
  explicit resume; frozen source unchanged, no automatic resume or data clearing).
  Physical-phone short smoke passed (details below); do not claim full M1 acceptance.
- New instrumentation tests use disposable databases only. New CI workflow covers
  API 31–36 and 37.0 plus an emulator-only host force-stop/relaunch test. Official
  Android 12 emulator runs locally; the stalled Android 17 download was stopped.
  CI provides the Android 17 image. Pixel 10a installation and pending physical
  evidence are recorded below. Recovery actions were moved above the fold and
  source IDs behind Source details after visual inspection.
- PR #11 merged on 2026-11-02 as `b32b74e` after all seven emulator jobs, contract/engine/web CI and the Pixel 10a smoke passed. Continuing M1 on `codex/m1-sync` from freshly fetched `origin/main`; prior branch/worktrees retained. https://github.com/LachimB/gym-assistant/pull/11. Full emulator
  matrix passed on `1181a77`: API 31–36 and 37.0, 10 real SQLite tests per version
  plus actual force-stop/relaunch/explicit-resume/partial-finalization host tests.
  Run `41424079753`; standard contract/engine/web checks also passed. Earlier
  runner disk exhaustion, system-UI ANRs and host-test checkpoint/UI races were
  fixed without weakening the production interruption policy. The harness now also rechecks after its third system-ANR dismissal instead of failing without a final observation; local Android 12 process test passed (562→573 ms). Later heads through `02854cb` passed six versions; API 37.0 passed SQLite but
  stopped on repeated system-UI ANRs. After the final-dismissal check fix,
  `796cff2` passed the complete seven-version matrix again (run `41450398107`).
- Implementation head `f002438` passed all seven recovery jobs (`41451951198`); final evidence head `599720f` passed them again (`41454629667`), plus contract/engine/web checks. Physical Pixel 10a / Android 17
  smoke also passed on the same checksum-verified APK: first recovery 4686/4686 ms
  scheduled/observed and hold 0:08; explicit resume to 8237/8237; paused Forward to
  12000/8237 and Back to 4000/8237, completed count stays one. Second recovery
  preserved 4000/8237 and stayed frozen. Confirmed partial finalization showed
  Workout saved; force-stop/reopen showed Start Workout. No data clear/uninstall,
  phone instrumentation or phone database export. See `m1-device-recovery.json`.
  The temporary phone verifier's scheduled-position navigation assertion was
  corrected against the accepted engine; no production change or weakened rule.
- Prepared local `tools/drive_scope_experiment.html` and a memory-token Drive
  transport with a persistent 500-request cap, fixed API origin and no redirects.
  Four safety tests pass and are added to CI, including the native-fetch receiver
  regression found during the first browser request. User completed Google consent.
  The app-created synthetic folder/JSON probe passed; counter 12/500 including the
  failed first transport and three metadata requests. No ETag exposed to this
  browser, so no conditional write attempted and concurrency feasibility stays open.
  The first probe covers app-created synthetic folder/JSON, stable-ID duplicate
  create rejection, exact read/list and rename; all other scope/sync cases remain open. The user proposed a real-workout folder; connector metadata returned 404 and
  the browser showed You need access for the selected personal account. Viewer
  access/corrected link requested; no contents read or access-request message sent.
  Per-operation AI document consent/provider selection is still required.
- See `docs/milestones/m1-recovery.md` for storage format, tests and limits. Remaining
  gates: canonical JSON projection/sync and real
  selected workout; Drive OAuth experiment (now approved, setup in progress). A concrete synthetic-only proposal is in
  `docs/operations/m1-drive-scope-experiment.md`; user approved on 2026-11-02; personal account selected and signed in; dedicated billing-disabled project/Drive API and OAuth testing configured. Per-file consent and the app-created synthetic probe are verified; no real data uploaded.

## Initial M1 foundation — 2026-11-02

- Working branch `codex/m1-foundation`, based on accepted M0 commit `936fcae`.
  This chat initially used stale starter commit `2d0b575`; the resulting claims
  that M0/ADR-0002/apps were missing were incorrect. The accepted M0 baseline and
  device evidence below remain authoritative. No new engine spike is needed.
- Local Python contract experiments: `tools/template_loader.py` (bounded safe YAML
  and reference validation), `tools/template_review.py` (read-only expected-revision,
  diff and identity review), `tools/storage_contracts.py` (volatile storage fake).
  These do not replace the accepted Kotlin/TypeScript engines or Kotlin store.
  No durable quarantine, publication, outbox, recovery or Drive integration yet.
- Latest user correction: personal owner and repository-local author name `LachimB`,
  existing personal email `<private-contact-redacted>`. Updated docs, env example and
  safeguards; global Git config unchanged. Global collaboration instructions also
  record the confirmed account and distinct completion/input-needed banners.
- Stored GitHub login verified as `LachimB` and full read-only preflight passed for
  existing private origin with the four token overrides omitted for that command.
  The inherited token remains untouched. Never print it or use it implicitly.
- Local M1 tests: 55 passing tests cover loader, review, mock storage and credential safeguards. Existing
  Kotlin/web/Android checks must be rerun on this accepted baseline before PR.
- All local work was preserved in a stash before baseline synchronization; recovery
  stash retained. Initial work committed as `10196af`, pushed through verified
  personal preflight and opened as PR #10: https://github.com/LachimB/gym-assistant/pull/10.
  CI passed on commit `3aa8301`; PR #10 merged as `c641676`. No remote creation, public deployment, new service or billing
  resource activation occurred in this chat.
- Next M1 decision: `docs/adr/0003-m1-durable-session-store.md` proposes a Kotlin session-store port and device-private Room/SQLite transactions for events/checkpoint/outbox. Approved by the user on 2026-11-02; implementation is tracked above. Then integrate template validation and durable checkpoint/recovery. Real workout selection and Drive OAuth approval
  remain open; use synthetic fixtures meanwhile. No schema migration authorized.

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
| m0-08-close | approved and complete | User confirmed final PR #2 merge on 2026-11-02. Untested and unsupported capabilities remain later gates. |

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
1. M0 is approved and complete: PR #9 closeout and PR #2 are merged. No further M0 approval is pending. Earlier dated sections below are historical.
2. Agree on the first bounded M1 task before implementation. Recommended preparation: document the local durable checkpoint/session-start contract and recovery acceptance tests, considering the existing Season/Program decisions. Persistence architecture/schema decisions still require approval; no cloud setup is authorized by M0 closure.
3. M1 must preserve completed/partial records and four separate time categories, recover without fabricated progress, and add the required Android emulator coverage. Long-duration/audio reliability and other documented limitations remain unproven.
4. Continue the approved automatic debug-APK installation workflow after local builds; do not uninstall or clear data.
5. Keep GitHub writes on personal `LachimB` credentials with preflight. No new service or billable resource without separate approval.
6. The acceptance-status documentation is recorded on local branch `codex/m0-accepted`, based on merged `main`. Include this documentation commit in the next approved PR; do not create another standalone M0 closeout PR merely for the approval record.

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

## Final M0 approval — 2026-11-02

- User merged PR #9, then explicitly confirmed completion of the requested final
  M0 approval/PR #2 merge step. GitHub confirms PR #2 merged into `main` at
  `edbdec5ff6ec8fbcb993bff82c5a26b76efc9b95` on 2026-11-02.
- PR #2's accepted head `a514ec1` passed contracts, engine/store and web CI,
  including the production build and dependency audits. Run: `41339132829`.
- M0 is complete at the documented feasibility scope. Known limitations and later
  milestone requirements remain binding. No M1 implementation or cloud setup
  was started or authorized by this approval.
- Carry this local acceptance-status update into the next approved PR rather than
  creating another approval-only closeout loop.


## M1 sync feasibility continuation — 2026-11-02

- On `codex/m1-sync` from merged `b32b74e`; main/recovery worktrees retained.
- Connector profile matched the user-selected personal Google account; created
  and metadata-verified only a private external synthetic JSON child inside the
  already labelled test folder. Four setup calls conservatively charged to budget.
- App's own drive.file grant: parent HTTP 200, externally created child HTTP 404.
  No inherited child grant and no content read. Counter now 18/500. Six Node
  safety tests pass; direct request cap cannot be reset by bookkeeping operations.
- User approved Picker terms/enablement and then restricted browser-key creation.
  API Enabled verified. Created key restricted to Picker API only and two
  localhost origins plus docs.google.com; verified persisted restrictions. First
  Picker attempt failed invalid-key; iframe AX output exposed transient token/key.
  User informed, page reloaded, later AX output filters credential-bearing URLs.
  Reload is not revocation. Never copy values into Git. After changing document
  referrer policy to strict-origin-when-cross-origin (direct Drive still
  no-referrer), explicit exact synthetic selection passed; key propagation may
  also have mattered. App grant read parent/child/content 200/200/200 vs child404
  before selection. A further seven requests moved the child into a labelled test
  subfolder and back, preserving file ID/grant; every response200. No deletion or
  sharing. Counter now30/500 plus two Picker UI attempts (internal count not
  measured). Key remains restricted; no service account/billing or broader scope.
- PR #12 merged as d858724 after final head dfeb290 passed standard CI and all
  seven Android recovery jobs (41536199398 / 41536199693).
  https://github.com/LachimB/gym-assistant/pull/12. No Android app/schema/DB change.
  Continued on codex/m1-session-projection from freshly fetched origin/main;
  prior branches/worktrees and local proposal preserved.
- The denied real-workout folder was superseded by an accessible individual PDF.
  The user explicitly selected OpenAI in this chat for that PDF's structure
  extraction. Downloaded privately outside Git; full single-page text and rendered
  table inspected. No real content/identifiers committed or used in the synthetic
  experiment. Owner clarified variant structure, side completion before switching,
  full-set alternation, paired-stage mapping and a missing rest. Private extraction
  notes updated outside Git. Owner chose a 4-second reset and upper bounds for
  fixed initial targets, then explicitly approved the private template preview.
  Draft YAML passes the existing schema/reference validator; not yet imported
  or executable on Android. Original/extracted content and draft remain outside
  Git and the synthetic Drive experiment. Owner wants
  fixed Template targets plus one-click repetition addition and skip during a
  Workout Session, preserving these as session deviations rather than changing
  the immutable source Template.
- Existing compiler only accepts fixed timed phases; it does not yet provide
  runtime ranges/manual completion, explicit side cues or weighted-resistance
  display. Canonical projection must retain scheduled/observed/pause/known and
  unknown gaps separately; current draft session timing fields alone cannot do
  this. Review a concrete versioned contract before changing schema or local DB.
  M1 projection/Android sync/web history and real-workout acceptance remain open.

## M1 projection review gate — 2026-11-02

- ADR-0004 is a local proposed document. Owner clarified they want to review it
  before deciding; no design changes specified and no approval granted. No export schema implementation,
  data migration or real-workout cloud upload is authorized by this exchange.
- Review identified duplication between semantic events and per-tick receipts,
  growing upload size, internal-format coupling and unspecified event/step mapping.
  The proposal now records these unresolved concerns and a plain-language example.
  Do not choose a replacement design on the owner’s behalf.

## Human session history versus execution internals — 2026-11-03

- Owner explicitly wants meaningful events for human use; quarter-second tick
  saves may exist for implementation needs but must stay separate from human
  session recordings. ADR-0004 revised accordingly: no raw tick/receipt log or
  mandatory linked internal audit file in ordinary canonical session history.
- Meaningful events and domain timing/outcome summaries preserve pauses, known
  and unknown gaps, skips and partial completion without exposing internal rows.
  Internal records remain available for recovery/integrity; no deletion/migration
  or technical-log cloud upload introduced. This is data separation, not only UI.
- The owner confirmed this direction, not the entire new session schema. ADR
  remains proposed; no schema implementation or database change made. Exact
  contract fields/event mapping remain for review. Private workout preview stays
  approved; Android execution/import and real-history cloud upload remain open.

## ADR-0004 approved and meaningful-history projection — 2026-11-03

- Owner replied “approved” to the revised human/internal separation ADR. ADR-0004
  status updated. New session 1.1.0 export/schema added; 1.0.0 unchanged. This
  supersedes earlier unapproved-status notes, not the remaining service/data gates.
- `SessionStore.history(id)` reads a consistent Room transaction and validates
  frozen source/hash plus the complete committed prefix before projecting domain
  events/timing/outcome. No tick log, raw receipts, operation IDs or checkpoint in
  human JSON. No DB migration, outbox acknowledgement, file upload or new UI flow.
- Explicit partial/planned-end/discard/awaiting-ack outcomes; session-wide events
  have no invented step. Unknown repeat restarts remain visible even when the
  reducer emits no event. Source plan hash is verifiable from retained UTF-8 bytes.
- Nine JVM tests and seven independent Python history-contract tests; full Python
  suite62, validator19 fixtures/80requirements pass. All12 real SQLite tests on
  local Android12 pass. Emulator process recovery passed1→97ms, no automatic resume
  or clear; not drift evidence. Existing engine/store tests and APK builds pass.
- Phone installation skipped: no physical device connected. No new physical
  history-export acceptance. Private workout preview remains outside Git and not
  imported. See `docs/milestones/m1-session-history.md` for contract/mapping/limits.
- Remote CI/PR pending for this implementation. Remaining M1 work includes private
  template import/side/weight cues and runtime repetition deviations, durable
  validated library publication, Android auth/sync/conflict handling and web history.
  No real-workout cloud upload, token storage or DB migration is authorized by ADR4.

## History merged; Android synthetic connection stage — 2026-11-03

- PR13 merged as4646d53 after final54498a5 passed standard CI41581755092 and all
  seven Android recovery jobs41581755077. ADR4 human-history projection complete
  at its read-only scope. Continued from fresh origin/main on
  codex/m1-android-drive-probe, preserving prior branches and local follow-up work.
- Prepared the native portion of the already-approved synthetic Drive/OAuth
  experiment: Google AuthorizationClient22.0.0, explicit personal-account-pinned
  drive.file request with prior scopes excluded, strict returned scopes and
  about.get identity check before exact synthetic folder/child metadata reads.
  No app token persistence, auth codes, backend, new Cloud resource or real data.
  Google Play services may maintain its own account/token cache; not app storage.
- Local APK configured by private build property with the already-selected
  personal email. That identifier is embedded in the private test APK, not Git;
  CI/default account is blank and test button disabled. SDK error bodies/logs and
  credentials are not emitted. Internet permission added; no account-list/contacts
  permission, no Room migration or production-sync claim.
- One-time native20 request reservation succeeded via localhost UI: budget50/500
  conservatively allocated (prior30 + reserved20), not50 observed requests. Phone
  counter persists before requests, caps20, authorization attempts6; no resets.
- Eight native fake-transport tests + nine history JVM tests and12 real SQLite
  tests pass locally; no Google requests made by tests. Native Google consent and
  ETag availability need the authorized physical phone. No phone is currently
  connected, so install/auth test pending. Steps: m1-android-drive-probe.md.

- User connected/unlocked the Pixel10a Android17; verified local APK SHA256
  f38797518d4434df63163c0785ecff2d8513e712d16100a08deed94bdb5b998f and
  certificate SHA1 matches the registered Android client. install-r succeeded and
  app opened. No clear/uninstall/instrumentation/phone DB export. Owner asked to
  tap Test personal Drive connection, verify the account/specific-file consent,
  and report final status. Native result pending; no credential contents requested.
- Native APK also passed emulator process recovery (377→394ms; not drift evidence),
  no automatic resume or app-data clear. Prepared UI inspected on emulator.

- PR14 draft: https://github.com/LachimB/gym-assistant/pull/14. Owner saw recovered
  unfinished workout rather than start screen. Fixed test accessibility by adding
  an independent Drive dialog from recovery; no resume/end/discard required.
  Emulator regression now checks unchanged complete checkpoint/frozen source while
  opening/closing it; passed1→111ms after explicit resume only. Version4 APK SHA256
  9f222946f0d0d7392e7fea864e78e098285ecfc5b86fc3c9b82a888c2e7cbd13 installed-r
  on confirmed phone, app opened, unfinished workout retained. Google test result
  pending owner action; do not infer a successful grant/ETag from installation.


## Native personal Drive connection passed — 2026-11-03

- Owner reported final native success: personal account verified, synthetic parent
  and selected child metadata accessible, no ETag received, no workout uploaded.
  No writes attempted; conditional updates remain unproven (not proof that every
  Drive endpoint/client lacks ETags). No further connection-case repeats needed.
- Independently read only two non-secret probe counters: four authorization
  attempts and12 direct requests. Prior30 + native12 =42 counted direct/setup
  attempts, with8 unused native requests still reserved inside50/500. No resets.
  SDK internal traffic separately unmeasured. No phone session DB/history export.
- Evidence: docs/milestones/m1-native-drive-probe.json; installed source227c17d,
  APK version4 / SHA9f222946…e7cbd13. Owner's unfinished workout remained in
  recovery; test required no resume/end/discard. Phone counter equality unmeasured;
  emulator regression checks complete checkpoint/source equality across dialog.
- PR14 final evidence/CI pending; code tested on phone does not provide durable
  synchronization. Read-only web history work is independently saved on branch
  codex/m1-web-history at d47e917 (19 web tests/build/audit pass; browser QA pending).

## Local web history reader — 2026-11-03

- Native probe PR14 merged after current-head contracts/web/engine and all seven
  Android recovery jobs passed. The API37 emulator archive download failed before
  tests on the first attempt; rerunning that failed job passed without code changes.
  Personal phone connection success remains recorded in m1-native-drive-probe.json;
  no ETag returned, conditional writes still unproven, no workout uploaded.
- Web reads local canonical 1.0.0/1.1.0 files with schema/semantic validation and
  exact frozen-plan hash checks for 1.1.0. Shows meaningful events, separate timing,
  captured labels/context, recorded results/effort/quality, partial outcomes and
  unknown interruptions. No raw ticks, invented per-set results or current-context
  inference. Synthetic examples explicitly labelled. No Drive connection here yet.
- 20 web tests, production build and dependency audit pass. Actual browser checked
  partial example and a completed synthetic file selected through the file chooser.
  See docs/milestones/m1-web-history.md. No private workout data committed.
- Remaining M1: Drive conditional-write feasibility, durable auth/outbox/conflicts,
  private template import/runtime side/weight/add-repetition support and real-workout
  acceptance. ADR4 does not authorize DB migrations or real-history cloud upload.

## Synthetic Drive conditional content writes — 2026-11-03

- Seven bounded browser requests with the existing approved drive.file grant
  verified personal identity/labelled parent/exact synthetic child/content, then
  tested Drive v2 media PUT: current strong tag200/new tag, stale tag412, final
  read200 with first update preserved. Earlier v3 no-ETag observations unchanged.
  See docs/milestones/m1-drive-conditional-write.json. No real workout uploaded.
- Global allocation57/500 includes unused native8;49 counted setup/direct attempts
  include consumed native12. SDK/Picker traffic remains unmeasured. No budget reset,
  new service/scope/key, sharing or deletion. Page credentials forgotten afterward.
- tools/drive_conditional_probe.mjs has fixed synthetic targets and fail-closed
  account/metadata/content/tag checks. Fourteen Node tests pass, included in CI.
  First browser attempt rejected cached old transport locally before any request;
  versioned import corrected it, budget unchanged until successful seven calls.
- Conditional-write feasibility is now demonstrated for this exact v2 content
  path, not v3 compatibility or production conflict/outbox/fencing implementation.
  M1 remains open; new persistence/security boundaries require reviewed approval.

## Next sync decision prepared — 2026-11-03

- Proposed ADR-0005 makes a first finalized-session delivery slice reviewable:
  immutable account/root binding, exact-byte durable intent, stable provider ID
  before create, lost-ack verification, preserved conflicts and no tick upload.
  New connection/delivery tables require additive Room1→2 migration. Owner review
  requested; **not approved or implemented**. No phone migration/backup performed.
- Proposed authorization includes migration implementation/tests and phone
  application only after a verified private backup. Cloud tests remain synthetic;
  no general real-history upload, stored OAuth token or background worker.
  Active-session fencing/library sync/real-workout execution remain open requirements.
- Web history PR15 and conditional-write PR16 are separate tested slices. Do not
  infer their merge/CI state from this note; inspect current GitHub checks.

## ADR-0005 approved; delivery implementation in progress — 2026-11-03

- Owner requested a plain-language explanation, then explicitly chose “Approve
  ADR-0005”. The approved scope includes account-bound finalized synthetic delivery,
  additive Room1→2 migration/tests and phone application only after verified private
  backup. Real-workout upload remains excluded. ADR status updated locally.
- Current branch codex/m1-account-bound-delivery includes proposed-doc branch and
  latest PR16 merge of web history. PR15 merged1d8249a after all checks; PR16 current
  head433a47a has web/engine/contracts passing, final Android jobs still pending at
  last check. Inspect live GitHub status before merging; no failing-CI merge.
- In-progress/uncommitted: additive connection/delivery tables and explicit migration;
  immutable delivery store with deduplication and outbox acknowledgement transaction;
  fail-closed synthetic sender with durable provider ID, exact read-back, bounded
  transient retries/cancellation; fixed-host Google transport, no token persistence.
  Existing main DB builder now registers migration. App build version5.
- JVM tests including10 sender and7 HTTP-domain tests pass (34 total including17
  prior). Migration instrumentation first run:14 pass,1 incorrect test expectation
  (two actions expected as three); assertion corrected. Rerun15 currently running
  on explicit emulator-5560. Fresh APK built. Physical install deliberately held
  under the latest ADR5 tests+backup gate; no phone DB migration/backup yet.
- Sender defaults to refusing all payloads unless exact synthetic eligibility is
  supplied. No delivery UI/fixture factory wired yet; no actual delivery calls or
  extra budget reservation. Prior global allocation57/500 remains unchanged.
- Remaining implementation: exact generated synthetic fixture admission/predicate;
  Compose preview/explicit consent-purpose auth and delivery status/retry; reserve
  additional20 before any live tests; emulator crash/SQLite acceptance; verified
  private phone backup before install/migration; phone synthetic live evidence; docs
  and PR CI. Do not upload current phone history or touch unfinished slot for test.

## Delivery local tests and preview wired — 2026-11-03

- 36 JVM and16 actual SQLite emulator tests pass. Source-pinned generated sample
  can be persisted/retried without changing the existing unfinished workout. Live
  sender rejects all payloads unless exact generated canonical eligibility matches.
  Preview/explicit send uses independent purpose routing through the existing
  authorization client; status is recovered locally after restart, no auto-send.
- Local process-recovery test passed541→948ms only after explicit resume; no clear or
  auto-resume. Python67, validator19fixtures/80requirements, hygiene pass. No phone
  build installed/migration run yet. Default budget flagfalse blocks sending.
- Private backup helper/tests added; not run on phone. It refuses running work,
  verifies source consistency/receipt prefix, retains raw bundle and standalone
  verified copy outside active app, prints only counts/checksums/status. No contents
  sent to AI/cloud. Actual backup path must be persistent/private, not just tmp.
- Next: final emulator preview/crash checks, implementation PR/current-head CI,
  separate20-request global reservation, private configured APK build, verified
  phone backup then install-r/migration. User must complete phone synthetic send/
  Google consent if prompted. No real workout upload. See operations/m1-session-delivery.md.
- PR16's current-head API37 job failed only on truncated uiautomator XML after
  SQLite12passed; one failed-job rerun is running. This implementation adds a bounded
  observation-only re-dump with2 Python tests; do not weaken actual app assertions.

## CI integration checkpoint — 2026-11-03

- PR15 merged1d8249a; PR16 merged9307121 after current-head standard CI and all
  seven Android recovery checks passed. The one API37 truncated-XML observation
  rerun passed; bounded observation retry is included in this new delivery slice.
- Current implementation local:36 JVM and16 SQLite tests pass, including local
  synthetic status after reopen.67 Python tests pass. Default build's delivery
  budget flag remainsfalse. No phone backup/install or new cloud API call yet.
- Added a distinct one-time localhost20-request delivery reservation button. This
  has not been pressed at this checkpoint; global allocation still57/500. Only
  enable private build flag after verifying reservation; keep old native counters.


## Delivery build ready for CI/phone backup — 2026-11-03

- PR17 draft opened with implementation. Current code has36 JVM/16 SQLite tests;
  new emulator script also opens/closes synthetic preview and verifies exact saved
  source/checkpoint unchanged. Private configured v5 APK built with reserved flag;
  SHA2560d2881e4f151db81e3a5ea193980469df28cbe25458bdfe5a6fd89b64fde3344,
  signingSHA1CE:DE:EC:BE:F3:A8:92:F2:45:D2:83:EC:48:31:A0:07:57:79:12:BD.
- The20-request delivery reservation was pressed once:77/500 allocated.49 counted
  setup/direct attempts remain; unused native8 + delivery20. No delivery API request
  yet. Private build has account pin and syntheticDeliveryBudgetReserved=true; CI
  defaultfalse. No phone install/migration or backup yet; do not silently apply it.
- Emulator recovery/preview check passed447→530ms, no auto-resume/clear and no change
  from preview. Backup rehearsal then exposed copied hot-journal recovery: corrected
  validation to recover only a disposable working copy. Rehearsal now passes with
  raw retained bundle untouched.4 backup unit tests, full Python now68 expected.
  Emulator backup at /private/tmp/gym-m1-delivery-emulator-backup-v4 (public synthetic).
- Next: push new helper/preview checks, await current-head seven-version CI; verify
  one authorized physical phone and make persistent private version1 backup using
  tools/backup_android_database.py (no content output). Only then install verified
  v5 APK via install-r and launch for preserved paused recovery/migration. Send UI
  steps to owner for synthetic delivery/Google confirmation, not real workout.


## Latest delivery artifact and external action — 2026-11-03

- Additional retry contract checks pass: numeric/HTTP-date Retry-After bounded to30s,
  budget failure before transmission, and explicit retry after a wall-clock reset.
  Latest JVM39 pass; latest private v5 APK supersedes0d288 checksum:
  87808112806031903050af5a20094eb06489c4d4f9a2d5845e92ca3b9d297368.
  Signer unchanged. No phone installation/migration/backup performed.
- ADB physical count0. Owner was asked asynchronously to reconnect unlocked/idle;
  answer still pending at this checkpoint. This is a real external action, not a
  new approval request (ADR5 already approved). Finish CI/independent work first.
- Updated prototype has no delivery-stage network call yet; global allocation77
  contains unused old native8 and unused new delivery20. Do not reset counters.

## Current external gate — 2026-11-03 (local acceptance note)

- Implementation commit e9b3b9413faf6a58c6bd079a783ce0d3750e37e5 has all standard
  CI and all seven Android recovery jobs passing: runs41621862466/41621862178.
  PR17 stays draft pending physical backup/migration/synthetic delivery acceptance.
- Owner confirmed phone connected/unlocked/idle, but ADB still listed zero physical
  devices. macOS USB inventory exposed no Pixel/Android name. One local ADB server
  restart did not change that; no app data/state touched. Latest recheck remains0.
- Asked owner to reconnect using a data cable, keep unlocked and accept debugging
  if shown. That follow-up answer is pending. No phone backup, APK install or DB
  migration has run. No real workout uploaded. Next action is actual USB detection,
  then approved verified private version1 backup before install-r of the verified
  v5 artifact (SHA87808112806031903050af5a20094eb06489c4d4f9a2d5845e92ca3b9d297368).
- At that checkpoint this note was left uncommitted to batch device evidence.
  Subsequent physical acceptance below supersedes that waiting state.


## Physical migration verified — 2026-11-03

- Owner reconnected with a data cable/debugging accepted; exactly one authorized
  Pixel10a detected. Installedv4 and expected privatev5 APK hash verified. Fetch
  origin/current PR17 still e9b3b94; all standard/seven Android CI checks pass.
- Approved private version1 backup completed and verified before installation:
  <private-path>
  StandaloneSHA1ea77e88e58e67f3653813b3e11aeb76d6779ff93a0a5ae3bbc51676ec9a0e9d.
  Integrity/FKs/receipt-prefix/paused checks pass. Raw/standalone files retained
  privately; never committed or printed. No restore/clear/uninstall.
- Verified private v5 installed via install-r and launched. Local private copy
  comparison confirms schema2, all702 prior action rows/outbox rows and every source
  snapshot preserved; unfinished slot and scheduled/observed/pause/completed phases
  unchanged. Only normal recovery action added; restored paused, new delivery and
  connection tables empty at verification. See m1-phone-delivery-migration.json.
- Owner now asked to open Drive connection test→Preview synthetic session delivery
  →Send or retry synthetic example and report final status. This is phone action,
  not a new approval gate. Account/app/specific-file checks supplied. Do not send
  current workout. Global allocation77/500; delivery20 reserved separately.
- Phone migration acceptance is complete; live synthetic delivery/retry evidence
  still pending. No real-workout cloud upload authorized. Keep PR17 draft until the
  remaining physical test is verified; batch evidence/docs commit after result.


## Native synthetic delivery and retry passed — 2026-11-03

- Owner reported exact delivery/read-back success and same success after explicit
  retry. Read only whitelisted counter values: new delivery auth attempts2/direct
  HTTP11. One durable delivered job/connection; sender-domain attempts6 (inspect
  may issue two HTTP calls). Synthetic outbox3 delivered; all original sources,
  action/outbox rows and paused progress unchanged. No real workout uploaded.
- Synthetic provider ID1PpkSmoA4pjgM8mFvhzDLc8ffVV68KLnV; exact canonical SHA
  07c722af74d1ff2939ba65b2711e39c8ca1e4e0e8ebb29e29cd2dd4a46098b3b.
  Receipt captured only after retry; do not claim independent before/after ID
  sampling. Browser same-grant exact-file/list/hash verification being prepared.
- Added bounded readback tool for only this synthetic file. Four Node tests rebuild
  the exact expected public sample from the checked-in Kotlin golden and verify
  hash equality; no phone JSON fixture or personal content added to Git.


## Synthetic phone→Drive→verified download→web passed — 2026-11-03

- Existing browser grant verified the exact native file/label/account/hash and
  one visible matching filename with five200 responses. Canonical13706-byte
  download SHA matches phone07c722...98b3b. It opened through actual local web file
  chooser:21 meaningful events,50s observed/planned, zero pauses/gaps, planned end.
  Screenshot /private/tmp/gym-m1-phone-to-web-history.png. Browser credentials
  forgotten. Direct product Drive browsing/retrieval is still unimplemented.
- Global allocation82/500 =65 counted direct/setup +17 unused (native8/delivery9);
  authorization/Picker internals unmeasured. New bounded fixed-artifact readback
  harness and4 tests added (Node18 total); fake expected bytes reconstructed from
  public Kotlin golden, no phone-data fixture. See native-session-delivery.json.
- Phone backup/migration, initial synthetic delivery and explicit retry acceptance
  now pass. All real prior sources/rows/progress unchanged, no real upload. PR17
  should become ready/merge only after current combined head CI passes. Remaining
  M1: validated private template library/import, side/weight cues and one-click
  repetition changes, complete product template/history sync and real-workout gate.

## Real-template/runtime proposal prepared — 2026-11-03

- ADR-0006 proposed, not implemented: reviewed local Template publication, explicit
  side/weight cues, one-click extras appended to current set before following
  hold/rest/side. Proposed Template1.1/plan2/internal format2/history1.2 and additive
  Room3 library/effective-plan records; legacy execution/history stays readable.
  Existing unfinished workout not converted/ended/discarded. No real cloud upload.
- Owner review/approval requested asynchronously under rule4. No response yet at
  this checkpoint. Do not implement these new formats/migration before approval.
- Proposal is on codex/m1-real-template-runtime-proposal stacked on PR17 head16444f0.
  PR17 physical migration/delivery/retry/browser-download-web acceptance passed;
  current combined head16444f0 passed standard CI and all seven Android jobs.
  PR17 merged503a0b5 on2026-11-03; this proposal branch is brought onto that base.


## Current review gate — ADR-0006

- PR17 merged503a0b5 after39 JVM,16 SQLite local tests and successful current-head
  CI:41627572090 standard /41627572133 seven Android versions. Native physical
  backup/migration/delivery/retry and same-grant browser download/local-web review
  evidence retained. No real workout uploaded; original work remains paused.
- ADR-0006 review answer remains pending. It is proposed only; no Template1.1,
  engine/plan2, saved format2, canonical1.2 or Room3 implementation/migration yet.
  Do not infer approval from ADR5 approval or phone test success. Existing latest
  private v5 APK/Room2 stays installed; private backup remains persistent.
- Future author must fetch GitHub/check other worktrees and preserve this proposal.
  If approved, implement contracts consistently with its explicit append-to-set
  behavior, legacy coexistence, source immutability and backup gates. Private
  approved workout preview stays outside Git; no new AI/private-cloud consent.

## ADR-0006 approved; runtime core implementation started — 2026-11-03

- After the plain-language/local-vs-cloud explanations the owner replied “approved”.
  ADR6 status is approved. This supersedes the pending-review gate above. Real
  workout cloud upload remains excluded. Current branch codex/m1-template-runtime-core
  starts from the preserved proposal plus latest main503a0b5; worktrees/remotes/fetch
  verified before implementation. No other worktree changed.
- Added separate Kotlin engine2 source under packages/engine/src/jvmMain and new
  TypeScript src/runtime2.ts. Engine1 untouched. Android includes this JVM source
  alongside commonMain. Existing SnakeYAML-KMP3.1.1 dependency (already transitive
  via Kaml0.73) explicitly pinned for bounded type-preserving safe parsing. No new
  service. TS uses existing YAML plus Ajv8.20.0; npm audit0 vulnerabilities.
- Template1.1 schema adds explicit phase/rest roles, resetForStepId and optional
  inSessionRepetition mode appendToCurrentSet/resetMs. Old1.0 unchanged. New80s public
  fixture timed-runtime-v2.yaml: left2 timed reps/reset4, separate longer hold10,
  side transition4, right2/reset4 and finalrest30. Addition before hold uses existing
  reset + extra work/reset; addition before long rest inserts reset/work. Neither
  duplicates a hold/side/rest or changes active countdown. Source defaults immutable.
- Core preserves block/occurrence/set/source-step, side, names, structured resistance
  and stable SHA-derived phase/unit IDs. Stable ordinal is NOT array position and
  remains unchanged on insertion; intended checkpoint compression uses ranges over
  these immutable ordinals to avoid huge per-tick completion-ID arrays.
- Eight Kotlin runtime tests and eight TS tests pass; TS full suite33. Full frozen
  Kotlin plan in examples/expected/timed-runtime-v2.plan.json matches independently
  compiled TS object. Source compiler checks explicit contiguous eligibility,
  reset links, duplicate coverage, unsafe YAML, types, safe counters and ordering.
  Normal timing delegates to existing deterministic mechanics but translates back
  to stable phase identities; add returns a new effective plan. Repeated domain
  adjustment ID is idempotent; conflicting reuse rejects.
- Latest uncommitted additions bound compiled size conservatively (8MB estimate)
  before serialization; tests need rerun after this small guard change. Template
  root name capped4096bytes; no truncation. Python validator now registers20 fixtures
  and dispatches Template1.1, but full Python check not yet run. Plan2 JSON schema/
  canonical1.2 schema still to implement. ImportContracts2 loads trusted checked-in
  schemas; Kotlin assertion subset covers only source template/exercise keywords.
- No Room3/table/library/checkpoint2/session2/history1.2/UI implementation yet. No
  debug APK assembled or installed since ADR6 approval; phone remains v5/Room2 with
  original unfinished workout paused. Need preserved legacy dispatch, four additive
  tables, staged private file importer+review/CAS publication, new session/driver,
  compact checkpoints, meaningful canonical1.2 reader/web display, emulator matrix,
  new private Room2/files backup BEFORE physical upgrade and owner local-template
  acceptance. Keep exact synthetic-only cloud sender eligibility unchanged.
- Private approved preview stays /private/tmp/gym-m1-source/preview outside Git.
  Do not read its contents into AI for another operation or upload it. Any source
  conversion can run locally and emit only validation/count/duration metadata;
  owner must see publication preview. Never infer reset semantics in the compiler
  from display names/durations; source1.1 explicitly annotates them.


## ADR-0006 implementation status — 2026-11-03 (supersedes draft notes above)

- Owner approval recorded; core compiler/runtime2, Template1.1 and compiled-plan2
  schemas plus public base/trace goldens are implemented. Kotlin9 new runtime
  tests and full TypeScript34 pass. Shared trace compares complete plans/events
  across two adds, pause, skip, rewind, gap, explicit resume and completion.
- Separate additive-library/session/UI/history work is present locally and being
  validated for a following review commit/PR. Do not infer that an uncommitted
  file is already on GitHub or that the phone has been upgraded. Local evidence
  so far: Android40 JVM tests,24 real SQLite tests; Python70, web22, old actual
  process-death/recovery test. These combined-worktree checks are not core-only CI.
- Phone remains private v5/Room2. Hold physical installation until the new
  verified Room2 + app-private library-file backup and complete automated gates.
  Existing unfinished format1 workout must stay paused and never be converted.
  No real-workout cloud upload; exact pinned synthetic sender gate unchanged.


## Local library/session integration ready for CI — 2026-11-03

- Core PR18 merged bd5dc25 after all checks: standard41645751316 and Android
  seven-version41645751343. Follow-up branch codex/m1-local-template-runtime
  fast-forwarded onto that verified main, preserving all local integration work.
- Implemented Room3 explicit1→2→3/2→3 migrations, candidate quarantine/review,
  immutable private file storage and revisions, stale-head/CAS publication;
  format2 source/base/effective plans, compact stable ordinal checkpoint ranges,
  atomic append receipts/checkpoints/outbox and structural-only plan rows.
  Legacy format1 is explicitly dispatched and never converted. New restoration
  audits the committed prefix before recording unknown gaps/explicit resume.
- Added Android system-picker import/full-preview/publish/start flow and side,
  weight/band/note cues, +1 for eligible current sets, late-tap handling. Shared
  process Room instance is no longer closed by an individual ViewModel.
  New build versionCode6 /0.1.4-m1-local-template; no physical install yet.
- Canonical1.2 and schema/public fixture exclude raw ticks/internal receipt IDs;
  original-plan position is explicitly null during inserted work. TS web reader
  independently recompiles the frozen source, applies ordered semantic adds,
  verifies hashes/final effective plan/events/timing. Old1.0/1.1 remain supported.
  Compiler is loaded only when opening1.2. Web production build has no size warning.
- Local checks: Android40 JVM,24 SQLite (including rollback and legacy/new
  coexistence/driver gaps/late tap), web22, TypeScript34, Python70, contracts22
  and engine/cloud suites pass. Old actual process-stop test passed at current
  integration. Format2 UI/process acceptance and current-head CI still pending.
- Backup helper now captures/verifies library archive separately without extracting
  it or printing source names/contents;6 helper tests pass. New verified Room2/files
  backup remains mandatory before physical Room3 installation; old Room1 backup kept.
- Private local conversion prepared outside Git in /private/tmp/gym-m1-source/runtime2.
  It preserves the approved45m50 timeline,474 phases,7 exercise files;32 explicit
  repeated sets support adds, two one-off holds do not.203 inter-repetition resets
  explicitly annotated to the owner's4seconds; remaining original rests preserved.
  Independent TS and Kotlin plans match; all32 add policies checked locally.
  Host100 deterministic ticks took25ms (NOT device/background timing evidence).
  Only validation/count/duration metadata entered chat. Original preview unchanged.
  Temporary Kotlin local-check test removed; private sources/results remain outside
  Git/APK. Owner still must review/publish locally and provide physical acceptance.
- Exact pinned synthetic-only cloud sender remains unchanged. No source/session
  cloud upload, new external service or billing change occurred.


## Verified local format2 UI/process flow and backup rehearsal — 2026-11-03

- System picker on API31 selected only the two public synthetic YAML files;
  all10 phases were inspected in the review, explicitly published and started.
  While paused, +1 on the left side changed80→90seconds and retained the cursor.
  A real process stop/relaunch retained exact source/effective plan rows, the add,
  observed6745ms and paused counters; recovery did not advance without resume.
  Repeated after emulator install-r of v6. No clear/uninstall. See structured
  evidence docs/milestones/m1-local-template-runtime.json.
- Rehearsed the extended backup CLI on the stopped emulator's Room3 database:
  integrity/FKs/prefix match passed and four library files archived/hashed without
  extracting or printing contents. Bundle /private/tmp/gym-m1-pre-v6-emulator-backup.
  This is rehearsal, not the required physical pre-Room3 backup.
- Actual browser1.2 example verified: original80s vs adjusted90s, inserted-work
  base position inapplicable, meaningful addition names exercise/left/repetition3,
  two unknown gaps and distinct observed/pause values. Screenshot locally at
  /private/tmp/gym-m1-runtime-history.png; tab16 retained as a deliverable.
- Tightened1.2 gap-counter checks against semantic interruption details; a known
  gap supplied while already recovered is rejected rather than silently lost.
  Existing version1 engine/reader behavior remains unchanged.
- Physical phone is connected/authorized (exactly one); serial retained only in
  private /private/tmp/gym-m1-authorized-phone-serial.txt, never printed. Still v5/
  Room2. After current-head CI, create a fresh persistent verified backup BEFORE
  installation; never use the emulator bundle as a phone backup.


## Physical Room3 upgrade verified; publication usability follow-up — 2026-11-03

- APK source78b649ee9f00e7a6907ffa14942b7bac8b8004e5 passed all10 checks:
  standard41664633328 / seven Android41664633400. Private v6 APK SHA256
  17e5d587a8133c741dbb8847717a4f6347e0d767e9c870e3371433d87dddfca3,
  signerSHA1 cedeecbef3a892f245d283ec4831a007577912bd installed with install-r.
- Initial backup attempt stopped BEFORE installation because exec-out failed to
  propagate a missing optional path's status, yielding error text as an archive.
  No clear/restore/migration on that attempt. Fixed binary reads to shell -T,
  whose remote exit status distinguishes absent sidecars/library. A CLI regression
  simulates this failure; backup-helper7/full Python71 pass. Real emulator binary
  comparison proves exact bytes. Failed raw bundle retained, never auto-restored.
- Fresh verified Room2 backup <private-path>
  standaloneSHA2568f721c29712de5ac08dcda1a62ff21b4c66d35272053ffb1e488d58daf825319.
  Integrity/FKs/prefix checks pass, library correctly absent/0files. Prior Room1
  backup retained. Tool-only transport fix was locally tested; APK source remained
  byte-for-byte the CI-tested78b649e before this migration.
- Pixel10a upgrade2→3 passed exact private before/after comparison: all3 source/
  checkpoint rows,706 prior actions/outbox, unfinished slot, one Drive binding and
  one delivered job preserved. Only one legitimate legacy recovery receipt added;
  observed/scheduled/pause/completed progress unchanged. All four new tables empty.
  Post-check bundle <private-path>
  No original source/receipt contents printed, no workout delivery created.
- Owner then confirmed saving the old workout as a partial record. Do not discard
  or rewrite it. Eight reviewed YAML definitions transferred over personal USB to
  phone Documents/GymAssistant-LOCAL-M1/Reviewed-workout and read back exactly;
  no Drive/AI upload, app publication not yet done. Private local review also at
  /private/tmp/gym-m1-source/runtime2/review.md. Owner action review/publication and
  physical real-workout acceptance remain genuine input gates.
- Long-preview publication action moved to fixed AlertDialog footer; routine UI
  version7/0.1.5-m1-local-template, SAME Room3/no new migration. New disposable-only
  test_android_template_review.py proves selection of exactly two public files,
  complete preview and footer visible at its top, explicit publish, unchanged
  workout/delivery rows and no authorization. Added to seven-version CI after
  existing process test. API31 local24 SQLite and actual footer UI test pass.
  Current UI follow-up CI/physical install still pending; no M1 completion claim.


## Phone v7 ready for owner publication — 2026-11-03

- Routine UI-only v7 installed under the standing install-r workflow after local
  Android40 JVM/24 SQLite and real selected-import/footer UI regression passed.
  Source5ba555b17bb256b56b097d83c4c2366abfcee400; APK SHA256
  6ddb16fbfd8bb30dc7ba38f07489c57c1a5577f10352463785d34f5e2fafcb0c.
  Same Room3, no further schema migration. Against a fresh verified pre-UI7
  backup, all11 application tables compare exactly equal after the update.
  Android's own metadata table is excluded from the app-entity count; comparison
  resumed against the ORIGINAL pre-update backup after correcting that verifier
  count, without repeating installation or replacing the baseline.
- Pre-UI7 backup <private-path>
  standalone884ef5bf242fe09cfb8bf40faa9f967a28387dba784ded9c5788b71846b6d52b.
  Post-check <private-path>
  The original session's final intent is verified end/partial, outcome retained;
  no unfinished slot remains. Its source/history have not been converted.
- All8 private YAML files still match phone readback exactly. Owner is locating
  them in the system picker: PERSONAL internal storage → Documents →
  GymAssistant-LOCAL-M1 → Reviewed-workout. The top Documents chip is a file-type
  filter; a search for gym cannot find generic training-template/exercise filenames.
  User's latest screenshot is already out of search, with ☰ beside Search this
  device; instructed to open that menu. Do not repeat search/back instructions
  once the UI has changed. No need to inspect unrelated files/accounts/work profile.
- Phone publication was requested, but not yet confirmed. Expected45:50/474phases,
  fixed Publish reviewed revision footer. Owner physical execution acceptance still
  open. Keep private data out of AI/Git/cloud; metadata-only local checks remain.
- APK/schema migration source78b649e CI already passed10/10. This follow-up adds
  backup regression and fixed-footer UI checks to all seven Android versions;
  current review CI is authoritative at PR19/checks and required before merge.
  Do not claim the full milestone complete. Real-workout cloud remains excluded.


- Owner has now found the local folder: the phone's visible storage-root label is
  The Rocket 2. Use that exact UI label, then Documents/GymAssistant-LOCAL-M1/
  Reviewed-workout. They are selecting all8 YAML files (training-template.yaml +
  exercise-1..7.yaml). Earlier screenshots were category/search views, not the
  physical Documents directory. Await the actual validation/publication result;
  do not claim import complete from folder discovery alone.


## Local runtime merged and physical short test verified — 2026-11-03

- PR19 merged57692f8 after all10 checks on1680546: standard41674024615 and seven
  Android41674024398. New selected-import/footer/publish regression passed on every
  supported version. Phone remains v7/source5ba555b, same app code as that CI head.
- Owner confirmed45:50/474phases and published. After USB reconnection, local exact
  comparison proves the published head, plan and all8 source definitions match
  /private/tmp/gym-m1-source/runtime2.32 eligible sets; Template unchanged by adds.
- Owner started a short test, paused and added once: adjusted46:00. Verified private
  stopped Room3/files backup before real process termination/relaunch. First sample
  was before the recovery commit (revision535/Paused); later verified536/
  RecoveredPaused against the ORIGINAL baseline, not a replacement snapshot. No
  automatic resume; source/effective plan rows, cursor/offset/completed identities,
  observed/pause/known-gap counters exactly unchanged; unknown-gap count +1 only.
  A second read after a wait is identical. No precise recovery latency claimed.
- Private recovery evidence /private/tmp/gym-m1-private-runtime2-phone-recovery.json;
  pre backup <private-path>
  SHA2564733c03f2809afa9c2bf2c073df66c61f1cc35139f12d21b5c3dade51a221b71;
  verified post <private-path>
  Do not print private payloads/receipts or replace the original comparison baseline.
- Owner confirmed discarding the short test. Verified outcome/last intent discarded,
  all537 test actions retained, published source still45m50, unfinished slot empty.
  Prior sessions, bindings/deliveries and source defaults unchanged. This was NOT
  a completed real workout. Full-workout/offline/synchronization M1 gates stay open.
- ADR-0007 proposed for the NEXT scope: selected finalized human1.2 session to the
  same personal Drive account, separate PERSONAL folder, no automatic upload/AI,
  at most50 additional direct attempts within500, immutable file-backed delivery
  and additive Room3→4 only after tests + fresh backup. Exact fields/boundaries in
  docs/adr/0007-m1-manual-personal-session-delivery.md. NOT APPROVED/IMPLEMENTED.
  Current live sender remains exact-synthetic only. No new Drive resources/calls.


## Natural-completion checkpoint fix found before full workout — 2026-11-03

- A local full virtual run of the private prepared plan (metadata only) exposed
  Checkpoint2.state's `phaseOffsetMs == if(active==null)0 else <Long>` comparison:
  the absent-phase branch boxed an Int zero, rejecting the valid Long offset0 at
  natural plan end. This also blocked subsequent acknowledgement/recovery. It did
  not affect the earlier partial/discarded short test. Changed only0→0L; no schema
  or source-format migration. Routine bug-fix v8/0.1.6-m1-completion-fix pending.
- Public regression covers full80→90s completion, absent active phase, reopen,
  recovery awaiting acknowledgement and explicit finish. Canonical1.2 completed
  fixture independently read in web. Local Android41 JVM/25 SQLite, web23 and
  contract23 fixtures pass; Python71 remains. No temporary private tests committed.
- Full PRIVATE VIRTUAL shape with one extra:476 completed phases,956 meaningful
  events,1,219,806 UTF8 bytes; independent TypeScript web validation passes without
  network. This is simulation, NOT evidence of a physical full workout. Raw virtual
  JSON stays outside Git under runtime2/DO-NOT-UPLOAD-VIRTUAL-human-history.json;
  temporary Kotlin/Vitest test files removed. Do not upload or treat it as a real
  finalized phone session. File-backed proposed ADR7 delivery keeps SQL rows small.
- Owner was told the completion bug is being fixed BEFORE a full workout request.
  No change to approved exercise targets/order, published source or real-cloud gate.


## Completion fix installed with exact preservation — 2026-11-03

- Phone v8/source6977599428ed32e11f5202e6d6ae2b653aba0505 installed with install-r;
  APK SHA2561d3e3d6244982c083461eadf1d5f8c59968973e106fa8563d2f0e1b3cab08d0d.
  Same signer and Room3, no migration. All11 application tables compare exactly
  equal; private library archive and all8 external source files unchanged. No
  unfinished workout, clear/uninstall, source re-publication or cloud upload.
- Fresh verified private backup pre-completion8-20261103T194615Z under the personal
  backup root; standalone SHA2561d792a9e76ee27230cefce9d848dfda9143fd1694d4e579a2faaaaf0cfebc1e5.
  Post comparison post-completion8-check-20261103T194624Z. Prior backups retained.
- PR20 current-head checks required before merge. Owner approval requested for
  proposed ADR7; do not implement Room4 or real delivery until explicitly approved.
  Full physical workout acceptance still open; short discarded test is retained.

## ADR7 approved; personal delivery implementation underway — 2026-11-03

- Owner requested explanation, then explicitly approved ADR-0007 in chat. Updated
  ADR status; scope includes one separate personal root, manual finalized format2
  human1.2 delivery and additive Room3→4 after tests + new verified backup. No
  live folder/upload/phone migration yet. Phone remains verified v8/Room3.
- Follow-up codex/m1-personal-session-delivery branches from PR20 head b4c5709;
  PR20 is still completing its seven-version CI and must merge first. Do not
  conflate current working changes with that reviewed completion-only PR.
- New dedicated personal table/file-backed payload adapter, durable setup intent,
  account/root verification, manual session selection/preview and same-ID retry.
  Existing inline synthetic jobs and exact synthetic remote path remain separate.
  Large canonical bodies are app-private files; SQL stores pointers/hashes only.
- Local initial49 JVM checks and28 real SQLite checks pass, including composed
  migrations/rollback, old inline delivery, immutable payload/reconstruction and
  final-source verification. Room4 export preserves all11 old entities exactly.
  Further UI and current-head CI still required. No physical install authorized
  merely by a successful local build: ADR7 backup/CI gates take precedence.
- Extended backup captures delivery-payloads/personal-drive archives separately,
  checking paths/links without extraction/content output. Python backup8 checks
  pass. Approved global allocation82→132/500; new persistent50 direct/4auth cap,
  no old counters reset/refunded; SDK internal requests unmeasured.

- PR20 merged c8c0f60 after all10 checks at b4c5709: standard41706474885 and
  seven-version41706474849. Follow-up branch fast-forwarded onto freshly fetched
  main without discarding changes. Phone v8 is the same completion-fix code.
- Strengthened Room3→4 fixture now includes published source/candidate/head,
  structural plan, paused format2 session and retained inline legacy delivery,
  comparing all11 tables exactly across rollback/success. Emulator preview
  assertion corrected to inspect disabled clickable ancestor, not enabled TextView;
  actual control was disabled. No product gate weakened.

- Final API31 real UI: process recovery passed, selected import/full review/fixed
  publication and personal preview passed with all recording/delivery rows unchanged,
  no selected-session send enabled and no Google authorization. Strengthened28
  SQLite tests pass after correcting only the synthetic fixture date to its UTC
  timestamp. Default build's personal allowance remains disabled; no phone Room4.

- Added actual finalized public synthetic-session UI selection test to the seven-
  version workflow. API31 passed: save partial, select captured Template/date/time,
  review27KB payload scope/Not sent status; all recording/delivery rows unchanged
  by selection, no Google authorization or upload. Corrected obsolete modern UI
  “not uploaded” claim to neutral persistent-delivery status; preview shows saved
  delivered/pending/conflict state. New test runs sequentially after import.
- Extended Room4 backup rehearsal passed on emulator,12 tables and14 public
  library files plus both personal archive namespaces. NOT a phone backup.
  Default build49 JVM checks and private configured build49 pass. Current-head
  CI must re-run for these final UI/status changes before phone migration.

- Supplemental generated stress check (temporary test removed):62m40,470 phases,
  15,042 quarter-second receipts →943 human events/1,333,718 UTF8 bytes; validation
  + serialization1610ms on this Mac. NOT phone timing/reliability evidence. No
  private source or network. Personal flow now reports preparation/account-root/
  durable-intent/readback stages instead of falsely remaining “waiting for
  authorization” during this work. Final-head CI remains required.

- Personal preview status is live after send instead of retaining its pre-send
  Not sent note. A generation guard prevents an older list refresh from replacing
  a selection/send status. No SQL/network policy or payload changes. Final source
  checks must cover this status correction before migration.

- Current CI caught the new personal-preview harness waiting for an offscreen
  Pause before scrolling on API37. Use the existing bounded scroll-and-click
  operation, require visible Resume after pausing, and verify exactly one new
  finalized database record before preview. No app requirement/assertion relaxed;
  migrations/process/import passed before that new test. Current-head CI rerun
  required; do not migrate/merge a failed revision.

- Corrected run passed32/33/34;35/37 exposed a second harness race: immediate
  confirmation click before the end-workout dialog rendered. Added explicit
  library/dialog/confirm visibility waits and synthetic-only failure diagnostics.
  No source/app/schema change or weakened data assertions. Require complete
  corrected matrix before the already approved migration.

- The corrected run passed31–34, while35–37 failed at the end-workout confirmation
  transition. Version split plus unhandled target35+ edge-to-edge makes navigation-
  bar overlap a concrete layout concern: Activity now enables consistent edge-to-
  edge and root recording column applies safeDrawingPadding. Main controls remain
  above system bars; dialog waits remain explicit. Routine layout fix, no engine/
  schema/transport change. Official guidance: developer.android.com/develop/ui/compose/system/insets-ui.
