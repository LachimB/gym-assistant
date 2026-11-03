# M1 meaningful session history — ADR-0004

ADR-0004 approved 2026-11-03. The Android `SessionStore.history(id)` read-only
port now projects a committed session into portable human history. This does not
upload history, acknowledge outbox work, import the selected private workout or
provide a web history screen. M1 end-to-end acceptance remains open.

## Contract and compatibility

- New `schemas/session.v1.1.schema.json`, version `1.1.0`. Existing
  `session.v1.schema.json` and all version-1.0.0 examples are unchanged.
- `tools/session_history_contract.py` validates known versions with explicit
  dispatch. Old records retain their old facts; no new timing values are invented.
  New records additionally verify the frozen plan hash, identity, captured local
  date, event IDs/references, counter consistency and explicit final outcome.
- No Room schema/version change, migration, deletion or replacement of existing
  local JSON. Internal ticks/actions/receipts remain private local records. The
  portable root, event detail and source snapshot reject raw receipt/tick fields.

## Human record

`SessionHistory.toCanonicalJson()` emits stable domain identity/start information,
a frozen source snapshot, timing summary, meaningful events and completion kind.
It contains no Room rows, raw commands, checkpoint, operation IDs, per-tick
revision or outbox state. Consumers do not replay Kotlin commands.

| Field | Meaning |
|---|---|
| `sourcePlanSnapshot.templateYaml` / `exerciseYamls` | Frozen executed definitions, not the current library or original PDF |
| `sourcePlanSnapshot.compiledPlanJson` | Exact frozen UTF-8 hash input; parseable plan JSON, not an execution command log |
| `sourcePlanSnapshot.hashFormat` | `sha256-kotlin-timed-1-json-utf8`; allows independent verification without recreating Kotlin serialization |
| `timing.plannedDurationMs` | Full scheduled plan duration |
| `timing.scheduledElapsedMs` | Current plan cursor; can move with skip/rewind |
| `timing.observedRunMs` | Observed running time, including planned rests; can exceed plan duration after repeated work |
| `timing.pauseMs` | Measured paused time |
| `timing.unobservedGapMs` | Known monotonic gap duration; not completed work |
| `timing.unknownGapCount` | Unknown-duration interruption evidence; never converted to milliseconds |
| `completionKind` | `unfinished`, `plannedEnd`, `partial`, `discarded` |
| `status` | `inProgress`, `paused`, `awaitingCompletion`, `completed`, `discarded` |

`awaitingCompletion` means the plan reached its end but the owner has not
acknowledged finalization. `plannedEnd` does not assert every repetition was
performed: skips remain visible. Partial finalization is `completed` with
`completionKind: partial`; readers must display both together.

No active/rest/total duration or per-set repetition/hold totals are fabricated
from phase count. Those optional older-contract aggregates are omitted until
reliably derived. Source snapshots can contain personal workout definitions in
future use; this implementation has only synthetic export evidence and adds no
cloud upload authorization.

## Event mapping

| Recorded evidence | Human event |
|---|---|
| Start / phase start / completed phase | `sessionStarted`, `phaseStarted`, `phaseCompleted` |
| Pause / ordinary resume | `pause`, `resume` |
| Resume after interruption | `resume` with `resumedAfterInterruption: true` |
| Forward / Back with an actual move | `skip`, `rewind` referencing the landed phase |
| Recorded known/unknown interruption | `interruption` with explicit `gapDurationKnown` and duration only if known |
| Another unknown restart while already recovered-paused | Another `interruption`, even without an engine event |
| Reaching the end of the plan | `planEndReached` |
| Acknowledged finish / early end / discard | `sessionEnded` with plannedEnd/partial, or `sessionDiscarded` |
| A tick without a meaningful transition | No event; timing summary can still update |

Added repetitions are not implemented by this slice; they must later become
session deviations without changing the source Training Template.

Session-wide events have no invented plan step. Phase events retain source step,
phase and set IDs where present. A skip/rewind references its destination; the
preceding phase-start history gives its origin. Event IDs are a SHA-256-derived
semantic ordinal scoped to session ID, independent of intervening empty ticks.
Event timestamps are the original observation/commit instant: transitions batched
in one sample are not assigned fabricated earlier wall-clock instants.

## Read and integrity boundary

Room reads immutable start, final checkpoint and all action rows in one read
transaction. Projection checks the frozen source/hash, supported formats, ordered
contiguous prefix, unique operations and every recorded transition/receipt using
the existing reducer. It rejects inconsistent or overflowed timing, incomplete
prefixes and unsupported records. It does not modify, repair or acknowledge data.
No export-generation timestamp or random event IDs are introduced; the same
revision produces byte-identical JSON. Hash checks detect inconsistency, not
malicious tampering by an owner able to rewrite both content and hash.

The full internal prefix is used locally for validation, not transmitted as
history. Synchronization/coalescing, version preconditions, conflicts and remote
ownership fencing remain separate work. Export success does not mean synced.

## Evidence

- Nine Android JVM tests cover tick fragmentation, known/unknown recovery,
  partial/normal/acknowledged outcomes, skip without fake completion, discard,
  stable bounded IDs, frozen hash bytes and corruption/incomplete/overflow rejection.
- Kotlin-produced synthetic completed/partial JSON fixtures also pass seven
  independent Python schema/semantic-reader tests. Full Python suite: 62 tests;
  starter validation: 19 fixtures, 80 mapped requirements, unchanged 50,000 ms trace.
- Local Android 12 emulator: all 12 SQLite tests passed, including read-only
  projection across reopen/retry and rejection of a corrupt stored receipt without
  changing records/outbox. Existing engine/store checks and app/test APK builds pass.
- Emulator host force-stop/relaunch/explicit resume/partial-save check passed
  (1 ms saved, 97 ms after explicit resume; no automatic resume or data clearing).
  This is recovery evidence, not a timer accuracy/performance measurement.
- No physical device connected after build; phone install skipped. No new physical
  acceptance or long-duration/background claim. Existing tooling warnings retained.
- CI now includes the Android JVM history tests before the seven-version emulator
  matrix. Current-head remote results are recorded in the handoff/PR when available.
