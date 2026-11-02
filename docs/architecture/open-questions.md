# Design gap register — audit of what is missing or unproven

**This register is the explicit answer to the user's request to identify omissions.** None of these is permission to change agreed product behavior silently. `BLOCKER` requires early technical evaluation; `MVP` requires resolution before daily-use release; `FUTURE` can remain configurable.

| ID | Severity | Missing decision, ambiguity or external constraint | Proposed resolution and milestone |
|---|---|---|---|
| GAP-01 | **BLOCKER** | `drive.file` grants are per-file/user-approved, while users may select/relocate visible folders and edit externally. Folder selection may not grant automatic access to child files. | M0/M1 Drive OAuth + Picker spike; prove create, list, edit, externally changed YAML, move, optional custom folder; propose least-privilege scopes before approval. |
| GAP-02 | **BLOCKER for production reliability; M0 basic smoke passed** | Android background/locked execution, audio TTS, foreground-service type, notification permissions and OS process death cannot be guaranteed uniformly. | Pixel 10a / Android 17: tested APK installed; the user reported short running/paused screen-off, pause/resume, and navigation passing. Process termination restarts cleanly but loses progress; persistent recovery is an M1 gate. No foreground service or real audio. Short numerical foreground timing baseline recorded with capture uncertainty; final M0 approval remains pending. Long-duration/other-OS/audio behavior remains unproven. See `docs/milestones/m0-device-notes.md`. |
| GAP-03 | **BLOCKER** | KMP-to-React bridge may create heavier integration than two conformant engines. | Measured in M0. Kotlin/JS builds and runs under Node, but the output is not a clean Vite import. ADR-0002 keeps separate Kotlin and TypeScript engines against the versioned JSON fixtures. |
| GAP-04 | **MVP** | Concurrency semantics for one active session while first device is offline, and explicit transfer / fencing on Drive. | M1 ownership token, expirations/checkpoints, conflict branches and no-silent-takeover acceptance tests. |
| GAP-05 | **MVP** | Multi-device idempotent Google Sheets updates and preservation of **threaded comments** vs cell notes without a coordinator. | M4 tested ownership/outbox protocol; dedicated protected feedback ranges; prove actual Google Sheets API behavior, document unsupported comment types. |
| GAP-06 | **MVP** | BYOK in browser: storing provider API keys securely and whether providers support direct browser CORS without unsafe exposure. | M3 threat model and per-provider feasibility; recommend ephemeral browser consent or separately approved gateway, no persistent plaintext. |
| GAP-07 | **MVP** | Exact OAuth scopes, consent-screen configuration and provider approval/verification for private beta vs broader public launch. | M1 approval-backed setup spike; least privilege and separate staging/prod OAuth; record verification and quota requirements. |
| GAP-08 | **MVP** | User account recovery, changed storage account and restoration when storage grants are revoked or accounts are lost. | M4 document export/reimport and encrypted secrets recovery; test revocation without destroying local state. |
| GAP-09 | **MVP** | How exercise family variation and per-set variation combine, and how a rep-level alternate order interacts with skipped/inserted blocks. | M0/M2 normative state machine and schema extension; golden fixtures for variation per set and non-linear edits. |
| GAP-10 | **MVP** | Real PDF import accuracy varies by source and intellectual-property rules; source PDFs may not be available to every agent. | M3 user-reviewed import provenance and privacy/rights policy; synthetic PDFs for CI; keep original private. |
| GAP-11 | **MVP** | Protection from accidental Google Drive folder deletion: backups in separate Drive folder share same account/permission failure domain. | M4 backup recovery drills and optional future second-provider backups; do not claim independence from compromise. |
| GAP-12 | **MVP** | Retention/deletion semantics, including derived Sheets, shared copies, offline device records and backups, require specificity. | M4/M5 retention policy + data map, explicit irreversible deletion UX and privacy notice. |
| GAP-13 | **MVP** | Cost ceiling does not identify approved hosted auth/email/AI gateway providers; free tiers and geographic hosting could change. | M0/M1 shortlist with provider costs and approvals, explicit billable-resource gate. |
| GAP-14 | **MVP** | Fully deterministic playback of old sessions needs event schema for actual manual timing, undo/reorder and clock-anchor reset. | M2 durable append-only events, engine/version provenance and simulated replay test pack. |
| GAP-15 | **MVP** | Multi-language fallback rules for exercise family vs variation vs block/phase naming and speech voice installed availability. | M2/M3 localized field fallback chain, voice-missing behavior, imported source retention and tests. |
| GAP-16 | **MVP** | Manual edit of YAML may reorder arrays, change IDs or introduce conflicts during an active session. | M1 validator + optimistic concurrency, detect ID changes and orphan refs, keep last known-good plan until reviewed. |
| GAP-17 | **MVP** | Performance targets (maximum timeline error, cold-start, battery impact, memory, media cache size) were not quantified. | M0 baseline timing tests; M5 propose measurable thresholds for user acceptance on actual phone; do not fabricate acceptance values. |
| GAP-18 | **MVP** | Home-location and location-specific equipment storage data structure not fully specified; no location sensing requirement. | M3 schema for named training locations and equipment availability; do not request GPS permission without approval. |
| GAP-19 | **MVP** | Authentication on a privacy-first BYOS application adds dependency even if user only wants local workouts. | M1 decide whether optional local/offline guest workflow is feasible, without weakening chosen email/Google login; document account-deletion behavior. |
| GAP-20 | **FUTURE** | Stable final product ID, name, public domain, Play Store publisher and open-source license not chosen. | Keep placeholders distinct for stage/prod; choose before Play/public releases; no unapproved purchases. |
| GAP-21 | **FUTURE** | Detailed collaboration/public moderation, abuse handling, licensing and version distribution not yet designed. | P1 community milestone, not M0–M5 blocker unless sharing scope changes. |
| GAP-22 | **MVP** | No explicit UX choice for multiple workouts completed on one date in monthly tab. | M4 ensure stable session-ID column mapping and labeled multiple sessions/day. |
| GAP-23 | **MVP** | Explicit account data encryption, OAuth token caching, web offline support, consent revocation and accidental server logging need threat model. | M1/M5 device secure storage, token lifecycles, audit, no history backend caches. |
| GAP-24 | **MVP** | Accessibility acceptance beyond font scaling (TalkBack focus order, animations, exercise hands-free mode) not yet measured. | M5 platform accessibility test plan; invite targeted user validation. |
| GAP-25 | **MVP** | Session metadata such as timezone, DST, dates, duration semantics and multiple training locations may create wrong journal placement. | M1/M4 store UTC timestamps + IANA timezone + local session date; test DST and multi-session days. |
| GAP-26 | **MVP** | Exact definition of user quality scale, per-exercise RPE and journal fields not yet defined, though all must be distinct. | M2 define configurable scale/version and preserve old import values without reinterpreting. |

