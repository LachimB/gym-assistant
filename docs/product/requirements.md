# Product requirements catalog — binding, v2 draft (Decisions 1–97)

**Status:** approved behavior. IDs are stable; use in issues, PR descriptions, tests and acceptance evidence. MVP = first daily-usable private release after M0–M5. M0 is NOT MVP. Priority `P0` means mandatory first release; `P1` means post-MVP unless otherwise noted; `P2` means future/architectural provision. All user-approved requirements are retained even when deferred.

## Canonical terminology and optional organization

**Training Season** (optional) → **Training Program** (optional reusable collection) → **Training Template** (reusable immutable workout definition) → optional Section → Block → Set → Repetition/Action. An actual execution is a separate **Workout Session**, optionally attributed to a Season and/or Program. Seasons/Programs are references, not required parent containers. No independent physical ownership of Templates; see `docs/architecture/seasons-programs-and-promotion.md` and Decisions #79–#97. Older references to “training session” as a definition are superseded by **Training Template**; older “actual session” means **Workout Session**.

## Seasons, Programs, membership and promotion

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-ORG-001 | P0 | Users can create, edit, archive and clone optional Training Seasons and Training Programs. Ungrouped Training Templates execute without either; names required, other metadata optional. |
| GA-ORG-002 | P0 | Training Templates are independent and reusable across 0+ Programs; Programs are standalone or in 0+ Seasons via stable, auditable participation identities; reuse does not duplicate Template or Workout Session records. |
| GA-ORG-003 | P0 | At most one globally Active Season; activation of another requires pausing/completing/abandoning or otherwise resolving the former with explicit user confirmation. Several active Programs may coexist. |
| GA-ORG-004 | P0 | Program global lifecycle draft/planned/active/paused/completed/abandoned is independent of Season participation planned/active/paused/ended; archivedAt separate; explicit reopening. Never auto-change states from dates. |
| GA-ORG-005 | P0 | Season transition or completion prompts explicit per-Program carry-over/end/pause/standalone/global status choices; preview/confirm effect and preserve all historical sessions. Nonautomatic completion suggestions allowed. |
| GA-ORG-006 | P0 | Standalone active Programs coexist with an active Season; direct unassigned Training Template execution always possible. Program-only Workout Sessions must not silently inherit active Season. |
| GA-ORG-007 | P0 | Optional start/end dates (may overlap/cross Season), notes, descriptions, goals, focus, suggested frequency/rotation, tags and visual identifiers. Program global lifecycle independent of Season. Advanced scheduling optional post-MVP. |
| GA-ORG-008 | P0 | Clone Seasons/Programs (optionally with nested Programs); new identities and Draft status, **clear start/end dates by default**, reuse Template refs/policies/overrides by default; optional independent Template copy; preview before confirmation; NEVER duplicate history. |
| GA-ORG-009 | P0 | Every committed Training Template modification creates a NEW immutable revision; historical Workout Sessions and execution-plan snapshots reference the exact resolved revision permanently. |
| GA-ORG-010 | P0 | Each Program–Template membership has stable ID and `latest` or `pinned` version policy. Existing/older versions and comparison are accessible, and scheduled work may pin a revision. |
| GA-ORG-011 | P0 | Material Season/Program structural changes create immutable revisions; metadata/status changes audited; preserve compare/restore-as-new-revision and immutable session-captured labels/IDs. |
| GA-ORG-012 | P0 | Program membership may override existing Template values (resistance, equipment, per-set reps/side counts, phases/tempo/rest), and limited existing-set selection/repetition; never arbitrary exercise/block reorder/insert. Preserve source and derived IDs. |
| GA-ORG-013 | P0 | Resolve effective Template + Program overrides, show source/diff and preview, validate complete execution plan before starting. Overrides never alter shared source Template; changes create new Program revision. |
| GA-ORG-014 | P0 | `latest` membership auto-adopts only compatible Template revisions. Invalid/ambiguous overrides block upgrade; retain last validated configuration; notify and offer three-way diff/explicit resolution, no silent drop. |
| GA-ORG-015 | P0 | Store optional session context: active Season and/or globally Active Program, with active participation when both. Capture labels, IDs, revisions, membership and participation at start; no retroactive reassignment on moving/reorganizing. |
| GA-ORG-016 | P0 | Same-Template strict occurrence/set previous performance prefers selected Program across Seasons; transparently falls back to same Template other/unassigned Program if absent. Disclose original session/Season/Program, allow manual source selection; never cross-Template fallback. |
| GA-ORG-017 | P0 | Android Home shows active Season and all active participating and standalone Programs; hybrid quick Template library and Season/Program hierarchy; search/filter, recent, favorites, Program context selection without redundant prompts. Web is planning-first, Android execution-first; lifecycle operations both. |
| GA-ORG-018 | P0 | Web filters and basic aggregated counts/duration/frequency by Season, Program, Template/date; global totals include unassigned workouts. Annual monthly Google Sheets header shows captured Season and Program labels without reorganizing journal. |
| GA-ORG-019 | P0 | Completed Workout Session review allows selectively promoting deviations to a Program membership override revision or NEW shared Template revision; explicit scope selection/preview/approval. No default change automatically; old session and revisions remain immutable. |
| GA-ORG-020 | P0 | Program revision conflict-safe promotion and upgrade transactions require expected current revision checks; reject stale review, preserve alternative/conflicting versions and allow recovery. |
| GA-ORG-021 | P0 | Versioned YAML Seasons/Programs/participation schemas and JSON Workout Session/promotion schemas; Drive storage, backups, migration, offline support and traceability updated without assuming personal sample data in Git. |

