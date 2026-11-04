# ADR-0009 — Selected Training Template publication and external-edit synchronization

- Status: **Approved by owner in chat on 2026-11-04 — implementation in progress**
- Date: 2026-11-04
- Requirements: GA-DOM-006, GA-DATA-001/002/003/004/010/011; ADR-0001/0006/0007.

## What changes for the owner

Choose a published Training Template and enable its Drive connection explicitly.
The app publishes human-editable YAML/exercise files to the existing private
personal destination. An immutable source revision is retained separately from
its editable working copy. Edits made in Drive are downloaded into a candidate,
validated and shown as a comparison. They do not become the next workout's
instructions until the owner accepts the reviewed new revision. Invalid or
conflicting edits leave the last validated Template usable offline.

The next acceptance batch covers one selected Template, publication/readback,
one external edit, quarantine/review, interrupted retry and conflict preservation.
It does not authorize source PDFs, unrelated library upload, session backfill,
sharing, AI analysis or automatic adoption of changed future defaults.

## Confirmed review decisions — 2026-11-04

The owner approved this ADR as a whole after the design walkthrough. The exact
private folder inventory review, CI, verified-backup and device evidence gates
remain binding before their respective live actions. Confirmed decisions:

- The consolidation sequence below is confirmed as the planned approach. Whole-ADR
  approval and an exact private source/destination inventory review still precede
  live creation or relocation; no folder has moved during this review.
- The live-test allowance is confirmed: up to200 additional direct Drive API
  attempts within the existing500 global allowance, reserved once after ledger
  reconciliation, and at most6 explicit Google authorization launches. Preserve
  counters and pending work on exhaustion. Billing remains disabled. Nothing is
  reserved or consumed merely by confirming this design-review decision.
- The additive phone-database migration approach is confirmed: preserve existing
  records, pass required automated/supported-Android checks, create and verify a
  fresh private backup before installation, then verify preservation after update.
  No uninstall or data clearing. Whole-ADR approval does not waive CI, private
  backup or the remaining operation-specific gates.
- Conflicts are resolved field by field, with both original versions retained.
  Show the common-base, phone and Drive values for each conflict; the owner chooses
  the value to retain. Revalidate the complete combined candidate and show its
  final diff before publication. Do not default to a whole-version winner.
- Review before adoption: downloaded changes never become future workout
  instructions without explicit acceptance. The last validated revision remains
  usable offline; running sessions retain their frozen instructions.
- Template YAML and sync metadata may synchronize over both Wi-Fi and mobile
  data, including metered networks. This decision does not set a policy for media
  downloads or authorize additional document/session uploads.
- Background and screen-off Template synchronization belong in this ADR.
  Reopening the app must not be a routine prerequisite for pending transfers.
- One stable **Current working copy** folder per Training Template, with immutable
  published revisions stored separately in History. Do not create another visible
  editable folder for every publication.
- All Gym Assistant-owned Drive folders/files belong below **one common project
  folder named Gym Assistant** in the approved personal account. Do not create separate top-level
  personal, session, Template, synthetic-test, metadata, staging or backup folders.
  This rule concerns Drive layout; local private backups remain outside app storage.

The common root is named **Gym Assistant**, confirmed during review. Child names
below are illustrative; existing app areas will be consolidated under this root
through the reviewed migration plan.

```text
My Drive/
  Gym Assistant/
    Personal/
      Templates/
        <Training Template>/
          Current working copy/
          History/
            <revision>/
          Sync metadata/
      Sessions/
    Development/
      Synthetic tests/
    Backups/
    Imported sources/  (only when separately authorized)
```

Backups are siblings of active personal data under the common root, outside the
active data subtree. Imported/source areas remain segregated; reserving a location
is not authorization to upload PDFs or other source documents. All temporary
cloud objects and interrupted-operation artifacts stay below this common root too.

Existing app folders must ultimately fit this layout. Adopting it requires an
explicitly reviewed consolidation plan: choose/verify the common root, inventory
only authorized app-owned object metadata, preserve all existing folder/file IDs,
connection identities, markers and sharing settings, and verify the resulting
ancestry. Moving an existing personal/synthetic area under the common root must
not recreate its contents or redirect delivery jobs to another folder. Do not
move unrelated user files. No live relocation or root creation occurs before the private inventory review;
existing protected bindings remain authoritative until the
consolidation and compatible readers/writers are approved and verified.

