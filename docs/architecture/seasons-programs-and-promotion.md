# Training Seasons, Training Programs and immutable Training Templates

**Binding:** Decisions #79–#97; see `docs/product/decision-register.md`. This is the authoritative domain extension to `docs/architecture/overview.md`. The two organization layers are optional; no user must create either to execute a Training Template. All new UI and code use the canonical names below.

## Canonical concepts

| Object | Purpose | Identity / immutability |
|---|---|---|
| **Training Season** | Optional time-bounded or open-ended organizational grouping | Stable `seasonId`, structural revision `revisionId`, separate metadata and lifecycle events |
| **Training Program** | Optional collection of reusable Training Template references and optional later scheduling | Stable `programId`, structural revision `revisionId`, global lifecycle independent from participation |
| **Season participation** | Explicit many-to-many Season↔Program association | Stable `participationId`; local `planned/active/paused/ended` status, audit trail |
| **Program Template membership** | Reference a Template from a Program; version policy plus limited overrides | Stable `membershipId`, `templateId`, latest/pinned policy, override targets anchored to stable IDs |
| **Training Template** | Reusable definition of sections, blocks, sets and repetitions/timed actions | Stable `templateId`; EACH committed edit creates new immutable `revisionId` |
| **Workout Session** | Actual Android execution | Stable `sessionId`; immutable start snapshot + append-only events and approved corrections |

```
Training Season (optional; maximum one Active across the account)
  -- participation --> Training Program (0+ Seasons; independent global status)
                          -- membership --> Training Template (0+ Programs; independent library object)
                                                Section (optional)
                                                  Block → Set → Repetition / Action
Workout Session (actual execution) --> immutable Training Template revision + compiled plan
                                  --> optional captured Season and/or Program context
```

**IMPORTANT:** These are references, not physical ownership or cloned data. A Template may be started directly without any context. A Program may stay Active without a Season, even while a Season is Active. A Program can span Seasons with continuous history. A new Workout Session can attribute a Season **only when it is Active at start**; attaching an active Program to that Season requires an active participation. A standalone launch records only Program even if another Season is active. A launch from library may record no context. There is no requirement to reassign historical sessions when organizational affiliations change.

## Lifecycles and explicit transition review

Seasons and Programs have global `draft → planned → active ↔ paused → completed | abandoned`, with explicit reopen transitions; `archivedAt` is independent. The Program↔Season participation has local `planned | active | paused | ended`. Global and participation statuses are different: pausing the Season can pause its participations while the Program may remain globally active. Only ONE Season can be `active` at a time; several Programs/participations may be active. A user can pause Season A then activate Season B. These constraints require *collection-level* checks in addition to JSON Schema validation.

Before changing an active Season, pausing/ending/completing a Season, or activating another Season, present an explicit **per-participation transition preview**: for every Program, choices to end participation, carry forward into new Season, keep active standalone, pause participation, or change global Program status as separately confirmed. Do not implicitly end a global Program when a Season is completed. Newly completed or abandoned Seasons keep their recorded provenance and session history. Suggestions based on dates are informational; no automatic completion. An already completed Program is not reopened by bulk action without explicit confirmation. Audit transitions/reasons and ensure changes are transactional or recoverable.

## Dates, metadata and cloning

Only localized name required for a Season/Program. Dates optional, may overlap, cross years or exceed parent Season. Goals, focus, notes, frequency/rotation, tags and visual hints optional. Cloning Season/Program creates new IDs, always resets lifecycle to `draft`, **clears start/end dates by default** (unless user explicitly supplies replacements), and never copies Workout Session history. Optionally clone included Programs and selected Templates as separate new identities; default to references to existing immutable Template versions, latest/pinned policies and overrides. Show complete preview and obtain confirmation before mutation. Record clone origin when convenient, but clone relations do not imply linked history.

## Versioning and references

