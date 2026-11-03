# ADR-0006 runtime contract implementation

Owner approved2026-11-03 after explanations. Private imports remain local; the
current exact-sample cloud gate is unchanged. This document records implemented
contracts, not complete M1/runtime/library/device acceptance.

## Source and compiler

Template1.1 preserves1.0 and adds explicit phase roles and an optional per-set
`inSessionRepetition: {mode: appendToCurrentSet, resetMs: ...}`. Rest steps with
role`reset` name their immediately preceding repetition with`resetForStepId`.
A compiler never guesses reset ownership from labels or durations. A declared
addition policy requires contiguous ordered repetitions separated by its explicit
reset; unsupported alternation/manual/transition patterns are rejected visibly.
A1.0 source can compile as a fixed plan with unclassified roles and no addition.

Strict typed JSON-compatible YAML input:1MB/file,32 exercise files,8MB total,
64 nesting levels and100,000 parser events. Reject duplicate/non-string keys,
aliases, anchors, explicit tags and ambiguous unquoted legacy YAML scalars; quote
text such as`yes` or date-looking labels. No source excerpt in exceptions. Safe
integers and finite resistance values; no coercion of quoted numbers. Validation
uses the checked-in template/exercise schemas; unknown assertion keywords fail
closed in the Kotlin import validator. TypeScript uses Ajv without coercion,
default insertion or property removal. Existing dependency versions retained;
SnakeYAML-KMP3.1.1 is already Kaml0.73's parser and is now an explicit dependency.

Plan2 captures names, side, structured resistance, block/occurrence/set/source-step
references, unit/phase instance IDs, role and original/effective time positions.
Stable SHA256 identities hash UTF-8 of length-prefixed components (lengths are
UTF-16 code units, consistently in Kotlin and TypeScript). An immutable
ordinal maps identities for compact checkpoint ranges; it is not a timeline-array
index and never changes when later phases are inserted. Shared Kotlin-produced
public golden in`examples/expected/timed-runtime-v2.plan.json`; independent TS
compilation matches all fields. JSON schema:`schemas/compiled-plan.v2.schema.json`.

Limits:10,000 phases,1,000 additions, safe integer time/count bounds,4096-byte root
name and a conservative8MB compiled-plan estimate including policy snapshots.
Resource failures reject rather than truncate source/plan facts.

## Session-only additions

The active eligible set is explicit. Additions append after its last original
repetition and prior queued additions, before the next hold/rest/side. The current
countdown and completed phase identities stay unchanged. A trailing source reset
is reused before extra work and followed by one new reset before a separate hold;
without a trailing reset, insert one reset before the extra work and retain the
following rest. Both cases add only the matching work duration plus one reset.
No duplicated hold, side, exercise pair or inter-set rest. Source Template and
base plan are immutable. Adjustments have domain IDs independent of transaction
operation IDs; identical replay is idempotent and conflicting reuse rejects.

Normal timing/skip/rewind uses the existing deterministic mechanics, mapped to
stable instance identities. Observed time, pause and known gaps stay separate;
base cursor is inapplicable during inserted work. Rewind does not subtract
observed work. Recovery blocks additions until explicit resume; paused additions
stay paused. Meaningful history still excludes empty ticks and internal receipts.

## Local integration and evidence

The following integration is implemented in the follow-up review after the core
compiler PR. Four additive Room3 tables store staged candidates, immutable
revisions, reviewed heads and structural execution plans. Publication rechecks
source/plan hashes and uses a transaction plus expected-head comparison; failed
publication leaves the prior revision executable. Source files are app-private,
atomic and immutable. The system picker reads only explicitly selected files.

Saved format2 freezes the exact source and base plan/hash. Checkpoints reference
an immutable effective plan revision, stable active instance/offset and compact
completed ordinal ranges. Only structural additions create a plan row; ticks do
not duplicate plans. Action, checkpoint, plan revision and outbox commit together.
Recovery validates the complete receipt/plan prefix and requires explicit resume.
Legacy format1 dispatch remains unchanged; neither version converts the other.

