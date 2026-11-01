# Approved decision register — Decisions 1–97

This list preserves the requirements interview and final review. Where a decision had a later amendment, the later specific instruction prevails. `docs/product/requirements.md` converts the decisions to testable requirement IDs.

| # | Approved decision |
|---|---|
| 1 | Guided workout execution plus flexible training log. |
| 2 | Reusable web-authored templates, flexible Android usage, readable YAML/JSON interchange. |
| 3 | Timed holds, reps/sets, left/right, intervals, circuits/supersets, coordination. |
| 4 | Hybrid Android current-exercise focus and full-workout overview. |
| 5 | One-tap adjustable logging of reps/resistance/RPE/notes and previous execution, multi-band combinations. |
| 6 | Resistance selectable per set, including pyramids. |
| 7 | Shared exercise library, centrally defined exercises and variations with template-specific config. |
| 8 | Minimal live view plus on-demand rich instructions and flexible timed/manual rep programs. |
| 9 | Per-block immediate transitions, timed rest or indefinite pause until manual action. |
| 10 | Configurable audio cues, voice, language/countdowns, global podcast ducking. |
| 11 | Screen visible/unlocked generally, screen-awake option; background/locked timers and notifications. |
| 12 | Detailed live phase screen, progress and next preview; pause/skip/back/forward by rep/set/block. |
| 13 | Training → optional section → block → set → repetition; composite rep-level alternating. |
| 14 | Composite block 2+ exercises and pyramids, automatic or explicit custom sequence. |
| 15 | End-of-block editable summary and deviation highlights. |
| 16 | Distinguish versioned template, actual session and previous performance; immutable history. |
| 17 | Full web visual + YAML editor, preview, archive, compare, Drive-hosted human-readable data. |
| 18 | Offline-first local persistence, auto-sync, unfinished-session recovery and sync status. |
| 19 | Human-readable template/exercise YAML, app-managed JSON sessions, separate media refs in user cloud. |
| 20 | Merge disjoint edits safely; preserve and manually resolve true conflicts. |
| 21 | Web for management/history/analytics; simulator only (no actual workout recording). |
| 22 | Rich web simulator with speed, step/jump, audio test, timeline and invalid-transition tests. |
| 23 | Intelligent, optional reviewed PDF import linked to originals and prior versions. |
| 24 | Exercise families/variations, independent history/comparisons, user-created variants. |
| 25 | Equipment library, location availability, bands + attachments/placements, kg/lb. |
| 26 | Strict previous-performance matching to template + block occurrence + exercise/variation + set, no cross-template fallback. |
| 27 | Manual per-rep stopwatch auto-next or manual start, times, pause and undo. |
| 28 | One unfinished session, autosave per rep/set/block, interruption recovery, mid-session modifications. |
| 29 | Session summary, duration, modifications, RPE plus distinct personal quality rating and subjective journal summary. |
| 30 | Cross-variation and exercise/template analytics without numeric assumptions for band colors. |
| 31 | Assisted historic Google Sheets import; source/uncertainty preserved; discontinue legacy sheet, create new automated shareable journal. |
| 32 | New journal resembles historic sheet: exercise rows, date columns, warm-up/strength/stretch, compact data. |
| 33 | Canonical session → Sheets one-way; third-party comments protected even on rebuild. |
| 34 | Initially personal, general-purpose multi-user-ready design; no hardcoded Vitalista/Google-user assumptions. |
| 35 | BYOS personal cloud in MVP, optional hosted storage later. |
| 36 | App identity independent from storage account; can switch storage account. |
| 37 | Private invite-only remotely hosted beta, GitHub CI, very low running costs, self-hosting and open-source readiness. |
| 38 | Technologies from requirements; Kotlin/TS/Java/Go/Python familiar; no handwritten JS/PHP. |
| 39 | TS/Node acceptable; Kotlin/Compose + TS/React tentative; versioned execution contracts. |
| 40 | Configurable interruption policy for calls, headset/Bluetooth and other events; never silently auto-resume after auto-pause. |
| 41 | Standard security initially, optional E2EE-ready; private by default, export/delete/revoke, selective sharing, local processing. |
| 42 | Optional AI for PDF import (MVP priority), authoring and history analysis; provider selectable; manual fallback; approval before edits. |
| 43 | BYOK initially, application-managed AI later; per-task model choice, usage/cost, provider fallback, future local models. |
| 44 | First release complete personal-use workflow; first-release priorities PDF AI import, Sheets export, full simulator, legacy migration. |
| 45 | English UI is sufficient, internationalization-ready; exercise/block/template names and instructions multilingual; voice independent. |
| 46 | Phone-first Android with future tablet/watch; portrait, light/dark, font scaling, full-screen mode. |
| 47 | Monotonic clock authoritative; audio not timing driver; no drift; disruption warnings. |
| 48 | Comprehensive automated/emulator/device testing; Vitalista-derived sanitized fixtures, virtual time, navigation, recovery, cross-platform equivalence, sync. |
| 49 | Local execution/sync logs, web replay, optional user-reviewed issue submission. |
| 50 | Versioned backups, pre-change backup, diff preview, granular restore and portable export. |
| 51 | YAML exercise/template definitions, JSON execution/history; versioned JSON Schema, stable IDs, external validation/diff and safe migration; Drive manual edit. |
| 52 | Evaluate shared Kotlin/JS engine early, independent conformance-tested Kotlin/TS acceptable; compiled plan, immutable snapshot, versioned conformance fixtures. |
| 53 | Signed direct APK first and Google Play beta later; CI, separate environments, auto updates where supported, rollback, release notes. |
| 54 | Android 12+, latest Android and background/battery compatibility tests and compatibility matrix. |
| 55 | Provider-neutral managed hosting initially, self-host path, target ≤€10 monthly infrastructure, EU preferred, IaC and cost monitoring. |
| 56 | Passwordless email magic links + optional Google Sign-In; independent storage auth, invite-only beta, multi-device, offline and recovery. Later amendment: Apple, Microsoft, Meta sign-in extensibility. |
| 57 | Single active execution owner per session, explicit synchronized device transfer, offline continuity, preserve conflicting branches. |
| 58 | Switchable detailed/focus mode, big targets, contrast, haptics, safeguards and accessibility. |
| 59 | Offline-ready images for relevant workouts, selective large videos, PDF extracted images, phase media, private, storage cleanup. |
| 60 | Share selectively with independent template copies and optional version updates; private/shared/public; physiotherapist and community eventual; never affect active workout. |
| 61 | Physiotherapist selectively reads, comments, proposes changes; approval and revocation; use Sheets for initial collaboration. |
| 62 | High-risk proof-of-feasibility followed by vertical slices; runnable milestones, CI, acceptance tests, real fixtures, docs; frequent personal phone/web tests. |
| 63 | Semi-autonomous agent within milestone; PRs, CI, small changes, frequent builds/previews, GitHub issues, release approval, durable handoffs. |
| 64 | Modular repository-ready specs + agent entry point, ADRs, traceability, diagrams, version history; support multiple agents. |
| 65 | Binding product requirements, flexible internal implementation; ADR/review gates; every new external service needs approval. |
| 66 | M0 integrated technical prototype: Android install, web simulator, composite fixture, timing/navigation, tests, architecture report; live Drive not acceptance blocker. |
| 67 | M1 incremental end-to-end flow: real template, Drive sync, Android record, web review, interrupted session recovery. |
| 68 | Short parallel cutover with real workouts; verify Sheets, migration, offline, recovery, performance; legacy sheet read-only after migration. |
| 69 | Private open-source-ready monorepo; real PDFs/records/secrets outside Git, synthetic fixtures, license before public. |
| 70 | Full ZIP starter: specs, bootstrap, initial agent prompt, schemas, fixtures, tests and checklist; macOS/Cursor personal GitHub, blank repo. |
| 71 | Automated personal GitHub preflight, repo-scoped Git identity, owner/remote/token checks, abort; `<private-path>`; `mobile-data-switch` only setup reference. |
| 72 | Working name Gym Assistant; temporary configurable branding; distinct dev/prod app IDs and OAuth; defer domain. |
| 73 | Configurable default Drive folder and relocation; personal vs imported data separated, backups outside active directory, stage/prod separation and least privilege. |
| 74 | Google Sheets one file per year, monthly tabs, current tab default, comments protected, selective rebuild, session links, optional overview. |
| 75 | Hybrid direct cloud access as practical, provider-neutral, no server-side history copies, automatic retry for sync/Sheets. |
| 76 | Android/web coordinated idempotent Sheets updates, online sync trigger, protected comments, status and rebuild, persisted offline queue. |
| 77 | Guided authorized service setup; service purpose/cost/scope and personal account safeguards; separate explicit consent before **any billable resource**. |
| 78 | Architecture and milestone roadmap approved; create complete starter package; explicitly identify design omissions. |

