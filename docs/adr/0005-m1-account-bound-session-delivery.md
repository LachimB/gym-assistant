# ADR-0005 — Account-bound delivery of finalized Workout Sessions

- Status: **Approved by owner in chat on 2026-11-03**
- Date: 2026-11-03
- Requirements: GA-DATA-004/005/010/011, GA-EXE-008/012, GA-WEB-004
- Builds on approved ADR-0001/0003/0004. Does not replace their guarantees.

## Approved decision, in plain language

The owner approved a first durable sync slice that sends a **specifically selected, finalized
Workout Session** to the connected personal Drive account. Save enough information
on the phone that a lost connection or restarted app can retry the same delivery
without producing another copy. Keep account selection and delivery bookkeeping
separate from human-readable session history. Never include quarter-second saves
or recovery logs in the uploaded session.

This requires an additive on-device database migration and a persistent binding
between the chosen account, destination folder and delivery. Approval covers
implementation, automated migration/retry tests and applying the tested migration
to the phone **only after a verified private backup**. Cloud validation remains
synthetic-only under the existing experiment; real-session upload requires a
separate explicit selection/preview and owner authorization. Approval does not
make M1 complete or authorize another service, scope, billing, public deployment,
phone-data deletion, account switching or background token storage.

## What changes for the owner

- Local recording/recovery continues to work without Drive or an internet connection.
- A delivery screen shows the exact personal account, synthetic destination,
  selected session, outcome and upload size before starting the test delivery.
- Status distinguishes local save, waiting for authorization/network, verification,
  delivered and conflict. “Delivered” requires reading back matching remote data.
- Authentication cancellation leaves local data and pending delivery intact.
  Selecting another account cannot redirect an existing pending delivery.
- Existing paused work stays paused. Existing history/pending internal rows are
  neither uploaded automatically nor deleted by migration or connection setup.

## Bounded first implementation

Only finalized completed/partial/discarded sessions supported by ADR-0004 are
eligible. A session at plan end but awaiting completion acknowledgement is not
finalized. The immutable source and complete local receipt prefix must validate
before producing the canonical 1.1.0 bytes. Failure blocks delivery without repair.

This slice does not implement live active-session synchronization, execution
ownership transfer, library publication, remote corrections or general conflict
merging. Those remain binding M1/product requirements, not silently relaxed gates.
No second device may start execution from a downloaded history file. Read-only web
retrieval is a subsequent consumer of the same validated history contract.

Initially the live sender is restricted to explicitly marked synthetic sessions
and the approved labelled test namespace. Display names alone do not establish
synthetic provenance. Existing phone records cannot be reclassified by filename.
A later real-data transition requires its own reviewed authorization and destination
selection; it is not an automatic consequence of adding these tables.

## Persistent internal records — database version 1 → 2

Keep all five existing tables, their rows, constraints and meanings unchanged.
Add only these tables and indexes. No OAuth token, refresh token, client secret,
raw HTTP response or user-readable source PDF belongs in either table.

| New internal table | Fields / constraints |
|---|---|
| `drive_connections` | `connectionId TEXT PRIMARY KEY`; `provider TEXT NOT NULL` fixed `googleDrive`; `permissionId TEXT NOT NULL`; `email TEXT NOT NULL` captured for display; `rootFolderId TEXT NOT NULL`; `namespaceMarker TEXT NOT NULL`; `createdAt TEXT NOT NULL`; `verifiedAt TEXT NOT NULL`; unique `(provider, permissionId, rootFolderId, namespaceMarker)` |
| `session_deliveries` | `deliveryId TEXT PRIMARY KEY`; `connectionId TEXT NOT NULL` FK; `sessionId TEXT NOT NULL` FK to existing `session_starts`; `terminalRevision INTEGER NOT NULL`; `canonicalJson TEXT NOT NULL`; `contentSha256 TEXT NOT NULL`; `remoteFileId TEXT NULL`; `state TEXT NOT NULL`; `attemptCount INTEGER NOT NULL`; `nextAttemptAt TEXT NULL`; `errorCode TEXT NULL`; unique `(connectionId, sessionId)` |

A connection is immutable in account/root identity; re-verification updates only
its display email and verified time after permissionId equality. Email alone is
not the stable account key. Changing the selected root/account creates a new
connection only through an explicit preview; it never retargets queued jobs.
This first implementation exposes no account/root-change operation.

Delivery JSON contains only the human canonical record. Its hash covers exact UTF-8
bytes; saved retries reuse those bytes. Creation checks nonempty IDs, supported
namespace, valid terminal revision, nonnegative counters and a whitelisted state.
States are `queued`, `awaitingAuthorization`, `ready`, `verifying`, `retry`,
`conflict` and `delivered`; interruptions resume from persisted intent/remote ID,
never from a guessed network result. Retry wall-clock metadata is unrelated to
exercise/pause/observed-time accounting.
Use explicit Room migration SQL and exported schemas, including indexes/FKs;
validate schema with Room rather than relying only on successful SQL execution.
No changes to canonical 1.1.0 or the saved execution/compiled-plan format.

In one transaction, freeze the selected terminal projection, connection and job.
A duplicate request for identical intent returns the same job. Different bytes or
terminal revision for the same connection/session fail as a conflict; do not
silently replace the frozen delivery. Unfinished source records remain authoritative
for recovery and are never interpreted as delivery records.

