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
  candidate validator must succeed. Structural changes block. The YAML adapter
  now maps declared section/block/occurrence/set/phase/step/variation IDs and
  replaces only resolved scalar tokens, retaining raw base/phone/Drive branches.
  It preserves Unicode coordinates, CRLF, unchanged source bytes and comments,
  including block-header comments. Comments/layout or unkeyed-array changes are
  conservative structural conflicts requiring source review. Existing BOM import
  rejection is preserved. Final source-review UI is still required before adoption.
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
- Additive immutable progress records retain the original captured intent and
  every verified per-file receipt. File sync/readback precedes a transactional
  compare-and-swap of the job pointer under the same current lease, source and
  enabled binding. Duplicate acknowledgements reuse the pointer; unknown files,
  changed receipt facts, source/working-ID overlap and retargeted bindings fail.
  All working-file receipts still do not mean a head is published or adopted.
- Source-only offline publication intents retain an explicitly selected published
  revision, source/plan hashes and stable binding fingerprint without inventing
  remote IDs or treating an unknown head as absent. Exact source is recompiled
  before enqueue/reopen. Transactional insertion checks the selected head and
  binding; repeated logical operations keep the same job. These intents still
  require verified remote capture before any working-file operation.
- A leased IO executor now bridges that captured intent to the guarded provider
  protocol and durable receipts. Reopening after an uncertain write reads the
  same ID. Already acknowledged files are read-only during retry; a newer edit
  blocks rather than being repaired. A final vector recheck catches an earlier
  edit during a later transfer. The result verifies only working files, without
  declaring the job complete, committing a remote head or adopting a Template.

## Local validation and required gates

Android86 JVM checks pass, including native transport, private-progress and
source-only offline-queue
regressions. Adapter20 JVM and122 Python checks passed in the preceding baseline.
Android migration instrumentation compiles; SQLite execution and the supported
API31–36/37.0 matrix must pass in CI. No local emulator was configured for this
check. Migration tests seed all12 old tables, inject4→5 failure, verify rollback,
then compare all prior rows and retained personal payload bytes after upgrade.
They also cover worker contention, stale lease rejection, retry deadlines, reboot
reclaim and inactive/mismatched root refusal.
Four additional SQLite regressions cover progress after reopen, injected pointer
commit rollback and retry, lease expiry during verification, stale/disabled
owners and folder retargeting, comparing all12 pre-existing tables unchanged.
They require current-head execution across the full CI matrix.
Four further SQLite cases cover offline enqueue/reopen, concurrent duplicate
requests, rollback/retry, stale selection/reused identity and scope retargeting.
Five executor cases cover same-ID lost-ack recovery after reopen, read-only retry
after an external edit, final-vector divergence, lease expiry in flight and
cancellation before mutation. They use a synthetic in-memory provider with the
real Room/filesystem progress store, not live Drive.

No phone update, root creation/relocation, token acquisition, Drive request or
budget reservation has run for this implementation. Preserve CI plus a fresh
verified private backup before phone installation. The exact private from/to
inventory still requires owner review before any live consolidation.

## Remaining implementation and acceptance

Wire source-only intents into verified remote capture and durable execution, verified
Drive hierarchy/conditional endpoints, immutable history/head publication and
external-candidate review. Add the separately reviewed WorkManager dependency and
bounded authorization/budget integration, then implement UI and compatible
single-root readers/writers. Prove process death, background/screen-off/Doze and
same-ID interrupted recovery on supported emulators and the physical phone.
The bounded live batch and complete normal-workout acceptance remain open.


The isolated native working-file transport now has synthetic contract coverage
for pinned account/root validation, exact-tag YAML updates and reserved-ID retry.
See [m1-drive-working-files.md](m1-drive-working-files.md). It is not connected to
the UI/worker queue and establishes no live or physical-device behavior.

Persistent constrained wakeup preparation is described in
[m1-template-scheduling.md](m1-template-scheduling.md). The separate conditional
head/recovery protocol is in [m1-publication-head.md](m1-publication-head.md).
Both retain explicit integration and physical/live evidence limits.

Compatible personal/synthetic delivery and web-reader preparation for the approved
environment hierarchy is described in
[m1-project-layout-clients.md](m1-project-layout-clients.md). Exact private execution
review and verified client/device adoption still precede live consolidation.