## Platform sources behind critical gaps

- Google Drive `drive.file` scope: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Google Sheets OAuth: https://developers.google.com/workspace/sheets/api/scopes
- Sheets cell notes and update fields: https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/cells
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android alarms and elapsed realtime: https://developer.android.com/develop/background-work/services/alarms
- Kotlin/JS and Kotlin/Wasm trade-offs: https://kotlinlang.org/docs/web-overview.html

## Resolution policy

Document each gap in an issue and link acceptance tests. If a solution changes a binding requirement, require explicit user approval, update the decision register/ADR and changelog, and migrate the traceability table. **Do not automatically 'resolve' a gap by removing functionality.**


## Organization domain implementation risks after Decisions #79–#97

These are validation tasks, not permission to change accepted behavior:

1. **Explicit composite order + limited set usage:** determine exact deterministic rewrite rules for excluding/repeating existing set patterns, especially interleaved per-rep composites. Reject unsupported/ambiguous cases rather than reorder silently. Validate on synthetic fixture variants in M2.
2. **Concurrent structural revisions:** specify optimistic concurrency, immutable revision ordering and replay of Program membership changes, participation transitions and promotional proposals across offline devices. A stale expected revision must fail safely.
3. **Revision provenance:** decide whether a Program structural revision includes participation membership or each participation has its own immutable version; both must remain attributable and auditable. Starter schema separates participation records and structural Program revision.
4. **Template revision identity:** decide stable ID retention vs new IDs on semantic replacement of exercises/sets, including compatibility test for both overrides and previous-performance matching.
5. **Season transition atomicity:** pause/complete old Season, activate new Season, and per-Program carry-over must be transactional or reversible; cannot temporarily leave two active Seasons after device sync.
6. **Promotion scope conflicts:** atomic publication of Template revision vs Program revision, validation and notifications to other follow-latest Programs; stale proposal guard and approval audit.
7. **Organization import:** historical legacy journal cannot be assigned Season/Program based only on dates; user review required, preserve unassigned records where context is unknown.
8. **Selection context UI:** starting directly from Library or a standalone Program must not silently inherit active Season; no extra prompt when Program/Season selection is already unambiguous.

Refer to `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001.
