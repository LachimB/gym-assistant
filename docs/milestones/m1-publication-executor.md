# Durable native publication execution

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
This additive internal journal/executor does not enable synchronization or change
Room5, recording formats, timing, library defaults or a running Workout Session.

## Original observations and recovery

Publication progress1.1 links an append-only hash-checked raw-response chain to
the original working capture, binding fingerprint, account and head identity.
Existing1.0 records remain readable and unchanged. Raw metadata/content is copied
before decoding and retained by hash, including invalid UTF-8/JSON, empty200
responses and explicit404 observations. It confers no validation or execution
authority. Semantic head observations and publication receipts remain separate.
The chain stops at1024 events or16MiB rather than trimming earlier facts. Per-
response metadata/content bounds remain64KiB/1MiB; oversized input stops.

File writes/fsync, hash checks and source compilation finish outside SQL. The
existing leased pointer transaction rechecks the frozen capture and binding;
expiry or rollback cannot acknowledge a candidate. Unreferenced evidence may
remain after failure and is never automatically removed. Reopening verifies
the full retained chain and original source, not merely the latest event.

The IO executor bridges the guarded immutable/head protocols to this store with
the caller's cancellation context. Every durable callback rechecks its lease and
original capture. Matching immutable files reuse their saved identities; missing
acknowledged files cannot be recreated. Head publication keeps the committed
receipt even if later working files or head differ. It never advances the binding,
adopts a local revision or completes the whole job.

The native composition recompiles captured bytes, verifies folder roles against
the leased Room binding, and supplies the durable sink to both transports. A
closeable batch holds tokens only in memory and closes both transports on failure.
There is no OAuth launch, resource creation, counter reset or scheduler/UI wiring.
The full coordinator must still satisfy ADR-0009's before-image/immutable-history
ordering before any editable working-copy update; this post-working publication
stage alone does not implement that orchestration.

## Verification and limits

Five new JVM regressions cover raw retention before validation, copied malformed/
empty/missing observations, hash/origin tampering, bounds and1.0 compatibility.
Local131 Android JVM/35 adapter JVM checks pass and instrumentation compiles.
Six additional SQLite cases cover rollback/reopen, expiry, lost file/head
acknowledgements, same-ID retry, later edits, missing receipts and native binding
role checks. They require current-head execution across all seven CI versions;
compilation alone does not prove execution.

Verified remote capture, full transfer coordination, binding/job completion,
account/allowance-bound authorization/scheduling and source review remain open.
No local APK assembly, physical installation, new live request, resource or
allowance ran. All approved authorization launches remain used. Native live,
background/screen-off/Doze acceptance and M1 remain incomplete.
