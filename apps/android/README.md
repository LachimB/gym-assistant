# Android prototype

Debug application id `dev.gymassistant.m0.debug`. This is not the production id. Minimum Android version is 12 (API 31).

The countdown uses `SystemClock.elapsedRealtime()` deltas. Pause time, observed running time, and unobserved gaps are separate. Cue text is simulated. The screen stays on while the activity is open. There is no foreground service in this build, so timing after the process is killed is not claimed.

```bash
cd /Users/michal/repos/private/gym-assistant
./gradlew :android:assembleDebug
adb devices
adb install -r apps/android/build/outputs/apk/debug/android-debug.apk
```

`local.properties` must point at the Android SDK and is not committed. Android Studio Run is equivalent.

Physical smoke, from `docs/milestones/m0.md`: start the fixture, let the first prepare and hold finish, confirm the next phase is the plank, pause and confirm the remaining time stops, resume, step forward and back, turn the screen off and on, and write down what the clock did. A result after Android kills the process is an observation, not a timing pass.
