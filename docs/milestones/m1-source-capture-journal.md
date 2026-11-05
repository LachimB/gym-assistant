# Incomplete remote-source capture journal

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
This private file/store boundary prepares retention before remote source reading.
It performs no network operation, remote-candidate validation/adoption or library change.

The versioned `sourceCaptureProgress` record fixes the original queued local
publication pointer, unique observation/capture origin, binding fingerprint, stable account/root/folder roles, exact
known source IDs/names/MIMEs and an append-only raw-observation chain. Separate
captures of the same job cannot borrow each other's audit chain. Reopening
recompiles the frozen local source and verifies all origin/audit hashes. Duplicate
IDs/names, namespace aliases, unsafe filenames, changed origin, wrong fingerprint,
unknown fields/versions or invented completion claims fail. A received malformed
remote response can be retained as untrusted bytes; it is never passed off as a
validated Training Template.

The only stage is `capturing`. The existing observation row stays pending with
validation code `changing`, meaning a stable validated snapshot has not yet been
established. Candidate, observed remote generation and tag remain unknown/null.
The original expected base stays in the frozen local publication. Empty pins or
a404 response do not establish a clean/new remote state or erase that expectation.
No future workout default or running Workout Session is changed.

Only the current leased owner of the same frozen job/binding can advance the
observation pointer. Hash/compiler/fsync work finishes outside SQL. The short
transaction rechecks lease, scope, digest-only source identity, original job
pointer and pending observation; expiry or injected failure rolls back. Files
left after rollback remain inert evidence. Repeated begin reuses the currently
validated pointer, including facts appended since the original begin. Advancing
the job to another intent or changing observation review state blocks appends.
No Room table/column/schema change; only queries on the existing observation row.

Five new JVM tests cover frozen origin, malformed raw reopen, unknown/missing
facts, aliases, capture isolation and tampering. Local147 Android JVM/36 adapter JVM checks pass;
instrumentation compiles. Five additional SQLite cases cover reopen/idempotency,
pointer rollback/retry, lease expiry before/after SQL, retarget and stale origin/review state,
comparing all twelve pre-sync tables unchanged. Those cases still require actual
current-head seven-version CI; compilation is not execution evidence.

Repeated CI bootstrap downloads timed out before compilation. The pinned wrapper
download now allows60 seconds instead of10; URL, version, wrapper bytes and all
library versions/licenses/notices remain unchanged. The reviewed input digest
records this operational-only change. No binary release clearance is added.

Verified readonly head/working capture, prior immutable-base verification,
candidate classification/three-way review, transfer-intent preparation,
binding/job completion and authorization/scheduling remain open. Exact known
source IDs are inputs; cold-client discovery is not implemented or inferred from
names. No live request, extra allowance, OAuth launch, resource, dependency,
recording/default change, APK assembly, installation or worker/UI enablement.
All approved authorization launches remain used. M1 and actual native/provider/
background/screen-off acceptance remain incomplete.
