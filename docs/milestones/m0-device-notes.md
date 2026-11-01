# M0 physical-device notes — 2026-11-01

The debug APK was installed on the attached phone. The interactive smoke in `docs/milestones/m0.md` was not observed, because the phone stayed on the lock screen.

| Item | Result |
|---|---|
| Model | Pixel 10a |
| Android version | 17 |
| Package | `dev.gymassistant.m0.debug` (provisional debug id, not the production id) |
| APK | `apps/android/build/outputs/apk/debug/android-debug.apk` |
| Install | `adb install -r` succeeded |
| Launch | `am start` delivered `dev.gymassistant.m0.MainActivity` |
| Focused window | `com.android.systemui` dream / alternate bouncer. The Gym Assistant screen was not visible. |
| Screen off and on | Not tested |
| Pause, resume, step | Not tested on the device |
| Process death | Not tested. This build does not claim timing across process death. |
| Foreground service | Not added. That permission needs a separate yes. |
| Audio | Not device audio. The UI is a simulated cue label. |

Repeat the smoke after unlocking the phone: start the fixture, finish the first prepare and hold, confirm the next phase, pause and check that the remaining time stops, resume, step forward and back, turn the screen off and on, and write down the clock. A result after Android kills the process is an observation, not a timing pass.