## Domain, content and authoring

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-DOM-001 | P0 | Inside each Training Template the execution hierarchy is optional section → block → set → repetition, with set-level timed holds/phase details. Composite blocks may interleave 2+ exercise occurrences at **individual repetition** granularity. |
| GA-DOM-002 | P0 | Support sets/reps, timed holds, left/right, intervals, circuits/supersets, and instruction-based coordination; arbitrary multi-phase repeat programs (prepare/hold/rest/manual). |
| GA-DOM-003 | P0 | Composite blocks permit fixed/custom order and pattern-driven alternation, 2+ exercises and pyramids, editing and reordering. Stable occurrence/set identifiers survive position changes. |
| GA-DOM-004 | P0 | Exercise library has globally reusable families, independent variations, multilingual short/long names, descriptions, associated media and equipment, with per-template overrides and independently comparable history. |
| GA-DOM-005 | P0 | Resistance recorded per set; bands include placement, multiple identical bands, combined configurations; pyramids may vary resistance and exercise variation per set. Support weights kg/lb; never assign numerical equivalence to categorical bands by default. |
| GA-DOM-006 | P0 | Reusable immutable-version Training Templates live as human-editable YAML with safe app edit and external Google Drive edit; original PDF associations and explicit applied version changes. Template modifications never rewrite session history. |
| GA-DOM-007 | P0 | Web WYSIWYG + YAML editor, drag/drop sections/blocks/sets, validation, duplicate, undo/redo, live preview and export/import. Archive/restore and compare versions, including archived. |
| GA-DOM-008 | P0 | Optional PDF import parses text/tables/images into a reviewable draft; detect existing exercises, uncertainty, variants and provenance; compare to prior template; no silent overwrites; manual import when AI disabled. |
| GA-DOM-009 | P0 | Original multilingual content preserved; translation may be manual or optional AI. UI English initially; voice language independent. Localized units, dates, numbers; language fallback to original content. |

