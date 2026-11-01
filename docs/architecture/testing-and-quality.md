# Verification and quality strategy

## Layered testing

| Layer | Scope | Required proof |
|---|---|---|
| Schemas and semantic validator | YAML/JSON structural validity, ID uniqueness, reference integrity, version support | Unit tests including invalid/dangling refs; starter validator is intentionally not a full engine |
| Pure execution engine | Timed/manual phases, alternating reps, composite pyramids, side/variation, rewind, skip, dynamic edits | Virtual monotonic clock, deterministic golden traces, property-based state invariants |
| Cross-platform conformance | Same plan+inputs → identical domain event sequence on Kotlin and web | Shared language-neutral JSON fixtures, versioned expected outputs |
| Android platform integration | Foreground/locked, notification controls, TTS/audio focus, calls/Bluetooth, process death | Multi-version emulator matrix + actual user's Android device observations |
| Durable offline persistence | Autosave every completed rep/set/block, restart, conflict sync, multi-device transfer | Fault injection: kill app between append and upload, offline network, retry, ETag conflict |
| Google Sheets projection | One-way correct row/column layout, 2+ sessions/day, comments and rebuild | Real authorized disposable Sheet integration; idempotency and third-party note preservation tests |
| PDF import/migration | Provenance, uncertainty and manual review; no invented values | Synthetic PDF/Sheet and user-approved private acceptance, no private fixtures in CI |
| Security | OAuth scopes, BYOK secrets, malicious YAML/PDF, privacy, deletion | Threat model, auth integration checks, automatic secret checks, manual review |
| Release operations | Android install/update/rollback, staging/prod, CI, cost controls | APK installation evidence, approved provider inventory, release/rollback drills |

## Required representative scenarios

1. Alternating 2+ exercise occurrences within a block; one exercise has 2 sets with different band resistance and variation; check every rep in expected order.
2. 4-second prepare + 8-second hold times eight, mixed with manual waits, pause and resume; audio never shifts planned time boundaries.
3. The same exercise family appears in two blocks at different intensities: strict last-performance matching returns the correct occurrence, never cross-template.
4. Mid-session skip/rewind and block insert; preserve event log, accurate completed counts, no phantom logged reps and stable source plan hash.
5. App is backgrounded/locked; audio focus lost; Bluetooth disconnect; incoming call; active timer resumed or stopped according to selected policy with warnings.
6. Android process killed/reboot; user sees reliable last checkpoint and uncertainty warning; no silent completion of missing phases.
7. Offline session upload retries; Google Drive concurrent YAML edit, version collision, bad data, revoked token, folder relocation and merged vs manually resolved conflicts.
8. Two clients enqueue same session to Sheets; journal contains one occurrence of session, protected third-party annotations survive selective rebuild.
9. Historical import preserves unknown values and source references; duplicate exercise names/variations do not merge incorrectly.
10. Schema migration and backup restore preview safely recover one session without overwriting newer edits.

## M0 fixture coverage

The synthetic golden timeline covers two different exercises, separate hip-lift sets with **different variations and band amounts**, interleaving of reps, a timed rest and 50,000ms scheduled runtime. It does not yet cover actual background audio, user voice, free-form manual rep timing, error handling or external Drive OAuth. Those must be added later and never reported as already tested.

## Release and regression policy

Run schema checks in CI and dedicated Kotlin/TS engine test matrices in M0. M0 acceptance also includes the composite fixture, virtual-clock tests, and deterministic cross-platform traces. An Android emulator matrix may be deferred during the M0 engine spike; physical-phone smoke remains an M0 gate. From M1 onward, automated emulator testing across supported Android versions is mandatory, plus physical-device smoke on accepted builds. Decisions #48 and #54 stay in force. A physical-device pass must record OS, model (without unnecessary personal identifiers), app build, scenario, observed audio/clock behavior, expected vs actual. Persist results in GitHub Issues or redacted test reports.

## New organizational conformance scenarios (#79–#97)

Validate cross-record invariants beyond JSON Schemas: at most one Active Season; Program↔Season many-to-many participation, no Program duplication; independent active standalone Programs; no Season assignment for Program-only sessions; active participation needed for joint context at START; historical sessions remain valid after transitions; clone resets Dates and Draft; membership overrides do not mutate source Template; incompatible/latest update retains last validated configuration; selected post-session deviations require explicit approval and an unchanged expected target revision. Strict previous-performance lookup requires Template, block/occurrence/variation and set identities with same-Program preference and same-Template fallback; source disclosed. Cross-platform execution must be tested on the **resolved** effective plan, not merely the base Template.

`tools/organization_contracts.py` and `tests/test_organization_contracts.py` provide an **illustrative deterministic contract harness** with negative test scenarios, not full application engine, sync or lifecycle implementations. `docs/product/organization-acceptance-scenarios.md` contains the feature acceptance matrix.
