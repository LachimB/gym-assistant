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

## Remaining implementation and evidence

Core tests cover cue/source identity, independent cross-language frozen plans,
both reset boundaries, ordered/idempotent additions, unchanged active countdown,
pause/recovery/skip, unsafe input and overflow. Legacy engine remains unchanged.
Android compiles both engines, but new engine has not yet been wired into a phone
workout. No new APK installed or Room migration applied under ADR6 yet.

Next: staged file library/review/CAS publication; additive Room3 records; saved
format2/compact checkpoints and legacy dispatcher; canonical1.2 projection and
reader; Android import/execution UI; complete emulator/device evidence and new
verified private backup before phone upgrade. Current unfinished workout must
remain usable in engine1. Full M1/private-workout acceptance remains open.
