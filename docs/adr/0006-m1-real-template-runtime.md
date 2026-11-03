# ADR-0006 — Validated local templates and session-only extra repetitions

- Status: **Proposed; owner review required before schema/runtime implementation**
- Date: 2026-11-03
- Requirements: GA-DOM-006, GA-EXE-001/002/008/012, GA-DATA-002/004/010,
  GA-WEB-003/004, GA-ORG-009/015/021; approved ADR-0001/0002/0003/0004/0005

## Decision requested, in plain language

Approve the next M1 slice: import a reviewed Training Template into a validated
local library, show the correct side and resistance on the phone, and allow one
extra timed repetition with one tap during an eligible set. Adding or skipping
work changes **that Workout Session only**. It never changes the source Template,
Program overrides, tomorrow's targets or previous sessions.

The already approved private preview remains the starting plan: fixed upper-bound
targets and the owner's specified reset/rest/order choices. This proposal does not
reopen those exercise decisions, publish private material in Git or authorize a
real-workout cloud upload. Private import stays local and uses deterministic
validation, with no additional AI operation/provider or external service.

This needs versioned Template/compiled-plan/history contracts and an additive
local database migration. Approval would cover implementation and tests, followed
by phone migration only after a new verified backup and successful automated
checks. It does not authorize converting the currently unfinished workout,
clearing data, automatic source adoption or automatic future-default promotion.

## What the owner would see

1. Import a Template and its exercise definitions, review validation and the
   complete execution preview, then explicitly publish the local revision.
2. Start that immutable revision directly, without requiring a Season or Program.
   Display captured exercise/variation names, left/right/bilateral cue, planned
   weight/bands and relevant source notes. Never guess a missing weight increment.
3. During an eligible timed repetition, tap **+1 repetition**. The app appends one
   matching repetition to the current set, before its following hold/rest/side,
   with the explicit reset countdown. The current countdown keeps running and
   the side stays the same.
   Repeated taps add separately identified repetitions in tap order.
4. Forward/skip keeps skipped work visible. Skipping a phase does not mark it as
   performed. Ending early retains partial timing/outcome and all adjustments.
5. History shows the original plan, session adjustments and reliable timing/events.
   Raw timer saves remain internal, exactly as required by ADR-0004.

## Explicit first supported repetition policy

A new optional set policy in Template1.1.0 declares
`inSessionRepetition: {mode: appendToCurrentSet, resetMs: <integer>}`.
No implicit reset duration; the reviewed target uses the owner's4-second choice.
The compiler validates the exact repeated phase unit and eligible contiguous set
sequence before enabling the button. Extended one-off holds, standalone rests,
manual phases and ambiguous/interleaved patterns do not gain an invented policy.
Those patterns remain product requirements; they are not claimed implemented here.

For this first policy, add is enabled during a regular timed repetition or its
explicit preparation/reset unit, while its insertion boundary is still ahead.
It is disabled once the set/side has ended, during a separate long hold/rest, or
while awaiting the recovery decision. No completed work is moved or erased to
make room for an addition. The preview makes eligibility visible before starting.
The insertion boundary is explicit in the compiled set policy and must still be
ahead; queued extras follow the original planned repetitions in tap order.

An extra unit copies the frozen set's timed work phases. Exactly one explicit
reset separates consecutive work units; an existing following reset is accounted
for without doubling it, and the reviewed reset before a separate hold/side is
retained. Unsupported or ambiguous boundaries fail validation. It
keeps the same side/resistance/source references. It does not duplicate the
following long hold, inter-set rest, opposite side or paired exercise. Existing
explicit full-set ordering stays intact. Paused additions stay paused; recovery
still needs an explicit resume. Existing Forward retains phase-level behavior;
a dedicated whole-repetition skip can only be added with an explicitly tested
unit definition, rather than guessing which phases count as a repetition.

## Versioned contracts

Keep the existing1.0.0 Template, plan1 and history1.0.0/1.1.0 readers/tests. No
reinterpretation of an existing frozen session under a newer engine.

| Contract | Proposed addition/change |
|---|---|
| Training Template1.1.0 | Optional explicit `inSessionRepetition` policy; source phase/step roles where required to distinguish work/reset/rest without guessing from names. Existing identity/order/side/resistance fields retained; imported edits create a new revision. Exact schema and synthetic examples reviewed before publication. |
| Compiled plan2.0.0 / engine `kotlin-timed-2` | Preserve block/occurrence/set/source-step references, captured side and structured resistance, repetition-unit boundaries, explicit roles and supported addition policy. Safe integer bounds, unique derived unit/phase identities and deterministic ordering; reject ambiguous expansion. TypeScript2 simulation has equivalent semantics. |
| Internal saved-start/checkpoint format2 | Frozen base plan/hash and source, engine discriminator, current effective-plan revision, active phase-instance/offset, completed execution-instance identities and separate observed/pause/gap counters. Decode format1 with engine1; do not silently convert it. |
| Human Workout Session1.2.0 | Frozen base source/hash, final effective-plan snapshot/hash, meaningful repetition-addition events with stable domain IDs/source references, timing that distinguishes original plan from adjusted plan, and existing outcome/uncertainty facts. No tick log, raw receipts, outbox IDs or internal plan-revision rows. |

