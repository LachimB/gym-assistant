# User journeys and interaction contracts

This document expands user-facing decisions beyond the field-level requirements. These flows are not finished UI designs; agent must preserve behavior and request sign-off when changing UX meaningfully.

## 1. Create a template in the web editor

`Templates → New/import/duplicate → Edit sections → Add block → Select exercises/variations → Configure each set → Choose timed/manual phases → Configure explicit/automatic alternation → Validate → Simulate → Save new revision → Sync`.

- Both WYSIWYG and YAML views must roundtrip without silently destroying comments/unknown legitimate fields. Canonical YAML formatting and comment-preservation fidelity are a separate M3 design decision; do not promise lossless YAML comments without an implementation.
- Every exercise family, variation, block occurrence and set has stable IDs even if renamed/reordered. A composer must allow multiple occurrences of the same family in different blocks and sets with different intensity.
- Undo/redo applies to editing, not retroactively to completed sessions. Archive and compare current/archived versions; imported PDFs retain provenance.
- If an external Drive YAML edit occurs, show changed revision and detailed validation errors; retain last executable version. Never silently overwrite cloud edits.

## Optional Seasons and Programs (M1 foundation, first-release P0)

Web planning: `Seasons → optional Program participation → Program Template memberships → latest/pinned revision → overrides → preview effective plan → save structural revision`. A Training Template is also always accessible from an independent Training Library. Home Android shows current Active Season, multiple active participating Programs, active standalone Programs and recent/favorite Templates; direct library launch never mandates context. Activating/switching/completing a Season launches a confirmation workflow covering each affected Program and Season participation; any bulk pause/completion remains explicitly reversible where allowed. Seasons and Programs can be cloned; new IDs, Draft, dates CLEARED by default, and NO historical session cloning.

## 2. Select and execute on Android

`Training Library or active Season/Program → Choose Training Template version and optional context → Resolve Program overrides → Check local media → Review planned vs prior (prefer same Program, transparent same-Template fallback) → Start → Execution view`.

- Detailed view: concise name and next phase, phase countdown, rep/set counts, block progress, previous-performance disclosure, pause/skip/back/forward, expandable instructions/media, audio mode.
- Focus mode: large phase/time, large action controls, accessible high contrast; switch during run without affecting phase clock.
- Manual rep: stopwatch starts automatically or after user Start as configured; tap marks completion and stores actual duration; pause/free navigation appropriately logged.
- End of block: compact automatic actual-vs-planned summary, user-editable notes, resistance, RPE, advanced details when expanded.
- Training overview: jump among blocks and change order or insert new block mid-session, clearly labeled as session-specific.
- End Workout Session: active/rest/total, partial/skipped, planned vs previous, RPE, **independent personal session quality rating**, subjective journal summary, arbitrary notes and modification history. Offer explicit, selective promotion of planned-vs-actual differences to a NEW Program structural revision or shared Training Template revision, with scope/diff/approval; never silently change future defaults or history.

## 3. Interruption and recovery

`Phone call/Bluetooth loss/process restart → configurable pause/continue policy → local checkpoint → visible warning if timing uncertain → explicit resume → event log preserved`.

- No magical continuation after system reboot where monotonic anchor lost; show last validated point and ask whether to resume/restart phase or discard with confirmation.
- A session remains available offline. After reconnect: upload canonical session, resolve true conflicts preserving both, then queue Sheets projection; never mark completed sync while pending.
- Explicit cross-device handoff only when latest checkpoint confirmed; one active owner. Other devices may view the last synced record.

## 4. Web simulator and troubleshooting

`Open template / historical plan → Compile → Step/real-time/2×/5×/10× → Inspect timeline → Simulate edits/skips → Evaluate warnings → Exit`.

- Dry-run must NEVER create a real session, change last-performance data or trigger derived journal export.
- Replay recorded execution event logs when diagnosing user-authorized problem, with redaction before issue reporting.
- Exact planned events must match Android for identical plan/action inputs. Browser real time is a preview, not a precision guarantee after tab suspension.

## 5. History, journal and physiotherapist feedback

`Android completes → local committed JSON → provider sync success → journal job pending → annual spreadsheet month tab updated exactly once → export status`.

- Monthly tab groups exercise row names and session dates, supports 2+ sessions same day, per-set bands/pyramids and quality notes. Show previous session comparison and source links.
- Physiotherapist can comment on designated protected cells/rows/areas in shareable journal; a journal rebuild must not overwrite that space. Direct app coach feedback is post-MVP.
- Web historical corrections append revisions with provenance and trigger derived journal update; never pull Google Sheets workout-value edits into canonical sessions.

## 6. Import with and without AI

`Select source PDF → Extract content/images privately → (Optional explicitly approved AI provider/model) → Match exercise library / aliases / variations → Show confidence + page references → User edits → Draft template preview → Compare existing revision → Approve → Save`.

Uncertain values remain uncertain, never hallucinated. A cancelled import has no effect on live templates. AI failure never prevents manual import. Original source PDF remains private and retained in imported-data area.

## 7. Season/Program context in Google Sheets and reporting

Monthly journal layout remains exercise rows and session-date columns; add the captured Season and Program labels to each Workout Session header, or omit where unassigned. Never reconstruct historical attribution from current membership. Web summaries group by optional Season and/or Program and include unassigned sessions in global totals. A Program can contribute across multiple Seasons while remaining one identity.

## 8. Account/provider lifecycle

`Sign in email magic link or Google → (different) storage provider authorization → select/default folder → validate permissions → first sync`.

Disconnecting storage must not erase unsynced local work; account recovery and data export/reimport are separate from Google identity. Privacy export/delete must report scope/derived copies/offline devices/backups. Invite-only until release policy changes.