## Reviewed consolidation sequence — execution approval pending

1. After whole-ADR approval, prepare and test compatible phone/web readers and
   writers before moving existing areas. Existing area IDs, names and namespace
   markers remain valid during adoption; the new common-root binding is explicit.
   Follow the tested-update/private-backup gates before a phone database update.
2. Build an owner-visible private inventory of only the previously authorized
   personal delivery and synthetic/test areas: exact IDs, account, current parent,
   marker, ownership and effective sharing. Record the intended destination of
   each area and the existing connection/job bindings. Do not open unrelated
   documents or select targets by display name alone. Stop on ambiguous ownership,
   unexpected parents, missing access or any effect that would widen sharing.
3. Preview that exact from/to inventory for the owner before live relocation.
   Plan one private common folder named Gym Assistant. If a folder with that name
   already exists, require its explicit verified selection rather than assuming
   it is the project root or creating a confusing duplicate. Persist the chosen
   or reserved root ID and operation intent before creation, then verify it.
4. Create only the required children below the verified root. Move the existing
   personal area beneath it and existing synthetic/test areas under Development,
   retaining their IDs, names, markers, contents and sharing. Initial consolidation
   does not rename legacy areas or rearrange the session files inside them.
   Add Templates beneath the personal area for the selected Template. All new
   cloud staging and metadata stays within this hierarchy. Backups/imported-source
   locations in the illustrative tree do not authorize new backup/source uploads.
5. Before each parent change, recheck the exact source ID, expected parent and
   sharing; use guarded updates where supported and test that boundary first.
   Persist progress and inspect that same ID after an uncertain acknowledgement.
   Do not recreate/copy contents, change permissions, delete old data or redirect
   pending deliveries. A mismatch stops for review; never force a move or undo a
   newer external change automatically.
6. Verify that every inventoried project area is now below the pinned common
   root, with the original file/folder identities, namespaces and effective access
   preserved. Verify existing session deliveries/retries and read-only web access
   against those same identities. Activation succeeds only after the hierarchy
   and compatible clients pass these checks. An interrupted consolidation remains
   visibly pending and resumes from persisted steps without duplicate roots.

The initial physical layout retains existing area names for compatibility:

```text
Gym Assistant/
  <existing personal area>/
    <existing session files>
    Templates/
      <Training Template>/
        Current working copy/
        History/
        Sync metadata/
  Development/
    <existing synthetic/test areas>
```

The earlier Personal/Sessions labels describe the logical organization. Renaming
or further rearranging legacy areas is not part of this initial relocation.
Only the common root belongs at My Drive level; unrelated user files are untouched.
Whole-ADR approval and the exact private inventory review remain necessary before
any live root creation/relocation. This sequence is the reviewed plan, not evidence
that any folder has already moved.

## Approved authorization and boundaries

Reuse the approved personal Google project/account, exactly drive.file and the
existing verified personal area under the single verified common project root.
Create selected-Template child folders, YAML files and small versioned JSON
manifests only within that hierarchy. Common-root selection/creation and moving
existing app areas require the reviewed consolidation plan above. Separate editable files
from immutable revisions; create no public link or new account/service/client.
Verify stable account ID, common-root/area/child markers and exact parent/MIME
on every batch. Check bounded ancestry to the pinned common root, including for
existing session/test areas. A missing or moved-out verified namespace stops
synchronization; never create a replacement folder at My Drive level.
No new Android broad-storage permission or refresh-token store.

Reserve at most200 additional direct attempts within the existing500 global limit,
once after reconciling the private ledger; at most6 explicit authorization launches
for this batch. Existing allocations are not reset/refunded. SDK traffic remains
unmeasured. Stop with pending work on exhaustion, storage/quota problems or a need
for broader file access. Billing remains disabled; no paid resources/upgrades.
The budget includes worst-case bounded multi-file creation, readback and external
edit checks; it is not permission to spend money or to start unlimited retries.

## Background scheduling and authorization

