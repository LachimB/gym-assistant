# Android prototype

Debug application id `dev.gymassistant.m0.debug`. This is not the production id. Minimum Android version is 12 (API 31).

The countdown uses `SystemClock.elapsedRealtime()` deltas. Pause time, observed running time, and unobserved gaps are separate. Cue text is simulated. The screen stays on while the activity is open. There is no foreground service in this build, so timing after the process is killed is not claimed.

Timer callbacks and button presses use the injected-clock `ClockedExecution`
adapter. Pause freezes scheduled/observed running time while pause duration grows;
Resume first settles the paused interval. JVM adapter tests do not replace the
remaining phone pause/resume/navigation and screen-off smoke checks.

```bash
cd <private-path>
./gradlew :android:assembleDebug
adb devices
adb install -r apps/android/build/outputs/apk/debug/android-debug.apk
```

`local.properties` must point at the Android SDK and is not committed. Android Studio Run is equivalent.

Physical smoke, from `docs/milestones/m0.md`: start the fixture, let the first prepare and hold finish, confirm the next phase is the plank, pause and confirm the remaining time stops, resume, step forward and back, turn the screen off and on, and write down what the clock did. A result after Android kills the process is an observation, not a timing pass.

## M1 local recording and recovery (ADR-0003 approved)

The debug app now starts an ungrouped synthetic Workout Session only after its
frozen Template/exercise sources, compiled plan, local plan digest, source revision,
engine/format versions, UTC start, IANA time zone and local date are committed.
Room/SQLite stores append-only logical actions and engine events, the checkpoint,
a single unfinished-session slot and pending outbox intents atomically. The
internal database is version 1, exported under `apps/android/schemas/`; it is not
a canonical cloud session JSON export. No existing saved user data is migrated.

After restart, the saved plan is loaded independently of current assets. Resume,
retain partial completion, or confirmed discard are explicit user decisions.
Discard releases the unfinished slot but retains the log and partial counters.
Completed sessions remain stored. A failed save stops timing and offers a reload;
no action is acknowledged as saved before its transaction commits. Duplicate
logical operations replay their original receipt; stale revisions fail safely.

The adapter samples every 250 ms while this process is running. If an interval
exceeds 2 seconds, it conservatively pauses as unobserved instead of advancing
exercise. An in-process monotonic gap has a measured gap duration; process restart
and clock reversal record an unknown-duration interruption. This policy is not a
background-service guarantee or a measured maximum data-loss bound. No foreground
service/audio implementation, canonical Drive sync, real workout import, previous
performance, manual-pattern support, completed-history UI or RPE/quality entry is
claimed. The bundled executable fixture remains the timed composite/pyramid.

The database is in private app storage. Android automatic cloud backup and database
device transfer are excluded; approved portable backups remain future work. No
OAuth or network history upload was added. **Do not uninstall or clear app data**
when testing an upgrade. Install the debug APK with `adb install -r`.

### Automated evidence

`RoomSessionStoreTest` uses uniquely named disposable SQLite databases and covers
rollback (including slot/events/checkpoint/outbox), retry deduplication, stale
writers, immutable snapshots, reopen, known/unknown gaps, partial/discarded records,
UTC/time-zone/DST dates and driver state after failed commits. Run instrumentation
only on disposable emulators; connected-test installers may reset target packages.

`.github/workflows/android-recovery.yml` defines API 31–36 and 37.0 emulator jobs.
Each runs instrumentation plus the emulator-only host test:

```sh
python3 tools/test_android_process_recovery.py --serial emulator-5554
```

That host test performs actual force-stop/relaunch, checks that the recovered
workout stays frozen until explicit resume, resumes and verifies newly saved
progress, ends partially and verifies the unfinished slot is released after a
second restart. It refuses physical devices and never clears app data.
Record actual run results in the handoff; the existence of a test is not proof it
passed. API 31–36/37.0 passed on `f002438` (run `41451951198`). The separate
Pixel 10a / Android 17 short physical smoke also passed: recovery, explicit resume,
paused navigation without crediting skipped work, second recovery and partial
finalization. See `docs/milestones/m1-recovery.md` and its sanitized
`m1-device-recovery.json` for exact counters and limits. Long-duration/background,
real-workout and actual Android Drive synchronization remain unproven.
