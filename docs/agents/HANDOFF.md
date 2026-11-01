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
1. Finish the remaining phone smoke in `docs/milestones/m0-device-notes.md`: on-device pause, resume, and step, plus screen-off and on. Do not add a foreground service unless that smoke shows the activity timer is insufficient and there is a separate yes. Do not treat the clean process restart as recovery. Long-duration background reliability is still untested. Unfinished-session recovery waits for M1.
2. Keep GitHub writes on `LachimB` with token variables unset. Author stays `MichalB <<private-contact-redacted>>` in the repository only.
3. Do not start Google OAuth, hosting, Play signing, or a billable resource without a separate approval.
4. Early end is available in the prototype; detailed partial-performance summaries stay later.

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

## Approved domain extension: Decisions #79–#97

Read `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001. Canonical names: Training Season, Training Program, Training Template, Workout Session. Basic organization is P0 for the daily-use release. The M0 executable fixture stays a standalone template. Organization schemas and contract tests exist. The prototype does not resolve Program membership at runtime. Most important unresolved M2 detail: deterministic expansion of limited set-use adjustments within an explicit composite step order. Before implementing user-facing promotion, require explicit approval, a scope preview, and a stale-revision check.
