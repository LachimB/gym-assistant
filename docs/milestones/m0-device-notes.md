# M0 physical-device acceptance summary

Technical evidence from 2026-11-01 on Android17/API37, debug application
`dev.gymassistant.m0.debug`. Source `5aa176fcd16d58052d241db2c33abe17324e8366`;
APK SHA256 `7ff9c889489561e1c6ff7c0592070cf562cca88ead1fd38d6d385ea8fb301138`.

| Scenario | Evidence and limitation |
|---|---|
| Install and launch | Verified debug install and artifact identity |
| Pause/resume and paused navigation | Short guided smoke reported passing; interactions were user-reported rather than independently instrumented |
| Screen off/on, running and paused | Short smoke reported passing; no continuous callback/audio guarantee |
| Process termination | Clean restart; unfinished position not persisted in M0 |
| Foreground clock accounting | Synthetic measured samples in `m0-device-timing.json` |

M0 does not persist Workout Sessions. Durable recovery is mandatory in M1.
Long-duration/background/audio reliability is not established by these short tests.
Detailed private narratives and screenshots are retained outside Git.
