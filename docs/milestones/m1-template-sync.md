# M1 selected Training Template synchronization

ADR-0009 is approved. GA-DOM-006 and GA-DATA-001/002/003/004/010/011 remain the
binding requirements. This initial implementation adds contracts and persistence;
it does not enable a live connection or establish background/device acceptance.

## Implemented foundation

- Provider-neutral revision/head1.0 schemas, strict bounded JSON codecs and exact
  retained-byte/hash verification followed by complete compilation with the
  existing Template1.0/1.1 and engine2 contracts. Synthetic fixtures derive from
  the existing public format2 source snapshot; no private source was copied.
- Three-way scalar merge model with stable ancestor/property references. All
  conflicting fields need explicit choices and a fresh revision ID; a complete
  candidate validator must succeed. Structural changes block. The lossless YAML
  field adapter and final source-review UI are still required before adoption.
- Guarded per-file protocol: require a verified durable intent/current lease;
  verify namespace; compare prior tag/hash; require conditional replacement or a
  reserved-ID create; inspect the same ID after interruption; verify full bytes,
  parent/marker/MIME/name and namespace before a per-file acknowledgement.
  This tested provider interface is not evidence of live Drive YAML behavior or
  multi-file/head publication. No unconditional fallback or automatic deletion.
- Immutable app-private source/before-image/intent files, bounded safe paths,
  hash validation and full recompilation on reopen. Fsync and atomic rename
  precede returning the pointer; existing retained files cannot be replaced.
- Additive Room4→5 migration, four indexed/foreign-keyed sync tables and a durable
  per-binding lease generation. Only enabled bindings under an active root with
  the same stable account can claim work. Stale or expired acknowledgements fail.
  Sync transactions contain no network operation or workout timing mutation.
- Private backup tooling covers all16 tables plus the sync-file archive. Existing
  database versions and delivery/library archives remain supported.

## Local validation and required gates

Android57 JVM, adapter20 JVM and122 Python tests pass. Engine JVM tests pass.
Android migration instrumentation compiles; SQLite execution and the supported
API31–36/37.0 matrix must pass in CI. No local emulator was configured for this
check. Migration tests seed all12 old tables, inject4→5 failure, verify rollback,
then compare all prior rows and retained personal payload bytes after upgrade.
They also cover worker contention, stale lease rejection, retry deadlines, reboot
reclaim and inactive/mismatched root refusal.

No phone update, root creation/relocation, token acquisition, Drive request or
budget reservation has run for this implementation. Preserve CI plus a fresh
verified private backup before phone installation. The exact private from/to
inventory still requires owner review before any live consolidation.

## Remaining implementation and acceptance

Wire durable queue/progress reconciliation, lossless YAML field mapping, verified
Drive hierarchy/conditional endpoints, immutable history/head publication and
external-candidate review. Add the separately reviewed WorkManager dependency and
bounded authorization/budget integration, then implement UI and compatible
single-root readers/writers. Prove process death, background/screen-off/Doze and
same-ID interrupted recovery on supported emulators and the physical phone.
The bounded live batch and complete normal-workout acceptance remain open.
