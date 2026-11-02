# M1 local recording/recovery slice

ADR-0003 was approved by the user on 2026-11-02. This slice implements the local
transaction boundary; it does not complete M1 or authorize Drive/OAuth.

## Internal format and compatibility

- New Room database `workouts-v1.db`, schema version 1. No M0 saved sessions exist
  to migrate. No destructive migration fallback. Keep the exported Room schema in Git.
- Immutable `SessionStart`: independent UUID, exact Template/exercise YAML, source
  Template revision in the frozen compiled plan, explicit format/engine version,
  SHA-256 of Kotlin's fixed serialized local plan representation, UTC start instant,
  IANA zone and captured local date. Digest is an internal integrity check, not a
  newly adopted cross-platform canonical digest specification.
- Checkpoint: reducer state/counters, completed phase indexes in that immutable
  plan, acknowledgement, outcome, unknown-gap count and optional UTC finalization.
- Actions: per-session sequence/revision, unique logical operation ID, expected
  base revision, complete action intent, newly emitted engine events and receipt.
  Event history is not duplicated inside the active reducer checkpoint.
- Outbox: one pending intent per committed session revision; no credentials,
  canonical materialization, network worker or Drive API implementation.
- The only current launch is ungrouped. No active Season/Program is inferred.
  Grouped context resolution and canonical organization snapshots remain later work.
- Canonical `schemas/session.v1.schema.json` is unchanged. Export/projection must
  map internal records to the reviewed canonical contract before any cloud sync.
  Future database/canonical migrations require compatibility/rollback review.

## Validation to record

| Scenario | Automated check |
|---|---|
| Commit failure leaves all local records consistent | Real Room transaction failure before outbox; start and progress rollback |
| Same logical action retry does not append again | Original receipt replay after a later action; changed intent rejected |
| No implicit second execution owner | Singleton unfinished slot; expected-revision failure |
| Source edits do not rewrite unfinished work | Reopen uses saved source/plan; external asset candidate has no role |
| Recover without invented exercise | Unknown restart gap; monotonic reversal; long live-process sample gap |
| Explicit decision after interruption | Driver blocks navigation until recovery choice; host force-stop/relaunch test |
| Partial/discarded history survives | Reopen after finalization; logs/outbox retained; finalized writes rejected |
| Date/time provenance stays explicit | UTC vs local day and repeated DST hour cases |
| Android-version compatibility | Emulator matrix API 31–36 and 37.0; actual pass/fail recorded separately |
| Real-phone recovery | Pixel 10a / Android 17 short smoke passed; `m1-device-recovery.json`; no clear/uninstall |

## Observed local evidence — 2026-11-02

- Android 12 (API 31), ARM64 default emulator: all 10 instrumentation tests passed
  in 0.944 seconds using real Room/SQLite files.
- Actual app process termination/relaunch test passed. The final recovery UI restored
  1061 ms of observed work, stayed frozen until explicit resume, then retained
  2177 ms across the next restart. Source snapshot identical; no app data cleared.