Canonical1.2 includes original/effective plan snapshots and hashes, domain
adjustments, meaningful events and distinct planned/adjusted/observed/pause/gap
facts. Original-plan position is explicitly null during extra work. It excludes
raw ticks, transaction IDs, checkpoints, plan-revision rows and outbox. The web
reader recompiles the frozen source independently in TypeScript, applies ordered
semantic adjustments and checks effective bytes/identities/positions/counters.
Existing1.0/1.1 readers and tests remain.

Android supports local import, complete phase/cue preview, explicit publication,
ungrouped start, side/structured resistance and eligible +1 repetition. Publication
and import require idle/paused state. An unfinished workout prevents another
start; nothing discards or converts it automatically. A tap that arrives after its
set boundary records timing without adding to another set and reports the result.
The process-owned Room instance stays shared with the Drive adapters.

Local checks so far: Kotlin9 runtime tests, TypeScript34, web22, Android40 JVM and
24 real SQLite tests; Python71. Public Kotlin base/trace and human1.2 fixtures are
independently verified by TS. SQLite checks cover populated1→2→3 and2→3 preserving
all original rows, failed migrations/publication rollback, quarantine/stale review,
source tampering, duplicate/failed additions, compact checkpoints, recovery,
late-tap boundary and legacy/new coexistence. Existing engine1 real process-stop
acceptance passes. Seven-version CI, real format2 process/UI evidence, private
preview conversion/comparison and physical acceptance remain required.

The physical Room2→3 migration is verified after successful APK-source CI and a
fresh persistent private backup:706 prior actions/outbox, source/progress and
Drive binding/delivery unchanged; only normal recovery appended. The owner then
saved the old workout as a partial record. The backup helper verifies standalone
SQLite and a separate private library archive without extraction/content output;
its remote binary transport correctly distinguishes absent optional files. Keep
all prior backups. No clear, uninstall, automatic restore or session conversion.
The long-preview publication button is now in a fixed footer; a disposable-only
selected-file UI regression is included in every supported Android CI version.
The UI follow-up and owner real-template publication/execution remain open.
Real-workout cloud uploads remain excluded by the existing exact-synthetic gate.


## Physical local acceptance, short test only

PR19 merged after10 successful checks (standard41674024615 / Android41674024398),
including actual selected-import/fixed-footer publication on all seven versions.
Owner's phone imported and published the private45m50/474-phase source; metadata-
only independent comparison matches all8 definitions and the prepared plan.

In the first eligible set, a paused addition changed the session45m50→46m00.
Actual process termination/relaunch retained the exact source/effective plan,
addition, active cursor/offset, completed identities and observed/pause counters;
normal unknown-gap evidence increased once. Recovery stayed paused without ticks.
An initial early sample preceded the recovery commit; verification resumed from
the ORIGINAL stopped backup, rather than creating a new baseline or claiming an
instantaneous restore. No numerical device recovery-time guarantee is established.

The owner discarded the short test; its history is retained. The published
Template still plans45m50 and no unfinished slot remains. This verifies local
import/publication/addition/recovery, not a full real workout or M1 completion.
This local acceptance sent no source/session content to Drive or a new AI operation. ADR-0007 is a separate proposed
manual personal-delivery boundary and migration, awaiting explicit approval.


## Natural completion regression

A subsequent full virtual projection found an absent-phase offset comparison using
an Int zero against the saved Long offset at plan end. Explicit0L preserves the
existing contract and makes completed checkpoints readable/acknowledgeable. No
migration. New public complete1.2 fixture plus JVM and real-SQLite reopen/recovery/
finish tests cover this boundary; independent web reader validates original80s,
adjusted90s and all12 phases. Counts now41 JVM/25 SQLite/web23/23 fixtures.

The private full virtual shape (one added unit) also validates all476 phases and
956 meaningful events independently in TypeScript, with1,219,806 UTF8 bytes and no
network. Its private raw JSON is not committed or uploaded and is not physical
full-workout acceptance. The v8 phone update and current-head CI must be recorded
before declaring this regression closed on the installed build.