## Android execution and session recording

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-EXE-001 | P0 | Android 12+ native phone-first execution; overview + focused detailed screen + simplified focus mode; quick access to current/other blocks; light/dark, portrait, responsive text. |
| GA-EXE-002 | P0 | Minimal at-a-glance current phase, timer, rep/set/block progress, upcoming phase; expand names, instructions, images, optional video; large accessible touch targets and high contrast. |
| GA-EXE-003 | P0 | Timed sequences use monotonic scheduled elapsed time, not TTS completion; runtime audio delays never stretch planned phases. Separate scheduled timing and actual event measurements. |
| GA-EXE-004 | P0 | Manual repetitions may start next stopwatch immediately or wait for Start; record individual rep elapsed times; stopwatch can pause, undo, and navigate. |
| GA-EXE-005 | P0 | Configurable immediate transition, timed rest, or indefinite manual pause per phase/block; skip/go forward/backward at rep/set/block level; allow repeating stages safely. |
| GA-EXE-006 | P0 | Optional spoken instructions, beep and silent modes; language and countdown settings per block/exercise; global customizable audio ducking for podcasts/audiobooks; test real-world audio focus. |
| GA-EXE-007 | P0 | During active workout keep-screen-awake option; continue when locked/background when allowed by Android; notification pause/resume/skip/stop; configurable call/Bluetooth interruption handling. Auto-paused work never silently restarts. |
| GA-EXE-008 | P0 | One unfinished session; local transactionally persisted progress after every rep/set/block; resume prompt, partial completion, optional interruption reasons, safe discard confirmation, distinction active/rest/total. |
| GA-EXE-009 | P0 | Planned values prefilled from template or strict previous same-Template/same-block-occurrence/same-exercise-occurrence+variation/same-set match only; prefer same Program across Seasons, transparently fall back to same Template in other context, NEVER cross-Template. Include source Workout Session/Program/Season and planned-vs-previous comparison. |
| GA-EXE-010 | P0 | One-tap log completion with adjustable actual reps, hold durations, per-set resistance, RPE and notes; mid-workout insert/reorder blocks without rewriting templates. |
| GA-EXE-011 | P0 | End-of-block compact editable performance summary; deviations highlighted; session end includes effort RPE, **distinct session quality rating**, subjective journal summary, free notes, modification log and total/active/rest duration. |
| GA-EXE-012 | P0 | Workouts run offline; synchronize later. Missing media must not block execution. Automatic update/download for relevant exercise images; selective video caching; storage manager. |
| GA-EXE-013 | P0 | Configurable audio/call/headset interruption policies; monitoring of audio disruptions. Critical recovery: no automatic blind continuation after reboot/time-source discontinuity. |
| GA-EXE-014 | P0 | Accessibility: TalkBack/screen reader labels, font scaling, no color-only state, optional haptics, accidental skip/stop safeguards; larger buttons. |
| GA-EXE-015 | P2 | Tablet and smartwatch companion readiness, with phone-first implementation and adaptive layouts. |

## Web simulator, history and reports

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-WEB-001 | P0 | Web performs template authoring, history, import, corrections, analytics and **dry-run** simulation, but never logs a simulated run as a real session. |
| GA-WEB-002 | P0 | Simulator supports real-time and accelerated time 1×/2×/5×/10×, step forward/back to rep/set/block, audio cue preview, skip/reorder simulation, timeline, ETA and validation of impossible transitions. |
| GA-WEB-003 | P0 | Web simulator conforms to identical compiled execution-plan semantics as Android, with shared fixture expected timelines and randomized/state-machine tests where feasible. |
| GA-WEB-004 | P0 | Searchable history, session details, planned/actual/previous comparison, historical corrections without accidental template modification; export CSV. |
| GA-WEB-005 | P1 | Advanced analytics by template/exercise/variation across strength, reps, holds, manual timings, volume, frequency, RPE, quality/journal, durations and selectable date ranges; categorical band comparability never fabricated. |

## Data, sync, backup and migration

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-DATA-001 | P0 | Provider-neutral storage abstraction; initially user-connected Google Drive with visible human-readable YAML templates/exercises and app-managed structured JSON session/event records. User can edit template YAML directly in Drive. |
| GA-DATA-002 | P0 | Canonical schemas versioned, explicit structural IDs, external-edit validator with diff; invalid edits quarantined while last valid version remains available. Safe backwards/forwards data migration plans. |
| GA-DATA-003 | P0 | Storage folder default configurable, safe later relocation, personal/active and imported/source areas separated; backups **outside** main app directory; staging/production separate; scoped permissions. |
| GA-DATA-004 | P0 | Offline-first durable local writes; automatic retries and synchronization when possible; safe merging of nonconflicting edits, manual resolution of genuine conflicts with both records preserved. |
| GA-DATA-005 | P0 | One active execution owner per session with explicit synchronized handoff, precise state snapshot and conflict preservation. Different devices can access history; no simultaneous execution ownership. |
| GA-DATA-006 | P0 | Automatic versioned backups, pre-migration/import snapshots, preview diff, individual session/template restore, whole archive export; never silently replace newer data on restore. |
| GA-DATA-007 | P0 | Assisted, reviewable one-time migration of historical Google Sheets journal, source reference and uncertainty/incomplete entries preserved; no invented measurements; old journal read-only after verified cutover. |
| GA-DATA-008 | P0 | New annual Google Sheets journal with monthly tabs, rows = exercises, columns = dates, grouped warm-up/strength/stretch and compact reps/sets/resistance/hold, overall rating and notes; overview tab optional. |
| GA-DATA-009 | P0 | One-way canonical JSON → Google Sheets materialization, except protected third-party comments/notes; source session links, per-session export status, idempotent queued retries on Android/web; month/year selective rebuild. |
| GA-DATA-010 | P0 | Preserve stable session IDs, original planned template snapshot, revisions and execution-engine versions; history is never overwritten by template edits. |
| GA-DATA-011 | P0 | Direct device↔user cloud flow where feasible, no server-side copies of personal workout history. Backend only when independently justified. |
| GA-DATA-012 | P2 | Future OneDrive/Dropbox/iCloud/hosted adapters and optional end-to-end encryption without requiring architecture rewrite. |