- Final APK SHA-256: `464ce24bbd3a9c9689eb7f386259d88069635d15e101a5af015f148438aa055f`.
- Full GitHub emulator matrix passed on `1181a771e29bc801dfd57a2c7d70aa6924a6f801`:
  API 31, 32, 33, 34, 35, 36 and 37.0, each with 10 real SQLite tests and the
  actual force-stop/relaunch/explicit-resume test. Contract, engine and web checks
  passed on the same commit. [Recovery run](https://github.com/LachimB/gym-assistant/actions/runs/41424079753).
- Host-test counters below are observed milliseconds restored after the first
  termination and after explicit resume plus a second termination. All seven
  verified unchanged frozen source, no automatic resume, no app-data clearing,
  and durable partial finalization. Very short first samples are checkpoints,
  not workout-duration or timing-precision acceptance.

  | API | First restored | Second restored |
  |---|---:|---:|
  | 31 | 263 | 2744 |
  | 32 | 412 | 800 |
  | 33 | 3 | 2534 |
  | 34 | 2 | 2910 |
  | 35 | 1 | 2924 |
  | 36 | 596 | 3652 |
  | 37.0 | 2 | 417 |

- Full seven-version matrix passed again on `796cff2`, run `41450398107`, after
  fixing the harness to observe the result of its last allowed system-ANR dismissal.
  Production app/checkpoint policy unchanged.
- Latest implementation head `f002438` passed all seven recovery jobs again, run
  `41451951198`, and contract/engine/web CI.
- Physical-phone short smoke passed as recorded below. Short emulator evidence is not a
  long-workout reliability claim. The stalled local Android 17 image download was
  stopped; the Android 17 evidence above comes from CI's official emulator.

## Observed physical-phone evidence — 2026-11-02

Pixel 10a / Android 17, the checksum-verified APK above, installed with
`adb install -r`. No instrumentation installer, uninstall or data clear on phone.
The user supplied the initial screenshot and confirmed pause time advanced while
exercise stopped. Subsequent values were read only from this app's synthetic UI.
No phone database contents were read or exported.

| Observation | Scheduled ms | Observed ms | Other evidence |
|---|---:|---:|---|
| User-paused initial hold | 4686 | 4686 | 0:08 displayed; one completed phase |
| First force-stop/reopen | 4686 | 4686 | Same hold/0:08; saved pause 85060 ms; explicit recovery choices |
| Explicit resume, then Pause | 8237 | 8237 | One completed phase |
| Forward while paused | 12000 | 8237 | One completed phase; skipped work not credited |
| Back while paused | 4000 | 8237 | One completed phase |
| Second force-stop/reopen | 4000 | 8237 | No automatic progress; explicit recovery choices |

Both recovery screens stayed fixed over two UI captures with at least three
seconds between captures. The agent confirmed Save partial workout in the app,
observed Workout saved, force-stopped/reopened again and observed Start Workout.
The source fixture/history was not cleared. Physical UI evidence does not directly
inspect final DB/outbox rows; real SQLite retention is separately tested on emulators.

A temporary phone verifier initially asserted that navigation preserves scheduled
position. That assertion was wrong: the accepted engine's `step()` moves the
scheduled cursor. The corrected check verifies observed exercise and completed
phase count; no app behavior or requirement was changed to pass the smoke.
Sanitized evidence: `m1-device-recovery.json`. This is not real-workout, long-duration,
background/audio/battery, airplane-mode or Android Drive-sync acceptance.

## Dependency choice

Room 2.8.5 with KSP 2.1.10-1.0.31 in KSP2 mode builds with the existing Kotlin
2.1.10/AGP 8.8.2 toolchain. KSP1 failed annotation processing; KSP2 resolves it.
This avoids an unrelated compiler/AGP migration. The pre-existing compileSdk 36
AGP warning remains; no unsupported compatibility claim is made from that warning.
Sources: [Room release notes](https://developer.android.com/jetpack/androidx/releases/room),
[KSP release](https://github.com/google/ksp/releases/tag/2.1.10-1.0.31).

## Boundaries

No foreground-service reliability or 60–90 minute battery/performance claim. The
250 ms sampling cadence and conservative 2 s gap threshold are implementation
policy, not a guaranteed checkpoint deadline. An app/OS freeze, failed disk write
or power loss can leave progress only up to the last successfully committed
checkpoint. Unknown gaps remain unknown and do not become observed work.

## Physical phone smoke test

Install the built debug APK with `adb install -r` only; retain the existing app ID,
signing key and data. Do not run connected instrumentation installers on a phone
containing personal app data; those tests belong to disposable emulators.

1. Open Gym Assistant and start the synthetic 50-second workout. Let a few seconds
   pass, tap Pause and note the saved phase/remaining time and observed counter.
2. Terminate the app process (force stop, not uninstall/clear data), then reopen.
   Expect an unfinished-workout recovery screen with Resume, Save partial and
   Discard visible near the top. The saved position remains frozen while waiting.
3. Resume explicitly; progress must continue from that checkpoint. Pause, navigate
   forward/back and verify that a skipped phase was not recorded as completed.
4. End the workout with confirmation. Reopen and verify that it offers a new
   workout rather than resuming the finalized one. Local records/outbox remain.
5. Report device/Android version, checkpoint vs recovered values, any crash/save
   error, unexpected automatic progress or missing controls. This short smoke does
   not establish long-duration/background/battery/audio reliability.
