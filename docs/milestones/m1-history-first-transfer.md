# Immutable history before editable transfer

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
The private transfer-progress1.2 record starts only from an original capture
with no working receipts. It keeps the original source/before-image pointer,
expected head, reserved identities, binding/account fingerprint and raw audit
anchor fixed while per-file progress advances. Original publication1.0/1.1 records
remain readable; a history-first record cannot masquerade as either format.

The coordinator verifies every immutable source/manifest object and commits its
receipt before permitting an editable working-file step. Store and file boundaries
also reject premature working acknowledgement. Matching existing immutable bytes
reuse their IDs. A missing acknowledged immutable object stops, even on retry.
Editable writes still use their captured before-image/tag and same-ID readback.
An acknowledged working file is read-only during recovery. The complete working
vector verifies before the head protocol, whose journal requires both vectors.

Per-file working progress creates a new linked record; it never changes the
original capture or raw-audit origin. Repeated identical receipts reuse their
pointer. Changed facts, rollback or lease expiry cannot acknowledge a transfer.
Earlier source, raw observations and publication facts remain reachable. Only the
head receipt can establish publication; no binding/default/job is advanced here.

Two new JVM cases cover ordering, immutable receipts, copied audit ancestry,
duplicate/changed working receipts and refusal to claim history-first after an
editable receipt already exists. Local133 Android JVM/35 adapter JVM checks pass;
instrumentation compiles. Two additional SQLite cases cover reopening after a
lost working acknowledgement and injected working-pointer rollback, comparing
all twelve pre-sync tables unchanged. Current-head execution across the seven
supported Android versions is required before merge.

The native post-working composition remains separate; a full native composition
still needs the strict working-response sink and delayed head construction from
completed receipts. Verified initial/base remote capture, scheduling/authorization,
binding/job completion and source review also remain open. No enabled worker/UI,
Room/schema change, dependency change, live request, resource, allowance reset,
APK assembly or phone installation. M1 and native/background acceptance remain
incomplete; this source component alone cannot authorize a live transfer.
