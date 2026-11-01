# First daily-use release: acceptance and controlled cutover

**Not the same as M0.** All `P0` requirements must be implemented or explicitly re-approved as a changed scope. No migration or real PDF import can be called complete until user-provided private records have been reviewed in a safe development environment.

## Release gates

1. Realistic composite / pyramid routines execute on supported Android versions, in portrait focus/detail UI, with predictable manual/timed transitions, pauses/rewinds, configurable audio and useful set-level performance logging.
2. Offline and background continuity tested with real 60–90 minute workouts, OS interruptions, Bluetooth disconnection, incoming calls, low battery and process restart; no falsely reported duration or lost records; document limitations.
3. Local durable logs → synchronized canonical JSON sessions → web history and corrections → annual Google Sheets export verified as idempotent (including offline retries and protected comments).
4. Web graphical/YAML editor, full simulator with speeds + timeline, explicit error review, migration tooling, optional AI reviewed PDF import and manual non-AI path operational.
5. Legacy historical migration reviewed with uncertainty flags and source references, old journal moved to read-only state only after approval and verification; no deletion.
6. Versioned backups before imports/migrations, selective restore and preview tested on disposable sample data; alternate copy outside primary app directory.
7. Security/privacy review covers consent, independent app/storage identity, OAuth scopes, no personal history on app-managed servers, BYOK key handling, auth recovery, multi-device ownership and deletion/export.
8. CI/instrumentation/E2E/conformance tests green, documented supported Android matrix, accessibility acceptance, no high-severity defects.
9. Frequent Android physical phone + web testing evidence, acceptable battery and UI responsiveness. Private invite-only hosted environment running within approved cost boundary.
10. Short parallel validation period with real sessions and the old journal as a temporary comparator. Approve cutover only when generated journal agrees and no data loss is observed.

11. Training Seasons and Programs are optional and fully manageable: clone with cleared dates, many-to-many participation, no more than one Active Season, multiple active Programs, explicit transitions and confirmation before Season completion/cascade; no historical membership rewrites.
12. Program Template membership supports latest/pinned, compatibility-safe limited overrides and full effective-plan validation; immutable versions; user-confirmed selective promotion after Workout Session; previous-performance source matching honors same Program then same Template fallback.
13. Android home/Library works with or without Seasons/Programs; web planning supports versions/cloning; Google Sheets headers use historical optional context and basic Program/Season analytics include unassigned sessions globally.

## Compliance checklist for agent handoff

- [ ] Requirement IDs map to implementation tasks and tests; all P0 reviewed.
- [ ] Plan changes tracked in ADRs and accepted where required.
- [ ] No company account/service/identity, no real private fixtures or credentials in Git.
- [ ] External services in approved inventory; billing explicitly confirmed.
- [ ] Build artifacts signed and staging/prod separated; Android version compatibility evidenced.
- [ ] Workouts/simulator verified equivalent on known execution fixtures.
- [ ] Drive permissions validated, including user-selected existing folders and Sheets files.
- [ ] Journal generated without duplicate rows/columns; physiotherapist annotations retained after rebuild.
- [ ] Backups independently restorable; legacy journal retained read-only.
- [ ] One Active Season policy tested, independent standalone Programs tested, cross-Season Program history preserved.
- [ ] Explicit per-Program Season completion/carry-over prompts and clone date clearing tested.
- [ ] Latest/pinned Template policies, compatible/incompatible overrides and user-approved selective promotion audited.
- [ ] Open gap register cleared or intentionally documented with approved exception.
