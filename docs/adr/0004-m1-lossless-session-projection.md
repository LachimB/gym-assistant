# ADR-0004 — Human-readable Workout Session history and private recovery records

- Status: **Proposed — human/internal separation confirmed by owner; full schema not yet approved**
- Created: 2026-11-02; revised: 2026-11-03
- Requirements: GA-DATA-002/004/010, GA-EXE-008, GA-WEB-004; ADR-0001/0003

## Owner's decision

On 2026-11-03 the owner clarified: human use needs meaningful workout events.
If recovery requires saving every quarter-second tick, those saves should remain
internal and separate from the session recording used by people. This resolves
the earlier question about including all internal receipts in canonical history.
It does not approve every remaining schema detail or a database migration.

## What the owner sees

A Workout Session contains the plan used, meaningful events and reliable timing
and performance facts. Examples: workout started, repetition/phase completed,
pause/resume, skip/rewind, an actual change such as an added repetition, an
interruption/recovery, and planned or early completion. Events are recorded only
when supported by committed evidence; this list does not claim all actions are
already implemented.

The history distinguishes scheduled position, observed exercise, pauses, measured
unobserved gaps and gaps of unknown duration. For example, 12 seconds on the plan
cursor, 8 observed seconds, 5 paused seconds and one unknown restart gap remain
separate facts. This synthetic example must never display as “12 seconds exercised.”

## Two separate representations

| Representation | Purpose | Contents and consumers |
|---|---|---|
| Canonical Workout Session | Human history, web review and ordinary session synchronization | Frozen workout provenance, meaningful events, timing/performance summaries, explicit partial/completed outcome; independent of Android database classes |
| Private execution/recovery records | Reliable local saving, recovery, integrity checks and retry bookkeeping | Ticks, checkpoints, commands, transaction receipts, operation IDs and local outbox state; consumed by implementation machinery |

This is data separation, not merely hiding thousands of tick rows in the UI.
Ordinary synchronized session files must not embed the raw tick log, duplicate
internal receipts under event details, or require a second internal audit file
for a history reader to understand the workout. Human CSV/export/history views
follow the canonical representation.

Internal records may be retained as needed for correctness. This ADR does not
introduce their deletion, retention limits, cloud upload or a diagnostic-export
feature. Any future technical backup, diagnostic export or device-handoff format
must be explicitly separate from human history, with appropriate access and
privacy handling. Existing recovery guarantees remain binding.

## Proposed canonical contract revision

Use a separately versioned session 1.1.0 contract, retaining existing 1.0.0 fixtures
and readers. This is a read-only projection from committed local records; it does
not change the Room database or recording engine.

1. Retain stable domain identity, start instant/time zone/local date, the exact
   frozen source/compiled plan and existing engine/hash provenance. Capture only
   genuine start provenance; do not copy the whole internal SessionStart or a
   mutable checkpoint wholesale into source snapshots. Do not infer Season or
   Program attribution for ungrouped sessions.
2. Make meaningful events the canonical history. Use portable domain fields and
   stable event IDs. Session-wide events may lack a plan-step reference; never
   invent an exercise or step just to satisfy the older draft's required field.
   Represent pause/resume, known/unknown interruptions and early finalization
   explicitly rather than smuggling them into a raw internal action payload.
3. Preserve separate scheduled-position, observed-running, pause, measured-gap
   and unknown-gap-count facts in a versioned domain timing summary. Quarter-second
   ticks update those totals internally but do not become human events. Do not
   claim observed running time is exclusively active exercise: it may include
   planned rests. Active/rest totals and per-set performance are published only
   when reliably derived; completed phases are not automatically completed reps.
4. Add an explicit completion classification: unfinished, reached planned end,
   ended early, or discarded. Reaching the planned end after skips does not mean
   all planned repetitions were performed. Preserve those skips in history.
5. Canonical readers use events, summaries and frozen source information. They
   do not replay Kotlin timer commands or depend on Room transaction receipts.
   Diagnostic machine-replay fidelity is an internal concern; preserving human
   facts does not require exporting every implementation step.

Exact field names, required/optional fields, event types and version dispatch
remain part of the proposed schema review. No schema implementation is implied
by the owner's confirmation of the human/internal separation alone.

## Projection and integrity rules

- Read the start, checkpoint and committed action prefix in one local transaction.
  Check for incomplete/corrupt history and verify source/hash integrity internally.
  Never mix revisions or silently repair recorded facts.
- Internal commands that produce no meaningful domain change produce no human
  event. Conversely, user actions such as ending early must be represented even
  if the current reducer emits no engine event for them. User-requested deviations
  belong to the Workout Session and do not rewrite its source Training Template.
- Derive stable domain event IDs without exposing internal receipt structure or
  exceeding ID length limits. Use original commit timestamps with their real
  precision; do not fabricate exact wall-clock times for earlier transitions
  observed together in one later timer sample.
- Preserve exact hash-input bytes/algorithm provenance internally or in the
  appropriate frozen plan representation. Do not rely on another language
  accidentally reproducing Kotlin serialization.
- Reprojecting an unchanged revision is deterministic. Ticks without a meaningful
  event or timing change must not create a duplicate human event. Internal
  revision/acknowledgement metadata remains distinct from domain history.
- Synchronization can coalesce superseded pending projections while preserving
  the local audit history and safe acknowledgements. A 250 ms local save is not
  an instruction to upload a growing full session every 250 ms. The precise
  outbox/acknowledgement protocol needs separate implementation review.
- Projection success does not mean synchronization success. Provider IDs,
  credentials and acknowledgements remain adapter metadata. Unknown gaps never
  become invented milliseconds or completed work.

## Validation before merge

Use synthetic cases for timed progress, pauses, navigation, skipped work,
known/unknown recovery gaps, natural completion, partial completion and discard.
Verify that frequent ticks add no tick events to human history while accurate
summaries survive export. Compare canonical facts with the committed source
store, including events for meaningful actions with no reducer event. Test stable
IDs, deterministic retry, corruption/unsupported formats, snapshot/hash mismatch,
and unchanged 1.0.0 fixtures. No private workout or PDF becomes a test fixture.

## Scope and remaining limits

No database migration, record deletion, real-workout cloud upload, OAuth expansion,
runtime repetition insertion, new plan format, remote overwrite or multi-device
handoff is authorized by this ADR. Drive conditional writes, writer fencing and
conflict preservation remain separate unresolved synchronization work.

The owner-approved private workout preview is independent of this schema review.
It is not yet imported or executable on Android.

## Alternatives

- Put all internal receipts in every canonical session: withdrawn following the
  owner's clarification; duplicates information and couples human history to
  internal machinery.
- Require a linked raw audit file for ordinary history: unnecessary for human
  use and adds another consistency dependency.
- Export only the existing draft timing fields: insufficient to preserve pauses,
  partial completion and unknown gaps clearly.
- Replace local recovery records with human events alone: outside this proposal;
  keep implementation records needed for reliable local behavior.