Automatic transfer retries and remote-edit discovery must also run while the app
is backgrounded or the screen is off, for an explicitly enabled Template binding.
Use persistent, uniquely named WorkManager tasks with network constraints and
bounded exponential backoff. New approved local publications enqueue durable
one-off transfer work. Bounded periodic discovery checks the selected Template's
known remote objects, not the whole Drive account. Use a connected-network
constraint that permits both Wi-Fi and mobile data, not an unmetered-only
constraint. Respect Android-imposed network/Data Saver restrictions. Propose a
15-minute periodic discovery interval, subject to Android deferral, with additional
checks on app opening or explicit Sync now. New approved local publications enqueue
one-off work rather than waiting for that periodic check. These triggers share
the same durable queue, limits and serialization. Network policy is confirmed;
this scheduling default is included in the approved ADR. No perpetual
service, exact alarm, new server/FCM service or battery-optimization exemption is
assumed. A compatible pinned WorkManager dependency and its transitive licenses
must pass the existing inventory/review gates before acceptance.

Android controls execution opportunities. Doze, battery/network restrictions and
system scheduling can delay work. Screen-off support means pending work survives
and resumes automatically when Android permits; it does not promise immediate or
fixed-interval transfer. Distinguish ordinary background/process death/reboot from
explicit user force-stop; test and document the latter's platform restrictions
rather than claiming the app can bypass them.

The application database remains authoritative for pending work. Reconcile the
scheduler from durable enabled bindings/jobs after startup/reboot and interrupted
scheduling. A scheduler result alone cannot acknowledge a cloud operation. Workers
must stop promptly when constraints or authorization are lost, persisting verified
progress before returning. Resume the same logical job/object IDs after restart;
never create a new allowance or delivery identity to recover from interruption.

Foreground Sync now and background workers share one serialized per-binding
executor with a durable compare-and-swap lease/generation. A stale worker cannot
acknowledge a newer intent. Conditional remote writes and same-ID create/readback
still protect requests already in flight when a worker is stopped. Local workout
recording never waits for the network worker; synchronization does not alter the
frozen source, execution owner, checkpoint or timing records of a running session.

For each worker batch, use the existing Google Play services AuthorizationClient
with the pinned account and exactly drive.file. An existing grant can yield a new
short-lived token without prompting. Keep it only in that worker's memory; do not
persist access/refresh tokens in app records, WorkManager inputs/output, files,
logs or backups. Google's credential management remains distinct from app storage.
Count authorization acquisitions as well as direct calls against the reviewed
batch allowances; no worker loop may exhaust them through repeated silent retries.

If Google requires interactive resolution, the account mismatches, access is
revoked or repeated401 prevents access, record Waiting for authorization and stop
automatic authentication retries. Show a clear reconnect action when the owner
next opens the app; never launch a consent popup from the background. The existing
explicit authorization-launch cap remains in force. Budget, conflict and invalid
source states also need a deliberate resolution rather than endless retries.

Transfers may be automatic after per-Template opt-in, but downloaded changes remain
candidates. Publication/adoption still requires explicit review. Active-session
ownership transfer and general automatic history upload are outside this Template
slice; this background decision does not expand ADR-0007's session-send permission.
Approving this ADR would not declare M1 complete.

## Provider-neutral source and publication contract

Continue using Template1.0/1.1, engine2/plan2 and human history1.2 unchanged. Define
a separate bounded provider-neutral revision manifest1.0 with these exact fields:
`schemaVersion`, `templateId`, `revisionId`, `previousRevisionId` (nullable),
`sourceHash`, `compiledPlanHash`, `engineVersion`, and `files`.
Each file entry contains `role` (template/exercise), `relativeName`, opaque
`objectId`, `sha256` and `byteCount`. Require one Template and bounded exercise
inputs (at most16 YAML files total, each and aggregate within existing import
limits). Resolve no absolute path, traversal, duplicate name/ID or document URL.
Hashes cover exact retained UTF-8 source and the existing compiled contract.
A manifest does not confer execution ownership or Season/Program attribution.

