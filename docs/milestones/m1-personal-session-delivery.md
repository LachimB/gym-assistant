# M1 manual personal Workout Session delivery

ADR-0007 was explicitly approved after explanation on 2026-11-03. Requirements:
GA-DATA-004/005/010/011, GA-EXE-008/012 and GA-WEB-004. This is a foreground,
owner-selected finalized format2/history1.2 subset, not complete M1 synchronization.

The phone lists only fully validated completed/partial local format2 sessions.
Discarded/unfinished/plan-end-awaiting-acknowledgement records are ineligible.
Selection captures terminal revision and canonical hash; Send rechecks them before
network setup or enqueue. Preview identifies account, personal folder, Template,
date/outcome, bytes, frozen instruction inclusion and meaningful-history scope.
No AI request, PDF bytes, raw receipts/ticks, automatic enqueue, backfill or source
promotion. No Season/Program attribution is inferred.

A separate personal setup intent persists the verified account permission ID,
reserved root ID, marker and reserved/verified state. Lost create acknowledgement
reuses that exact ID. Once verified, an inaccessible root stops; no replacement or
fallback folder. Synthetic root/file gates remain in their separate remote adapter.

Room4 adds only personal_session_deliveries. Its11 old exported entities remain
exactly unchanged. Payloads are immutable bounded UTF8 files under delivery-payloads,
written/fsynced/renamed before SQL pointer insertion. Interrupted insertion may leave
an unreferenced inert file; no automatic cleanup. Every load revalidates committed
source prefix, terminal revision, hash and bytes. A missing file can be reconstructed
only identically; corruption stops. Legacy inline delivery is never converted.

The personal persistence adapter feeds the existing sender with canonical bytes in
memory; no canonical body is stored in the new SQL row. Before each batch the sender
checks account/root; it persists a reserved file ID before create. Create409/lost
response verifies that ID, MIME/parent and exact bytes. Conflicts do not overwrite.
An explicit retry of a delivered personal job re-verifies identity/root/content and
never creates another file or reopens its terminal receipt. Direct calls remain
counted by the persistent HTTP allowance even on these read-only verification calls.

Local checks:49 JVM and28 real SQLite tests, including populated3→4/rollback and
composed1→2→3→4/2→3→4, legacy/new coexistence, immutable payload, missing-file exact
reconstruction, stale review, discarded eligibility, failed enqueue, lost root-create
acknowledgement, verified-root disappearance, wrong account, namespace confinement,
retry same ID and external conflict. Existing synthetic transport tests remain.
Backup archives cover library, personal setup and delivery payloads independently,
without extracting/printing contents; path/link checks preserve privacy boundaries.

Actual API31 UI also saves a public synthetic partial session, selects it by
captured Template/date/time, reviews payload scope and saved Not sent status, and
verifies all recording/delivery rows unchanged without authorization/upload. The
seven-version workflow includes this sequentially after import/publication.

Current-head seven-version CI, fresh private physical
backup, phone migration and live owner-selected session/readback/retry remain gates.
The phone remains v8/Room3 until those pre-installation gates pass. No real folder or
upload has been performed merely by implementing the feature. Global reservation
132/500, personal cap50 direct requests/4auth; billing disabled, SDK traffic unmeasured.

API contract references: [Drive folders](https://developers.google.com/workspace/drive/api/guides/folder)
and [files.create](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/create).


Current continuation: candidate500cf55 passes49 JVM and all three API31 actual UI
flows after system-bar spacing and dialog-wait fixes. The current full matrix did
not start: GitHub blocked standard41716578704/Android41716578424 for account
payment/spending-limit reasons. Exact cause requires owner review; no billing
change authorized/performed. Phone remains v8/Room3; PR21 unmerged, no live
personal folder/upload. Newer-OS layout acceptance and mandatory pre-installation
checks remain open.


Owner confirmed public repository visibility on2026-11-04. PR21 head74bc039
passed all10 checks (standard41767116394 / Android41767116465). The configured
v9 was then installed after a fresh verified private Room3/library/payload/setup
backup. Exact before/after comparison preserves all11 old tables and archives;
Room4's personal table starts empty. Eight external source files match exactly.
No clear/restore, personal folder or real upload. The normal offline/interrupted
phone workout, selected-session delivery/readback/retry and web review remain
owner acceptance gates. See structured evidence's physicalRoom4Upgrade.
