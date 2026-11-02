# ADR-0004 — Lossless M1 Workout Session projection

- Status: **Proposed — owner reviewing; not approved for implementation**
- Date: 2026-11-02
- Requirements: GA-DATA-002/004/010, GA-EXE-008, GA-WEB-004; ADR-0001/0003

## Problem

The approved Room version-1 store retains frozen source/plan, every committed
action and receipt, scheduled cursor, observed running time, pause time, measured
unobserved gaps and a count of unknown restart gaps. The draft canonical session
1.0.0 timing object cannot express all of these. It also cannot distinguish a
partial finalization from reaching the planned end without interpreting an
unspecified event detail. Dropping these facts would weaken accepted recovery
semantics. Putting mutable checkpoints into the immutable source snapshot would
misrepresent provenance.

## What this would change for the owner

A saved history entry would explicitly distinguish a finished plan from an
early-ended workout, and show observed exercise, pauses and unobserved gaps
separately. For example, a session with 12 seconds on the scheduled cursor,
8 seconds actually observed, 5 seconds paused and one unknown restart gap would
retain all four facts; it would not become “12 seconds exercised.” This example
is synthetic. The phone database and existing saved sessions would stay intact.

## Proposed decision

Add a separately versioned canonical **session 1.1.0** contract, retaining the
existing 1.0.0 contract and fixtures unchanged. This is a read-only projection of
the existing store, not a Room migration or a new recording engine.

1. Keep canonical identity/start/source fields, the existing engine version and
   exact current plan hash. `sourcePlanSnapshot` contains only the immutable
   SessionStart data, including the frozen YAML, exercise definitions and plan.
   Do not infer Season/Program context for existing ungrouped sessions.
2. Add required `recording` with `formatVersion: 1`, local `revision`, the exact
   final `checkpoint`, and ordered `actions`. Each action includes sequence,
   operation ID, expected revision, original intent, emitted engine events and
   the committed post-action checkpoint. Preserve original JSON strings where
   exact bytes matter; do not parse/re-encode and claim their hashes unchanged.
   This explicit, versioned audit representation includes ticks without engine
   events and recovery/discard/early-end actions. It is not portable credentials
   or provider metadata.
3. Add required `completionKind`: `unfinished`, `plannedEnd`, `partial` or
   `discarded`. Existing top-level status remains `inProgress`/`paused`/`completed`/
   `discarded` as appropriate; `completed` means finalized and must always be
   displayed together with completionKind. Partial never means all planned work
   was performed. Determine completion kind from the audited final action and
   checkpoint, not the current cursor alone.
4. Retain separate `scheduledElapsedMs`, `observedRunMs`, `pauseMs`,
   `unobservedGapMs`, `unknownGapCount` in the checkpoint. The existing optional
   aggregate timing/per-set performance fields are omitted until their exact
   definitions can be computed reliably. Completed phase count is not a rep
   count. Unknown gaps never become fabricated milliseconds or exercise.
5. Keep semantic `events` as a deterministic convenience projection of genuine
   recorded engine events. Stable IDs derive from session ID/action sequence/
   event ordinal; event timestamps are the action's recorded wall-clock instant,
   never fabricated per-phase instants. Preserve the original engine event in
   detail and all original counters. No invented note events to hide raw actions.
   The required audit recording is the lossless source for web reconstruction.

## Reader and integrity rules

- Obtain start, checkpoint and the complete action prefix in one Room read
  transaction; last sequence must equal exported revision. Do not mix revisions.
- Validate IDs, schema/format/engine versions, start/source/hash integrity,
  contiguous sequence, expected revisions, unique operation IDs and every
  transition/receipt against the accepted deterministic reducer. Fail closed on
  corruption, unsupported formats or an incomplete prefix. Do not repair history
  silently. A duplicate operation cannot create another exported action.
- Build export deterministically from that revision; exclude generated export
  timestamps so retrying the same revision returns identical content. Preserve
  original local data on every export error. No UI may claim synchronized merely
  because projection succeeds.
- Readers explicitly dispatch 1.0.0 versus 1.1.0. Never rewrite old files or
  relabel 1.1.0 content as 1.0.0. Unsupported versions remain visible as unsupported.

## Scope and limits

No local database/data migration, deletion, phone data export, real-data cloud
upload, OAuth permission change, runtime repetition insertion, plan-format change,
remote overwrite, multi-device handoff or last-write-wins policy is authorized by
this ADR. Those need their own concrete implementation review where required.
Provider IDs, credentials and acknowledgements remain separate adapter metadata.
Drive's write preconditions and fencing are still unproven and must not be
presented as solved by this projection.

## Validation before merge

Synthetic replay cases: start/tick/pause/resume, navigation without invented
completion, known and unknown recovery gaps, natural completion, early partial
finalization, discard, duplicate operation, missing/reordered/corrupt receipts,
snapshot/hash mismatch, unsupported versions and byte-identical same-revision
retry. Validate emitted JSON against the new schema and compare reconstructed
counters with the source store. Demonstrate that 1.0.0 fixtures remain valid and
unchanged. Real-workout contents and source documents never become test fixtures.

## Issues identified during revision review

The owner clarified on 2026-11-02 that they want to read the ADR before deciding,
not that they have specified design changes. The following concerns were found
by the agent during self-review and are open for review:

- Resolve whether full per-tick internal receipts belong in every canonical
  session, a separately linked audit file, or only a portable diagnostic export.
  The current proposal duplicates semantic events and internal actions; that has
  storage, privacy and implementation-coupling costs. Do not silently drop facts
  whichever representation is selected.
- Bound transfer behavior. A growing full recording sent after every 250 ms tick
  would cause excessive repeated uploads. A projection is not an instruction to
  upload every outbox revision independently. Define coalescing/acknowledgement
  behavior separately, preserving the local audit history and pending state.
- Specify stable event IDs within the existing length limits and exact mappings
  for recovery events and session-wide events without a phase index. Avoid
  assigning a fabricated plan step just to satisfy the older schema.
- Separate independent canonical semantics from Kotlin internal JSON wherever
  possible. The existing hash algorithm must remain verifiable from retained
  bytes, rather than becoming an undocumented cross-language serialization rule.

These are review findings, not newly approved decisions. No schema or database
change has been made.

## Alternatives

- Serialize only current draft fields: rejected because pause/gap/action evidence
  would be lost or ambiguously hidden in snapshots/events.
- Change Room first: unnecessary for a pure projection of already recorded facts.
- Replace the canonical contract with raw database JSON: rejected because the
  session envelope still needs stable versioned domain identity and explicit
  reader semantics. Raw internal rows alone are not a synchronization protocol.