- New committed Template edit → new immutable `revisionId`; never edit previous files. Restoring old revision creates a NEW revision. Stable occurrences/sets retained when semantics preserve identity; new structures get new IDs.
- Significant Season/Program membership, override, policy or participation structure change → new immutable structural revision. Minor text edits/lifecycle state changes → audited; captured display labels and revision IDs in sessions remain unchanged.
- `latest` Template reference resolves to most recent **published validated** revision; `pinned` uses specified immutable revision. Scheduled work may retain pinned revision. Running sessions never re-resolve.
- A Program membership may contain parameter overrides targeting stable `(blockId, occurrenceId, setId[, phaseId])`. Allowed: existing-set resistance/equipment, rep/side counts, duration/tempo/rest, and limited existing-set selection/repetition; NO arbitrary block/exercise insertions or reordering, no mutation of underlying Template.
- For a `latest` membership, new Template revision is adopted only if the full effective compiled plan validates. If overrides are incompatible/ambiguous: **continue with the last validated effective configuration**, keep pending update visible, show three-way comparison, do not discard overrides. Autoadoption creates auditable structural revision.
- Set selection and repetition during explicit interleaved order must be expanded deterministically, with unique **derived execution-instance IDs** linked to original source IDs. Never infer novel set structure silently. Ambiguous pattern expansion must fail validation until resolved. `setUsage` in draft schemas defines intent; exact expansion rules are an M2 compiler contract to specify and test with complex synthetic fixtures.

## Start a real Workout Session

1. Resolve optional Season/Program context. Active Season is unambiguous (max one), but **do not assign it to a standalone Program by default**. For multiple active eligible Programs, ask only if context is genuinely ambiguous. From a Program entry, context is explicit. Permit direct ungrouped Template launch.
2. Resolve specific Template revision and applicable Program membership overrides. Validate before use. If follow-latest upgrade is blocked, use last validated effective configuration and notify user.
3. Record immutable `organizationContext` with IDs, captured localized display names and structural revision IDs, membership/participation references, selected Template revision, override provenance and resolved plan snapshot. After startup, later Season/Program/Template edits cannot rewrite this record.
4. Android runs deterministic execution independently of the Season/Program navigation. Web can simulate any resolved configuration but never creates real sessions.
5. Strict prior performance lookup: same templateId, block occurrence, exercise occurrence/variation, set; FIRST prior same Program (across Seasons), FALLBACK prior same Template from other/no Program, with clear source disclosure and manual selection option. Never match by label, array index, or different Template.

## End-of-session review / promotion workflow

At completion, show planned vs actual. The user may select **individual deviations** and choose either:

1. **Program scope:** add/update the corresponding override(s) on a selected membership, create a NEW Program structural revision after validating effective plan; or
2. **Shared Template scope:** create a NEW immutable Template revision, with explicit notice that other follow-latest Programs may be affected; perform compatibility validation and required reviews.

Nothing is changed automatically; choosing a scope and accepting a preview are mandatory. Source Workout Session and historical Template and Program revisions remain immutable; record `promotionId`, source IDs, selected diffs, target scope, approvals, and result revision. Reject stale proposals when target revision has changed; require a fresh review. Never derive future default changes from RPE/journal-only differences unless selected and supported by schema.

## Analytics, Google Sheets, UI, sharing

- Home Android: current Active Season if any, all active participating Programs and separate active standalone Programs; library quick start, recent and favorites. Web: season/program planning, version comparisons, membership editing, cloning and organization drag/drop. Lifecycle operations supported in both. Web simulator never logs actual workouts.
- Filter history by Season/Program/Template/date; maintain ungrouped sessions in global analytics. Distinguish program all-time stats from season-specific participation. Basic counts, durations, training frequency P0. Advanced schedule adherence P1.
- Annual Google Sheets monthly exercise-by-date layout unchanged. Show captured Season/Program names in each Workout Session header; idempotent updates preserve third-party comments.
- Later sharing/coach workflows must include explicit privacy permissions; whole-Program public/community sharing is not automatically an MVP requirement. Backups/export include Season/Program revisions, participations, overrides and session snapshots.

## Formal references and contracts

- `schemas/training-season.v1.schema.json`
- `schemas/training-program.v1.schema.json`
- `schemas/season-participation.v1.schema.json`
- `schemas/promotion-proposal.v1.schema.json`
- `schemas/session.v1.schema.json` (`organizationContext` and template snapshot)
- `examples/seasons/`, `examples/programs/`, `examples/participations/`, `examples/promotions/`

Schemas are illustrative version-one artifacts. Domain-level checks (cross-record identities, one active Season, state transition guards, referential integrity, safe overrides, session context) must run beyond JSON Schema. Do not claim M0 validates full organizational runtime.