Use a separate mutable head1.0 record with `schemaVersion`, `templateId`,
`generationId`, `revisionManifestId`, `workingFolderId`, `sourceHash` and
`previousGenerationId` (nullable). The working-folder ID remains stable across
publications; generationId identifies a publication, not another working folder. The provider revision token is external metadata,
not a domain revision ID. Update the head only with exact expected-revision CAS;
for Drive use the tested current-tag/stale-tag conditional-write boundary and
readback. If the endpoint cannot enforce that boundary, stop; no unconditional
update fallback. Exact API/DTO mapping and synthetic manifests accompany code.

A revision ID is immutable: different bytes under an existing ID are a conflict,
never a replacement. External authors may forget to change revisionId. Preserve
the exact received candidate, then propose a fresh revision ID with that identity
change explicitly included in the publication preview; retain original bytes as
provenance. Recompile/review the resulting source before accepting it. Stable
exercise/set/occurrence identities are not regenerated or guessed from labels.
No Training Program override, promotion or future default changes silently.

## Durable native records — additive Room4 to5

Preserve all12 existing tables, their rows, files, delivery jobs and meanings.
Add four internal records with exported Room schema and explicit migrations:

- `project_storage_roots`: `rootBindingId` primary key; provider and stable
  account permission ID; verified common-root folder ID/marker; adoption state and
  verification time. Unique `(provider,permissionId)` for this approved account.
  Adoption records the reviewed root; it never retargets existing area identities.
- `template_sync_bindings`: `bindingId` primary key; `connectionId` foreign key;
  `rootBindingId` foreign key;
  `templateId`; verified template-folder identity/marker; selected remote head ID;
  last verified head tag/generation; last common local revision/source hash;
  explicit enabled flag and timestamps. Unique `(connectionId,templateId)`.
  Account/root/Template identity cannot be retargeted in place.
- `template_sync_jobs`: `jobId` primary key; binding foreign key; unique logical
  operation ID; direction; expected local revision and remote generation/tag;
  reviewed source/plan hashes; immutable private intent-file path/hash; persisted
  reserved object-ID map; whitelisted state/error; attempt count/next-attempt time;
  lease owner/generation/expiry for foreground/background serialization. Lease and
  retry timestamps are internal sync bookkeeping, never exercise-time evidence.
  The intent records each working file's expected provider tag, before-image path/
  hash, intended hash and acknowledged result. Source bytes remain in immutable
  bounded files, not large SQL cells. No tokens.
- `template_sync_observations`: `observationId` primary key; binding foreign key;
  existing candidate foreign key where staged; immutable private observation-file
  path/hash for file-ID/tag/hash vectors; expected local revision/remote generation;
  validation/review state and capture time. Invalid candidates remain retained.

Use foreign keys/indexes and nonnegative bounded counters. Validate private paths,
versions, hashes and identity on every load. Write/fsync/atomically rename private
intent/source files before committing pointers. A failed transaction may leave
an inert unreferenced file; never acknowledge a partial publication or auto-clean
retained evidence. Exact field constraints/state enums must be published in the
implementation without widening these behaviors.

Before phone installation: successful CI on every supported Android version and
a fresh verified private Room4 plus library/delivery/setup/sync-files backup
outside active storage. Preserve all previous backups. No uninstall/clear,
destructive fallback, automatic restore or conversion of existing sessions.

## Publication and recovery protocol

1. Capture the selected validated revision, expected remote head and current
   working-file ID/tag/hash vector in a durable intent. Verify the account and
   complete common-root/area/Template hierarchy. Present any remote divergence
   before authorizing changes; preserve both branches on conflict.
2. Preserve verified immutable before-images and prepare the new immutable
   revision under History. Reserve/persist IDs before creating new objects.
   Reuse the stable Current working copy folder and existing working-file IDs.
   Newly required files are created only inside it using reserved IDs.
3. Update an existing working file only with an exact expected-revision
   conditional write, after its prior bytes are retained. Read back its ID,
   parent, namespace, bytes/hash and resulting tag before recording the step.
   A lost acknowledgement is resolved by inspecting that same ID: matching
   intended bytes acknowledge the existing write; divergent bytes stop for
   conflict review. Never retry as an unconditional overwrite or a second copy.
