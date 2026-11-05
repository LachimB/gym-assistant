# M1 conditional publication-head protocol

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
This provider-neutral protocol complements the durable guarded working-file
executor. It does not supply native endpoints, immutable-history uploads or a
Room publication journal, and it does not enable live synchronization.

## Contract

- Capture the exact expected generation/tag, intended lineage, pinned metadata
  folder/marker and previously reserved head ID before any mutation. An initial
  head has no previous generation; updates preserve Template/working-folder IDs.
- Require current durable ownership and the verified account/common-root namespace.
  Retain observed competing/missing heads without adopting them.
- Verify every immutable source/manifest by identity, namespace, full hash and
  source compilation, plus the captured working-file vector, before changing a
  head. Use exact-tag replacement or the already reserved initial ID only.
- Resolve an uncertain acknowledgement by reading the same head ID. Never create
  a replacement for an expected existing head, force a competing generation or
  retry without a precondition. Verify committed source again before retaining
  the publication receipt.
- Keep that verified publication receipt even when a later working edit appears.
  Recheck the working vector and head/tag afterward. Return a verified current
  observation, a pending working-copy edit, or a competing head. No rollback or
  overwrite repairs an external edit. These separate observations do not promise
  an atomic folder transaction or prevent an edit after the final read.
- A publication receipt/result cannot acknowledge local Template adoption,
  advance a running session or complete the durable whole-publication job.

## Evidence and remaining work

Adapter29 JVM checks pass, including9 new head-protocol cases. Android86 JVM
checks pass and instrumentation compiles. Cases cover lost-write/create
acknowledgements, same-ID recovery, newer working edits, competing/missing heads,
invalid immutable source, stale ownership, bad readback and invalid lineage or
namespace. All use synthetic in-memory provider/journal state.

Implement the immutable history/manifest transport and durable journal, connect
source-only offline capture and the existing working-file executor, then wire the
approved account/allowance-bound worker and source review. Require current-head
CI and bounded live conditional-write/interruption evidence before claiming the
native publication path works. Preserve exact inventory review and the physical
CI/fresh-verified-backup gates. No native head write or phone update was performed
by this protocol implementation.