The human timing object retains `plannedDurationMs` as the original plan duration
and adds `adjustedPlannedDurationMs` and `adjustedScheduledElapsedMs`. Base position
is explicitly nullable during inserted work; do not pretend an extra repetition
advanced the original plan. Observed running time, pause duration, known gaps and
unknown-gap count remain independent. A rewind may reduce a cursor, never the
recorded observed time. Do not derive actual repetitions or active/rest totals
unless the complete unit/attempt semantics and evidence support those fields.

A semantic `adjustmentId` belongs to the session's meaningful domain history.
It is independent of the private transaction operation ID. Derived added-unit
and phase-instance IDs are stable hashes of that domain ID plus frozen source
references. Existing units retain identity when later units are inserted; array
indexes are not performance identities. Retrying an accepted logical action
cannot add the same adjustment twice. Actual repeat/rewind attempts must remain
visible; no double-counting a planned target merely because a cursor revisits it.

## Local publication and persistence — Room2 → 3

Add only these internal records; preserve every existing execution, connection,
delivery and outbox row. Existing raw recovery records remain private.

| New record | Proposed content and constraints |
|---|---|
| `template_candidates` | `candidateId` primary key; private staged source paths and hashes; validation result/diff; intended Template identity, expected current published revision and explicit review status. Invalid/unsupported candidates remain quarantined and non-executable. |
| `template_revisions` | Composite primary key `(templateId, revisionId)`; immutable exact YAML/exercise snapshot, compiled base plan2 JSON/hash, supported format/engine versions and publication provenance/time. No in-place update. |
| `template_heads` | Template primary key, selected validated revision reference. Compare-and-swap expected head at publication; a stale review cannot replace a newer head. Keep last validated revision on any failure. |
| `execution_plan_revisions` | Composite primary key `(sessionId, structuralRevision)`; immutable effective plan JSON/hash and causing domain adjustment ID (absent for the base). FK to session start. Create only on structural change, never on each tick. |

Use the Android system file picker for the explicitly selected YAML/exercise
inputs, then copy them into private staging. No all-files permission, blanket
folder access or new persistent external-document grant. Parsing/validation is
local code; no AI provider receives private import contents.

Staged imports stay separate from published personal definitions. Private source
PDFs/associations are not automatically copied or interpreted by this slice. No
private source or generated real Template is packaged in a public fixture/APK
asset or committed to Git. New app-private imported/personal files are hashed and
written atomically; backup covers both files and database before phone migration.
An interrupted publication cannot expose an unvalidated head or lose its last
validated source. Recheck hashes, references, supported semantics and expected
head in the publication transaction; present the exact effective preview first.

Start freezes the explicitly selected published revision and optional genuine
organization attribution. No current Season/Program inference, context change or
future-default update. Existing Program membership overrides must validate against
the effective plan before use; unsupported context/policy is rejected visibly,
not stripped. This proposal does not implement Season/Program management.

For an addition, one transaction writes the new immutable effective plan revision,
semantic action/receipt, checkpoint revision and pending outbox intent. Do not
mutate the frozen base plan or recopy the entire effective plan on every tick.
Persist before acknowledging the tap; failed commits leave the previous state
and queued work authoritative. Local serialization plus revision checks reject
stale writers. Active-session handoff/fencing remains a separate open protocol;
this local change must not be represented as solving remote ownership.

## Compatibility and migration safeguards

- Keep existing format1/engine1 workout recovery fully functional, including the
  currently unfinished phone workout. Do not end/discard or convert it to test2.
- Additive explicit Room2→3 migration (and composed1→2→3 for older installs), with
  exported schemas and real SQLite tests. New tables start empty; old source,
  receipt/checkpoint/outbox and delivery binding values remain unchanged.
- Before phone migration, create and verify a new private persistent backup of
  version2 plus relevant app-private library files, outside active storage. Keep
  the existing version1 backup. No automatic restore, old-APK downgrade, clear or
  destructive migration fallback. Stop on backup/migration/integrity failure.
- Old histories remain readable in web. New1.2 histories require explicit version
  dispatch and independent base/effective hash and identity/timing checks. An old
  reader must reject an unsupported version rather than show plausible bad data.
- Keep the current live cloud sender's exact synthetic-only eligibility gate.
  Supporting real local execution does not grant permission to upload its source
  or session. Production template sync and real-session release remain open.

## Required evidence and approval boundary

Before implementation, owner approval is required by AGENTS.md rule4 for these
schema, persistence and execution changes. Approval authorizes implementing the versioned contracts consistently with the
behavior and fields above. Publish exact schemas/DTO mappings and synthetic
contract examples alongside the implementation for review. If those details
require different behavior or a wider data boundary, return for a concrete
decision rather than guessing exercise semantics.

Required tests: loader/quarantine/publication interruption/stale head; deterministic
Kotlin/TypeScript golden timelines; left/right/hold/reset/weight cues; one/multiple
adds, retry and failed commit, pause/recovery/skip/rewind/finalization; source and
future defaults unchanged; stable instance identities, safe limits/overflow;
engine1/2 coexistence across restart; original rows preserved by migration;
web1.0/1.1/1.2 truthful display and uncertainty; all supported emulator versions.
Then private preview comparison and physical local-workout acceptance, without
cloud upload. Keep scheduled and observed execution facts separate throughout.

Alternatives: changing remaining repetitions in the Template would rewrite future
defaults; editing the frozen base would destroy provenance; storing a whole new
plan in every quarter-second receipt would needlessly multiply local storage.
These are rejected. A fixed-plan-only slice could show side/weight sooner but would
leave the owner's explicit in-session addition requirement open, so it is not a
substitute for this decision or for M1 acceptance.