## Privacy, sharing, AI and identity

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-SEC-001 | P0 | Separate application identity and storage-provider identity; email magic link and optional Google Sign-In, provider-neutral internal IDs; extendable Apple/Microsoft/Meta. |
| GA-SEC-002 | P0 | Invite-only private beta; offline use after sign-in, multiple devices/browsers, recoverable account and revocable storage access. |
| GA-SEC-003 | P0 | Private by default; user data export, account deletion and storage disconnect; principle of least privilege and no credentials or private data in Git. |
| GA-SEC-004 | P0 | BYOK AI provider selection per task with cost estimate, usage, configured provider fallback; AI strictly optional for imports; review/approve all generated changes; no AI in deterministic runtime engine. |
| GA-SEC-005 | P1 | Optional provider-managed AI and local-model connectors; optional client-side end-to-end encryption capability architecturally possible. |
| GA-SHARE-001 | P1 | Selective exercise/template sharing with independent local copies, optional version updates, private/shared/public visibility; no automatic active session or personal history changes. |
| GA-SHARE-002 | P1 | Physiotherapist/coach read-only selective template/date/session access, comments and proposed changes, explicit approval and revocation. MVP collaboration via derived Google Sheets journal. |

## Operations, product evolution and release

| ID | Priority | Requirement / verifiable outcome |
|---|---|---|
| GA-OPS-001 | P0 | macOS + Cursor, personal GitHub only, new private `gym-assistant` monorepo at specified path; automated preflight of effective auth, local author and remote; abort on mismatch. |
| GA-OPS-002 | P0 | Signed APK initially, Google Play internal/closed later; GitHub Actions CI, staging/prod isolation, auto-version/release notes, data-safe rollback, frequent phone/web testing. |
| GA-OPS-003 | P0 | Managed hosting initially but provider-neutral/self-hostable, personal infrastructure target ≤ €10/month excluding AI/domain, EU hosting preferred, infrastructure as code, monitored costs. |
| GA-OPS-004 | P0 | **Explicit approval for EACH new external service**; separate explicit approval before ANY billable resource; personal accounts only. Keep service inventory and credentials rotation docs. |
| GA-OPS-005 | P0 | Semi-autonomous agents inside agreed milestone; mandatory tests, small branches/PRs, approvals for major architecture/security/destructive changes, durable cross-agent handoff and ADRs. |
| GA-OPS-006 | P0 | M0 high-risk integrated feasibility prototype then vertical slices; each milestone runnable, testable with real/sanitized examples, documented and frequently phone/browser demonstrated. |
| GA-OPS-007 | P0 | Real-workout parallel acceptance period; verify historical migration, Sheets fidelity, offline recover/sync, battery/responsiveness before formally discontinuing old journal. |
| GA-OPS-008 | P0 | Local execution event logs, simulator repro, sync diagnostics, opt-in inspectable issue reporting; personal data excluded from automatic reports. |
| GA-OPS-009 | P0 | Initial English UI, Kotlin/Compose Android, TypeScript/React web and optional TypeScript/Node backend. No handwritten JavaScript or PHP. Final engine architecture determined by feasibility evidence. |
| GA-OPS-010 | P0 | Full repo-ready modular specification, numbered requirements, diagrams, ADRs, schemas, fixtures, issue traceability, agent startup prompt, changelog and compliance checklist. |
| GA-OPS-011 | P0 | Open-source-ready private monorepo; original private PDFs/real sessions outside Git; sanitized fixtures; explicit public license before publication. |

## Definitions and acceptance conventions

- **Must** / `P0` means a first personal-use release blocker, not necessarily M0 blocker.
- **Workout Session** requires a user-initiated Android execution. Web dry-run never creates one.
- **Immutable session start snapshot** records planned work; user changes produce separate execution events and recorded actuals, never retrospective template rewrites.
- **Personal training data** includes sessions, free text, images, PDFs, exercise notes and device telemetry; avoid inadvertent server logs or AI exposure.
- **Offline** does not imply guaranteed continued execution after OS forcibly kills a process; recover safely and disclose uncertainty.
- **Acceptance evidence** should include test ID, platform/device, scenario, expected vs actual and any unresolved defect. See `docs/product/traceability.csv`.
