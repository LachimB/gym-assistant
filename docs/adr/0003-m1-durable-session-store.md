# ADR-0003 — M1 durable Android session recording and recovery

- Status: **Proposed — user approval required before adopting the persistence design**
- Date: 2026-11-02
- Owner: project owner
- Requirements: GA-EXE-008/012/013, GA-DATA-004/005/010, GA-ORG-009/015/021
- Builds on: accepted ADR-0002; independent Kotlin/TypeScript engines remain.

## Problem

The accepted M0 Android app keeps `ClockedExecution` in memory. Process termination
loses the unfinished workout. M1 must persist an immutable start snapshot, events,
checkpoint and pending sync work before reporting a successful action. Starting
another workout must not silently replace the unfinished one. A restart must not
count the time since the checkpoint as exercised time.

This is a new durable storage boundary, not an extension of the volatile Python
contract experiments. Project rule 4 requires approval for major architecture
changes; this proposal makes the decision reviewable before implementation.

## Proposed decision

Use a device-private SQLite database accessed through Android Room, behind a Kotlin
session-store port. Keep database entities separate from canonical portable JSON
session contracts and the provider-neutral cloud interface. Room is an on-device
library, not an external service; this approval would authorize no cloud resources,
OAuth setup, billable activation, schema migration of existing user data or upload.
Exact compatible dependency versions will be verified before installation.

One database transaction commits each accepted action's new events, derived
checkpoint/revision and outbox intent. A failed commit leaves the last committed
state authoritative and visibly reports a save failure; no completion confirmation
is displayed for a failed write. Serialize actions through one local execution
owner. Revision checks reject stale local writers; remote device-transfer fencing
is a later protocol and is not claimed solved by a local database lock.

### Proposed internal records (not a published schema)

| Record | Required identity/content and constraints |
|---|---|
| Session start | Provider-neutral session ID; source Template ID/revision; plan format and engine versions; exact compiled plan and source snapshot; UTC start, IANA zone and captured local date; optional captured organization context. Immutable after creation. |
| Event | Session ID, increasing sequence, unique logical event ID, action and engine-event payload. Append only. Retried logical action cannot append duplicates. |
| Checkpoint | Session ID, expected/current revision, last committed event sequence, reducer status, scheduled/observed/pause/unobserved counters, completed phase IDs/indexes and completion acknowledgement. Derived from the frozen plan and recorded events. |
| Unfinished slot | Unique singleton reference to the unfinished session. Creation and release are transactional. A second start fails with a resume/discard choice. |
| Outbox intent | Stable operation ID and session revision, pending/retry/error state; points at canonical data. Created atomically with local changes. No OAuth tokens in payloads. |

An initial schema draft and synthetic examples will be reviewed against the
existing session schema before publication. Canonical session changes or future
migrations require their own documented compatibility and rollback plan; approval
of this ADR does not waive migration gates. Do not enable destructive migration
fallbacks. M0 has no saved canonical sessions to migrate.

### Start and recovery

1. Validate and freeze the selected Training Template revision and effective plan
   before creating the session transaction. Standalone ungrouped launches work;
   no active Season is inferred. Reject unsupported/ambiguous context or patterns.
2. Persist the start record and first checkpoint before exposing an active workout.
   Persist user actions and phase-boundary transitions; measure checkpoint cadence
   and write cost rather than choosing a claimed reliability guarantee in advance.
3. On restart, load the frozen plan and last reliable committed checkpoint, never
   recompile it from an externally changed Template. Present resume/discard.
4. Restore unfinished work paused with fresh monotonic anchoring. Keep its
   scheduled/observed/pause counters and completed performance unchanged. Record
   that an interruption was unobserved. Never compute observed exercise from wall
   time. If the gap duration cannot be established across reboot/clock changes,
   preserve that uncertainty rather than inventing a number.
5. Resume only after explicit user action. Confirm discard; retain an auditable
   partial/discarded record rather than erase the source log. Releasing the
   unfinished slot must not delete completed history or queued sync work.

## Alternatives considered

- **Atomic JSON checkpoint file:** small initial implementation, but synchronizing
  append-only events, checkpoint, unfinished slot and outbox across several files
  needs a journal/commit protocol. Avoid inventing a second database transaction
  mechanism for the daily-use workflow.
- **Direct SQLite APIs:** viable transactions without Room's query checks and
  migration scaffolding; more handwritten Android persistence code.
- **Cloud-first persistence:** conflicts with offline-first recording. Rejected.
- **Extend the in-memory mock:** cannot survive process death. Rejected.

Room's documented benefits include compile-time SQL checking and migration
support. Android recommends Room over direct SQLite APIs. Transaction annotations
support grouping DAO work; actual disk-failure/restart behavior still needs tests.
Sources: [Room overview](https://developer.android.com/training/data-storage/room),
[transaction API](https://developer.android.com/reference/androidx/room/Transaction).

## Required evidence before calling recovery complete

- Transaction rollback under injected failure: no event/checkpoint/outbox mismatch.
- Duplicate action retry, stale writer and second unfinished-session rejection.
- Process recreation using the frozen revision, including external Template changes.
- Pause/navigation/partial progress survive restart with unchanged time categories.
- Unknown/reboot gaps never become completed exercise; resume requires confirmation.
- Save failure remains visible and does not acknowledge a successful action.
- Android instrumentation/emulator coverage for the supported version matrix,
  including Android 12 and the latest supported version, plus a guided physical
  phone force-stop/relaunch test. JVM mocks alone are insufficient evidence.
- Completed immutable history and outbox survive app restart; Drive retry and
  conflict behavior remain unproven until the separately approved Drive experiment.

## Approval requested

Approve the Kotlin session-store port and Room/SQLite transactional implementation
for M1's new local recording/recovery boundary, under the constraints above.
No external service, real data import, migration or billable resource is included.
