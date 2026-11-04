# ADR-0007 — Explicit personal Drive delivery of a selected human session

- Status: **Proposed — owner approval required; not implemented**
- Date: 2026-11-03
- Requirements: GA-DATA-004/005/010/011, GA-EXE-008/012, GA-WEB-004
- Builds on approved ADR-0004/0005/0006. Does not complete M1 by itself.

## Decision for the owner

Allow the owner to choose one finished Workout Session, review its destination
and contents, then send its human-readable recording to the owner's personal
Google Drive. Retry must verify the same file instead of creating another copy.
Local recording keeps working offline. Nothing is uploaded automatically.

The recording includes the frozen Training Template and exercise definitions,
meaningful events, added/skipped work, dates/time zone, outcome, observed time,
pauses and known/unknown gaps. Quarter-second receipts, checkpoints, outbox rows,
credentials stay on the phone; this feature does not upload original PDF bytes.
References already present in the reviewed YAML remain part of that exact frozen
source. This operation stores the recording in Drive; it does not send it for AI analysis.

This changes the existing **synthetic-only** live-data boundary. Approval is
needed before enabling or testing real uploads, even though Google Drive itself
and the timing/history formats have already been approved.

## Exact initial authorization requested

- Reuse the existing personal development project `<approved-development-project>`
  and its registered Android client, with the owner's explicit expansion of its
  data boundary for this test. Its current name does not imply continued
  synthetic-only eligibility after approval; the app preview must say PERSONAL.
- Account: `the approved personal Google account`, verified by stable Drive permission ID as
  well as displayed email. Exactly `drive.file`; no additional scope or account.
- Create one private folder named **Gym Assistant — PERSONAL M1 DEV**, separate
  from all synthetic fixtures. No invitation, sharing change or public exposure.
- First live acceptance: one owner-selected, finalized format2/history1.2 session,
  exact readback and one explicit retry. A discarded smoke-test record is not
  selected automatically. Further sends require an explicit session selection
  and preview on the phone; no history backfill or automatic enqueue.
- Reserve at most50 additional direct API attempts within the existing overall500
  limit, without resetting old counters or refunding unused reservations. At most4
  explicit authorization launches for this acceptance batch. SDK-internal traffic
  remains unmeasured and must not be represented as part of a proven HTTP count.
- Billing stays disabled. No paid resource, storage upgrade, new external service,
  web hosting, new OAuth client or credential store is authorized. Stop if existing
  storage/quota is insufficient; never enable payment as recovery.

Approval would authorize implementation, the additive migration only after tests
and a new verified backup, and the bounded live test above. It
would not authorize uploading a different document/session to an AI provider.

## Foreground workflow and durable boundaries

1. Select a finalized completed/partial format2 session. Plan-end awaiting owner
   acknowledgement and unfinished sessions are ineligible. Validate its frozen
   source/base/effective plans and complete committed prefix before projection.
2. Preview the exact personal account, labelled destination, Template/date/outcome,
   payload size and the fact that frozen source definitions accompany history.
   An explicit Send freezes canonical1.2 UTF-8 bytes/hash and terminal revision.
3. Reuse ADR-0005's persisted connection/job/file-ID protocol. Account, root,
   session, terminal revision and bytes cannot be retargeted by later UI choices.
   Local recording never waits for the network. No background sender or app token
   persistence. The existing pinned synthetic sender stays a separate path.
4. Before every attempt verify identity and exact root/marker. Reserve and persist
   one provider file ID before create; retry that ID, inspect409/lost responses,
   and read back exact identity/MIME/parent/bytes/hash before acknowledging.
   Different remote content is a conflict, never a PUT, overwrite or second file.
5. A missing previously verified destination stops delivery. No search through
   other accounts, no fallback folder, and no silent recreation. Permissions,
   contract/account errors and cancellation leave the local session and job intact.