## Authentication and network boundary

Continue using the already approved Google Play authorization client, the selected
personal account and exactly `drive.file`, excluding other previously granted
scopes. App code holds access tokens only in memory; Google Play services' own
credential management remains distinct. No new app refresh-token store, web client
secret, service account or background worker in this slice.

Before file access, read `about.user` and compare stable permissionId to the bound
connection. Verify the exact labelled synthetic destination and marker. On account
mismatch, unexpected scope, revoked access or missing destination: stop and report;
do not search other accounts/folders, recreate a missing destination or widen scope.
A 401 clears app token memory and waits for explicit authorization. Redact tokens,
account identifiers, personal JSON and raw provider bodies from logs/errors.

## Idempotent delivery protocol

Use one serialized foreground sender. Local recording transactions never await
network operations. No concurrent sender or automatic periodic/background upload.

1. Persist canonical bytes/hash, connection and stable job before transmission.
2. Reserve a Drive-generated file ID, then persist it locally **before** any create.
   A crash before ID persistence can waste an unused reservation, not create a file.
3. On each attempt with a saved ID, first inspect that exact remote ID. A matching
   canonical file is a possible lost acknowledgement: verify bytes/hash, MIME,
   parent and canonical session identity, then acknowledge locally. A permission
   error is not absence. Never generate another file ID for an uncertain create.
4. If the exact reserved ID returns404, create that same ID using the previously
   tested v3 multipart create, fixed application JSON MIME and bound parent. A409
   triggers verification of that exact ID, not a new create or overwrite.
5. Read back and validate the exact bytes/hash and identity before committing
   `delivered`. In the same transaction, mark existing internal outbox rows only
   through that frozen terminal revision as delivered. Retain every row; no cleanup.
6. On differing remote content/identity, preserve local intended bytes and remote
   file unchanged, mark conflict and offer review. No last-write-wins, blind PUT,
   replacement file or guessed merge. Do not mark those local rows delivered.

Initial remote files are immutable by this sender. The successful synthetic Drive
v2 If-Match content probe establishes a useful later capability, but does not justify
v3 tag interchangeability or automatic rewriting of a human history file. Future
corrections, mutable manifests and execution handoff need a reviewed CAS/fencing
protocol before implementation.

Retry transient I/O/429/5xx with bounded foreground backoff/jitter, honoring a
bounded Retry-After. At most three transient attempts per user-initiated batch;
then retain the job and show a retry action. Every API transmission consumes the
synthetic experiment counter before sending, including failed requests. No retry
for account/contract/conflict/permission errors. Pending/retry metadata contains
only whitelisted error codes, never raw exceptions or tokens.

## Migration, backup and rollback

Before applying version2 on the phone, create a private, consistent SQLite backup
outside the active app directory without printing or sending its contents to an
AI/cloud provider. Record checksum, SQLite integrity result, table counts and
schema version as non-content evidence. Prevent recording writes during capture;
do not finalize/discard the paused workout. Include the WAL consistently using a
supported SQLite backup procedure or a verified checkpoint while the app is closed.
A raw copy of the main file while WAL writes are active is not a valid backup.

Test the explicit migration on a version1 fixture containing an unfinished paused
session, complete actions/checkpoint, prior sessions and pending outbox. Verify
all original values/counts, frozen hashes, projection, paused recovery and retry
identities are unchanged; new tables are empty. Inject failures to prove atomic
rollback. Repeat after closing/reopening the database across supported Android CI.

If backup verification or migration fails, stop without clearing data or destructive
fallback. Preserve the old file and report the issue. Do not install an old APK
onto version2 as a “rollback”; it cannot read the newer schema. Recovery requires
a separately reviewed restore or a forward fix. Keep the verified version1 backup
outside active storage; no automatic restore or deletion authorized here.

## Acceptance before claiming this slice works

- Real SQLite migration tests preserve existing paused recovery and all prior rows.
- Fake transport tests: account/root mismatch; no token persistence; lost create
  response; crash before/after remote ID and acknowledgement commits; duplicate409;
  unauthorized403; exact404; changed payload; malformed remote file; budget exhaustion;
  bounded transient retry and cancellation. No extra remote file after retry.
- Emulator process restart between all delivery boundaries; recovery remains paused.
- Small live synthetic delivery/read-back/retry using the existing approved project
  and grant. Reserve at most20 additional direct requests against the overall500
  before starting; do not reuse/reset the native read-probe's unused allowance.
  Disclose unmeasured authorization SDK traffic separately.
- Phone migration only after those checks, verified backup and install-r preserving
  app data. Phone cloud test remains synthetic-only and explicitly initiated.
- Update handoff/traceability with exact evidence and limits; PR CI must pass.

## Alternatives and costs

A volatile retry flag would lose the remote ID on process death and can duplicate
files. Using the last selected email/folder for every pending session can send data
to the wrong destination. Uploading raw receipts defeats ADR-0004. Overwriting a
remote session on retry can erase external edits. These alternatives are rejected.

The proposed additive tables duplicate a small canonical delivery snapshot locally
and retain internal bookkeeping; storage cost grows until a separately approved
retention policy. Keeping finalized delivery separate limits this slice: active
multi-device fencing and library sync still need further design and evidence.
