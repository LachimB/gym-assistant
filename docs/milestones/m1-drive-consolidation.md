# Approved environment consolidation

The owner approved ADR-0009's exact private inventory/execution plan before any
live folder creation or relocation. Reuse one common root and dev/test/prod
containers; retain existing area identities and their saved connection/job
bindings. Production subfolders and Template opt-in remain separate decisions.
Exact IDs, account inputs, markers, inventory, before-images, counters, APKs and
detailed evidence stay outside Git.

## Guarded metadata boundary

The bounded synthetic probe uses an empty, explicitly labelled subject folder
under the test environment. It verifies exact ownership, private properties,
sharing, parent/name pairs and strong v2 tags before mutation. A durable journal
captures the source snapshot and reviewed pins before the first request. The
correct-tag PATCH changes both parent and display name, then readback verifies
the same ID with a new tag. A stale-tag request tries a different name/parent and
must return412, followed by unchanged final readback. Missing/weak tags, unexpected
access or accepted stale writes cannot produce a successful proof.

An uncertain acknowledgement resumes by inspecting the same subject ID. A
verified target is retained without another accepted write; a changed source
tag or later external edit stops. The caller supplies the current approved-plan
guard, bounded fixed-origin transport and durable compare-and-swap journal. The
probe handles only folder metadata and never downloads document/session bytes.
Four synthetic cases cover correct/stale writes, lost acknowledgement, unsafe
metadata/lease refusal and false-success prevention. CI runs these alongside the
existing synthetic Drive probes and reader-helper regressions.

The provider endpoint and parent query parameters are documented in
[Drive v2 files.patch](https://developers.google.com/workspace/drive/api/reference/rest/v2/files/patch).
The live result proves the tested folder-metadata boundary; it does not establish
general v3 tag compatibility, YAML/head publication or multi-object transactions.

## Verified execution and limits

- PR41 merged at `aea8c75` after all required checks, the seven-version Android
  matrix and its exact owner receipt passed. The small per-allocation reader caps
  retain the previous exhausted ledger; the cutover's separately bounded reader
  share was reserved within the already approved parent allowance.
- The metadata-only preflight rechecked the captured source identities, namespace,
  ownership and access. Nested descendants were inventoried before the move and
  compared afterward. No document or recording content was read by AI.
- Reserved common/environment and empty proof folders were created using the
  retained IDs. The live empty-folder correct/stale metadata proof passed before
  either existing area moved. No replacement ID, copy, deletion or permission
  change was used.
- Compatible adopting/final phone configurations were installed with replacement
  only, after fresh verified private backups. All sixteen pre-existing database
  tables, retained file archives and original counters compared exactly unchanged.
- Both existing areas were conditionally moved/renamed beneath their reviewed
  environment. Final metadata verified the original area/child identities, names,
  parents, markers and owner-only effective access. Production is empty and the
  labelled proof artifacts remain under test. Submitted intents and readbacks
  are durably retained privately; usage stayed within the approved allowance.
- The hierarchy is verified. The owner reports that the final phone metadata
  probe and existing-delivery retry passed. The owner also confirmed explicit
  read-only web opening with matching values. No Training Template
  or background sync binding was activated, and no automatic recording upload ran.

M1 remains incomplete. Immutable Template publication/head recovery, external
candidate review, runtime authorization/budget integration and actual background/
screen-off/Doze acceptance remain open. Keep review before adoption and the frozen
Workout Session source independent of subsequent Template changes.

## Retained-source concurrency regression

The cutover-record CI exposed an existing concurrent offline-queue failure: one
reader could report a retained file missing while another writer temporarily
restricted the shared directory's permissions. A new two-store read/write
regression reproduced this locally before the fix. Directory/file modes are now
set atomically to the same owner-only permissions, removing the temporary access
revocation window. Reads also share the existing process-wide writer boundary,
including path/stat/open and byte-bound checks. Concurrent JVM and SQLite
regressions verify directory/file modes. Immutable files, hash checks, fsync and atomic rename are
unchanged. This adds no migration, provider request or Template opt-in. The
corrected local suite passes99 Android JVM and29 adapter JVM checks, and Android
instrumentation compiles. The27 synthetic Drive/helper checks also pass. The
existing SQLite concurrent-enqueue/reopen regression and all seven supported Android
versions passed on `c202e4b`; PR42 merged at `cb15d62` after all required checks and
the exact owner receipt passed. The installed cutover configurations
precede this fix; Template queue/runtime bindings remain disabled on the phone.

## Client verification follow-up

The web selection stopped before opening a recording because its private local
configuration still referenced the previous API key. Read-only Cloud Console
inspection showed a completed rotation and deletion of that previous key. The
existing replacement retains the approved API/referrer restrictions. Only the
local reader configuration was corrected; no credential was created/rotated and
no Cloud setting was changed by this repair.

The failed allocation remains retained and reconciled once into the parent
ledger. The owner approved one additional authorization/Picker attempt and a
six-request retry funded from unused reserved requests, with no increase to the
consolidation or parent direct-request ceilings. The prepared retry uses a new
explicit child allocation; it does not reset the failed ledger. The owner
confirmed successful opening and matching values. Its six direct requests and
single authorization/Picker launch were reconciled once; this retry is exhausted.
The consolidation's hierarchy and client checks are accepted. This does not
establish Template synchronization or complete M1.