The initial root-setup intent must also persist its verified account ID, reserved
folder ID, namespace marker and setup state before create. Retry inspects that
same ID. Once existence has been verified, a later missing/inaccessible folder
cannot be treated as a fresh create. Only explicitly reviewed setup creates a
new connection. No tokens or source/session bodies belong in this setup state.

## File-backed delivery intent — additive Room3 → 4

Keep large canonical documents in immutable app-private files so delivery SQL rows
stay small. Preserve all11 existing tables, including every inline synthetic job;
do not convert their storage or reinterpret1.1 bytes. Human history remains1.2.

Add `personal_session_deliveries` with these exact proposed fields:

- `deliveryId TEXT PRIMARY KEY`; `connectionId TEXT NOT NULL` FK to
  `drive_connections`; `sessionId TEXT NOT NULL` FK to `session_starts`.
- `terminalRevision INTEGER NOT NULL`; `canonicalSchemaVersion TEXT NOT NULL`
  fixed `1.2.0`; `projectionVersion TEXT NOT NULL` fixed `kotlin-human-1.2`.
- `payloadRelativePath TEXT NOT NULL`; `payloadByteCount INTEGER NOT NULL`;
  `contentSha256 TEXT NOT NULL`; `remoteFileId TEXT NULL`.
- `state TEXT NOT NULL`; `attemptCount INTEGER NOT NULL`;
  `nextAttemptAt TEXT NULL`; `errorCode TEXT NULL`.
- Unique `(connectionId,sessionId)` and indexes for both FK lookups. Positive
  terminal revision/byte count, maximum8MB payload, SHA256 format, nonnegative
  attempts and ADR-0005's whitelisted states validated before writes/reads.

Write/hash/fsync/atomically rename `files/delivery-payloads/<deliveryId>.json`
before one transaction freezes its pointer/hash/terminal revision and account
binding. File paths must resolve inside that directory; never accept provider-
supplied paths or overwrite different bytes. A failed transaction leaves an
unreferenced non-executable file, not an upload. No automatic retention cleanup.

Validate file bytes/hash and source prefix on every delivery/retry. Missing bytes
may be reconstructed only from the frozen committed source with the recorded
projection version and identical expected hash; corrupt/different data stops.
Keep canonical bytes independent of future source edits. A personal persistence
adapter feeds the existing bounded transport; the pinned synthetic factory remains
separate. No empty-string substitution or reinterpretation of old inline rows.

Explicit migration3→4 creates only this table/indexes and starts it empty. Export
Room4 schema and test composed1→2→3→4 plus populated3→4/rollback across the supported
Android matrix. Before applying it on the phone, create/verify a fresh private
Room3 + training-library/delivery-payloads/setup-files backup, outside active app
storage, keeping all prior backups. No clear, uninstall, downgrade, automatic
restore or conversion of an unfinished session. Approval is requested for this
exact migration as well as the real-data boundary; changes require a revised ADR.

Reject unsupported oversized payloads visibly while retaining every local fact.
Never truncate events/source, exclude meaningful facts or upload internal ticks.

## Verification and acceptance

Use generated fixtures first: format1/2 coexistence, finalized eligibility,
changed source/terminal revision, personal/synthetic separation, account/root
mismatch, interrupted setup/create/acknowledgement, duplicate retry,401/403/404/409,
conflict, cancellation, request limits and private-content/log redaction. Keep
all existing rows and outbox acknowledgement limited to the frozen revision.
Run the supported Android matrix and preserve phone data/backups.

Then the owner explicitly chooses a real record and sends it. Verify exact
provider ID/bytes/hash and unchanged file identity after retry. The owner may
download that file and open it in the existing local web reader; validate1.2
source/effective integrity independently. Check values locally and emit only
non-content verification metadata; do not send real session screenshots, source
or event text to an AI without separate per-operation consent/provider selection.

Template working-copy synchronization, external-edit conflict handling, direct
web Drive browsing, active-session fencing and full real-workout acceptance remain
open M1/product work. A delivered finalized JSON does not prove those features.
No source-default promotion, Season/Program inference or Google Sheets change.
