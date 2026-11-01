# M0 physical-device notes — 2026-11-01

Chronological observations on the same day, on a Pixel 10a running Android 17. Package `dev.gymassistant.m0.debug` (provisional debug id, not the production id). APK `apps/android/build/outputs/apk/debug/android-debug.apk`. Earlier untested statuses below describe those attempts; the final guided smoke section records the latest results.

## First attempt: lock screen blocked the UI

The debug APK was installed on the attached phone. The interactive smoke in `docs/milestones/m0.md` was not observed, because the phone stayed on the lock screen.

| Item | Result |
|---|---|
| Model | Pixel 10a |
| Android version | 17 |
| Install | `adb install -r` succeeded |
| Launch | `am start` delivered `dev.gymassistant.m0.MainActivity` |
| Focused window | `com.android.systemui` dream / alternate bouncer. The Gym Assistant screen was not visible. |
| Screen off and on | Not tested |
| Pause, resume, step | Not tested on the device |
| Process death | Not observed in this attempt. A later check the same day is recorded below. |
| Foreground service | Not added. That permission needs a separate yes. |
| Audio | Not device audio. The UI is a simulated cue label. |

## Later the same day: locked screen, execution continued

Michal ran Gym Assistant with the screen locked. Workout execution continued in the background. This is a successful basic screen-lock / background-execution test, dated 2026-11-01.

Screen-off was not separately described. This note records the locked-screen background continuation he reported. It is not proof of process-death recovery, and it is not proof of long-duration background reliability. Long-duration background reliability remains untested.

| Item | Result |
|---|---|
| Screen locked, execution continued | Pass. Basic screen-lock / background-execution test on 2026-11-01. |
| Screen off and on | Not tested. Not described separately from the locked screen. |
| Long-duration background reliability | Not tested. |
| Pause, resume, step on device | Not tested. |
| Foreground service | Not added. |

## Later the same day: process termination

Michal started a workout, progressed into the execution sequence, terminated the process while it was unfinished, and reopened Gym Assistant. Dated 2026-11-01, Pixel 10a, Android 17.

| Item | Result |
|---|---|
| Clean application restart | Pass. The app restarted cleanly and did not crash. |
| Unfinished workout state | Lost. The previous execution position was not restored. |
| Persistent workout recovery | Unsupported / not implemented. M0 does not record a Workout Session. |
| Full process-death recovery | Not passed. A clean restart is not recovery of the unfinished workout. |

Losing the unfinished workout is acceptable for M0. Persistent Workout Session recording and recovery are out of M0 scope, so this is a known limitation, not an unexpected M0 failure. Reliable unfinished-session recovery is mandatory in M1 and is not an M0 acceptance gate. See `docs/milestones/m1.md`.

At this point, screen-off and on-device pause/resume/step were untested. Those short scenarios were subsequently reported passing in the guided smoke below. Long-duration background reliability remains untested.

## Final guided smoke after clock-accounting APK installation

Date: 2026-11-01. Michal reported **all four guided checks passed** after the tested
debug APK was installed. This is user-reported physical-device evidence; Codex
verified installation/build identity, not the on-device interactions themselves.

| Build / device context | Evidence |
|---|---|
| Phone | Pixel 10a; model read through ADB |
| Android | 17, API 37; properties read through ADB |
| Application | `dev.gymassistant.m0.debug` |
| Application source | Clock-accounting changes from merged PR #4, implementation `5aa176fcd16d58052d241db2c33abe17324e8366`; later CI/documentation changes do not change application source |
| APK SHA-256 | `7ff9c889489561e1c6ff7c0592070cf562cca88ead1fd38d6d385ea8fb301138` |
| Installation | `adb install -r` returned Success; installed APK checksum subsequently matched the tested local artifact |

The first pause attempt did not show increasing pause time. The installed APK's
checksum differed from the tested artifact. Following installation of the tested
APK, the user reported the full guided smoke passed. The checksum mismatch alone
does not establish which source revision was previously installed.

| Guided scenario | Expected behavior | Reported result |
|---|---|---|
| Pause for approximately 10 seconds, then resume | Countdown and scheduled/observed running time freeze; pause duration increases; resume continues from the held position and pause duration stops growing | Pass |
| Forward and Back while paused | Phase/scheduled position changes, observed running time stays fixed, pause duration continues growing; resume uses the selected phase | Pass |
| Screen off/on while running, approximately 10 seconds | On return, phase/counters show progress consistent with elapsed time | Pass |
| Screen off/on while paused, approximately 10 seconds | Scheduled/observed running time stay fixed; pause duration increases | Pass |

The report contains no before/after counter values, logs, exact measured durations,
or new drift measurement. The screen-off result demonstrates correct displayed
state on return in these short scenarios; it cannot establish continuous callback
or audio delivery during suspension. Long-duration reliability, interruption/audio
policies, other Android versions, and persistent recovery remain unproven or later
milestone work. M0 closure and merging PR #2 still require review and approval.