4. Working-folder edits are not a multi-file transaction. Persist per-file
   progress so interruption leaves a visible incomplete synchronization state,
   not a newly published Template. Keep the prior validated local revision usable.
   Do not roll back by overwriting a newer external edit. Retain the base, intended
   revision and observed remote branch for recovery/review. A removed dependency
   is excluded from the reviewed active source set but never silently deleted;
   any archival move stays under the common root and needs explicit reviewed
   handling before it occurs.
5. Only after all required immutable and working files verify, conditionally
   advance the publication head from its expected tag/generation. Recheck the
   working-file vector; divergence prevents claiming a fully synchronized working
   copy. A competing head preserves the prepared branch and leaves the last local
   validated default unchanged. Read back the committed manifest before success.
6. Local adoption uses existing expected-head review/CAS. A failed or stale review
   cannot advance the local Template head. Running Workout Sessions continue from
   their frozen snapshots. A newer working-copy edit remains a pending candidate,
   even after an immutable revision has been committed.

The immutable manifest defines the published source boundary. Current working
copy always refers to the same editable folder; during a partial multi-file write
its contents may be mixed and must not be treated as a validated publication.
History retains verified source snapshots separately. Implementation must prove
conditional YAML writes and interruption recovery before live updates; the earlier
JSON conditional-write experiment alone is not evidence that this new flow works.

## External edit observation, merge and review

Read only the bound head and known app-created folders/files below the verified
common root. Capture metadata,
bytes and hashes; recheck tags to detect changes during capture and before review
publication. Reuse safe YAML/compiler validation and exact source/plan comparison.
Never execute quarantined data. A new ungranted file is an explicit access problem,
not permission to widen scope, enumerate the user's Drive or ignore a dependency.

Use three-way comparison against the last common source. Preserve raw local,
remote and base bytes. Disjoint stable-ID field edits may form a merge candidate
only if the complete result compiles and the owner reviews its exact diff. Never
merge YAML as arbitrary text lines or infer equivalence by labels/array indices.
Conflicting scalar changes require field-by-field owner resolution against the
common base, showing phone and Drive values with their stable source references.
Keep choices separate from the original branches. Do not infer an unresolved value
from edit timestamps or silently select an entire version. Deletion/reorder,
identity or unsupported structural conflicts must remain explicit and blocked if
a safe field-level result cannot be represented. Retain both branches. Validate
the complete resolved candidate and require final diff review before publication. Failure to safely
produce a candidate does not erase either source or change the last valid default.

Published older revisions remain verifiable by hash; if edited externally, report
corruption/conflict rather than silently repairing them. Pending working-copy
changes remain visible even when another publication advanced the remote head.
A metadata recheck is not a promise that a human editor cannot edit again after it;
newer edits are preserved for the next observation, never overwritten by adoption.

## Required evidence and remaining work

Synthetic evidence: populated/composed Room migrations preserving all old records;
failed commit/rollback; interrupted creates and head acknowledgement; same-ID retry;
CAS conflict; malformed/missing/ungranted dependencies; concurrent edits; immutable
revision tampering; stale review; safe disjoint merge and preserved real conflict;
request caps, auth cancellation and account/root mismatch; single-root ancestry
checks, no top-level fallback, stable working-folder/file IDs, interrupted per-file
updates and verified preservation during any separately approved consolidation.
Add scheduler/worker tests for duplicate enqueue, foreground/background contention,
constraint loss, backoff, process death/reboot reconciliation, silent token renewal,
interactive-resolution-required pauses, expired/revoked authorization and budget
exhaustion. Prove actual background/screen-off behavior and Doze deferral/recovery
on supported emulators plus private physical-device evidence; no latency guarantee
may be inferred from a simulated worker alone. Test all supported
Android versions. Preserve engine/history interoperability and offline start.
Then run the bounded owner-selected live batch after the verified backup.

Detailed sources, identifiers, phone/database outcomes and private backups remain
outside Git. No private acceptance input or transcript belongs in public records.
No PDF/session is sent to AI by this feature. Full normal-workout acceptance,
broader editing/ownership transfer and other M1 gaps remain explicit. Background
Template synchronization is now part of this proposal, not deferred. This proposal changes no release acceptance rule.


## Platform references

- [Persistent task scheduling and WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Defining periodic work, constraints and backoff](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Google authorization on Android](https://developer.android.com/identity/authorization)