## Precedence

When a decision appears to conflict with platform constraints, **do not silently interpret feasibility as guaranteed**. Raise an ADR and an approval request if binding product behavior must change. Example: precise ongoing timers after Android process termination and cross-device continuation must distinguish guaranteed vs best-effort behavior.

## Addendum: approved decisions #79–#97 (2026-11-01)

**Canonical naming overrides older informal terminology:** `Training Season`, `Training Program` (formerly Training Plan), `Training Template` (formerly reusable Training Session), and `Workout Session` (actual execution). The previous 78 decisions remain binding unless a specific later decision refines one. The Season and Program layers are ALWAYS optional. No change to the underlying Section → Block → Set → Repetition/Action workout semantics.

| # | Approved decision |
|---|---|
| 79 | Training Program is a collection of referenced Training Templates, with optional scheduling/rotation; no schedule required. Canonical names above, applied to all new UI and code. |
| 80 | Programs and Seasons can be cloned; Templates are reusable, and a Template is not inherently owned by a Program/Season. Program identity/history is continuous. Each committed Template edit creates a NEW immutable Template revision; each session references the precise revision. Initial one-Season-per-Program membership assumption is **superseded by #89**. |
| 81 | Season/Program require only name; optional dates (overlapping/cross-year), descriptions, notes, goals, focus, frequency/rotation, tags/visual identifiers. Program dates/lifecycle independent of Season. |
| 82 | Explicit state changes with suggestions, including suggestion to complete after an end date. `draft`, `planned`, `active`, `paused`, `completed`, `abandoned`; `archivedAt` is independent. Audit transitions; reopen permitted. Season-level actions can cascade only after preview and confirmation. |
| 83 | Program→Template membership version policy per link: `latest` or `pinned`; completed sessions freeze exact Template revision and execution-plan snapshot. Diff, notification, rollback/reuse older revisions, and version pinning for planned workouts. |
| 84 | Hybrid Android navigation: quick-start Training Library and Season/Program organization; active Season and multiple active Programs on home screen; favorites/recent/search/filter; direct unassigned training. Web provides richer planning and drag/drop. |
| 85 | Optional Season/Program historical provenance in session; web filters and basic Program/Season counts/duration/frequency; ungrouped sessions count globally. Show Season and Program in annual Google Sheets workout header, preserving monthly exercise-by-date layout. |
| 86 | First daily-use release includes basic Season/Program create/edit/clone, memberships, version policies, statuses, archive, hierarchy/home, session provenance and Sheets labels. Advanced recurring scheduling/goal analytics later. |
| 87 | Configurable cloning: create new IDs and `draft` state; optionally clone nested Programs; by default reuse Templates and preserve memberships' latest/pinned policies; optionally create independent Template copies; never copy actual sessions. Preview/confirm. **Clear all cloned start/end dates unless the user explicitly sets new dates.** |
| 88 | At most one Season globally `active`; pausing the Season permits activating another. New session attribution may reference only the active Season. Program context must be active and participating in that Season when seasonal. Both associations optional, no redundant prompts, historical context never silently reassigned. |
| 89 | Programs can participate in multiple Seasons over time with ONE stable Program identity/history. Activating a new Season must explicitly handle prior Season (pause/complete/abandon as applicable) and each Program (carry forward/end participation/retain standalone/etc.) with review/confirmation. Android execution-first; web planning-first; organization lifecycle available both. |
| 90 | Standalone Programs may be active alongside an active Season. A Workout Session from a standalone Program has Program context but **no Season**; active standalone Programs shown separately; future membership must not rewrite history. |
| 91 | Program global lifecycle independent from per-Season participation statuses (`planned`, `active`, `paused`, `ended`). Completing a Season asks what happens to EACH participating Program; never silently complete Programs. Participation history audited, historical sessions retained. |
| 92 | Immutable structural revisions for meaningful Season/Program changes (memberships, version policies, overrides, participation structure); minor metadata changes audit logged. Historical sessions retain IDs, display names and applicable structural revisions. Compare revisions; restore as NEW revision. |
| 93 | Previous performance: strict same Template ID + stable block occurrence + exercise occurrence/variation + set; prefer same Program history across Seasons; if absent transparently fall back to same Template under another Program/unassigned. Show source session, Program/Season; allow manual source selection; never cross-Template fallback. |
| 94 | Program–Template membership can contain parameter overrides without changing base immutable Template. Effective plan is resolved and previewed with parameter provenance; Program revision on override changes. Clones retain overrides. |
| 95 | For `latest` memberships, automatically accept only compatible, fully validated Template revisions. On ambiguous/invalid overrides, retain last validated effective config, show notification and three-way diff; no silent drop. Historical effective configs remain available. |
| 96 | Program overrides: equipment/bands/weights, rep/side counts, timed phase/tempo/rest, per-set pyramid values; limited selection/repetition of EXISTING set patterns. Cannot add exercises, reorder blocks arbitrarily or alter block/exercise composition. Preserve source IDs and derived-instance traceability; effective sequence preview/validation. |
| 97 | Completed Workout Session review may explicitly promote SELECTED planned-vs-actual deviations into a Program-specific override revision or a NEW shared Template revision; no automatic changes, no in-place Template edits, no mutation of historical sessions. Show scope and preview before confirm. |

**Override and transition precedence:** #89 supersedes #80's initial single-Season membership; #91 refines #82; #93 refines #26; #94–#96 refine #16/#83; #97 adds approved post-workout promotion. Do not silently resolve remaining implementation questions; record proposals in ADRs.
