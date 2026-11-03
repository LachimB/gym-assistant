# M1 finalized synthetic session delivery

ADR-0005 was explained to the owner and explicitly approved on 2026-11-03.
Implementation remains an M1 slice; real-workout upload, active-device handoff,
library publication and full end-to-end real-workout acceptance remain open.

## Boundaries

- Added Room2 tables for immutable account/root bindings and durable canonical
  delivery intents; explicit additive1→2 migration, no destructive fallback.
  Existing execution tables and canonical1.1.0 are unchanged. Internal receipts
  remain local. Acknowledgement updates outbox status only through the frozen
  terminal revision and retains every row.
- Foreground, explicitly initiated authorization using the existing personal
  Android client and exactly drive.file. Tokens remain in batch memory and are
  cleared when transport closes; no app refresh-token store/background worker.
- The live sender is disabled by default. Private build requires the approved
  account pin and `syntheticDeliveryBudgetReserved=true`, only after reserving its
  separate20 requests against the existing global500 allowance. New private
  counter `synthetic-session-delivery-v1` persists each direct request before send;
  max20 direct requests and6 authorization attempts. SDK internal traffic unmeasured.
  Do not consume or reset the old read-probe's unused8 requests.
- Preview/send operates on one generated public-fixture example with fixed source,
  synthetic session ID and timestamps. Factory verifies exact source bytes; sender
  eligibility compares the complete generated canonical record, not a display name.
  No existing phone workout is selected, reclassified or uploaded. Fixture admission
  never touches the unfinished slot; current workout remains independent.
- Before file access, match stable personal permissionId and pinned email; verify
  exact labelled synthetic root and marker. Reserve provider ID and persist it
  before create. Exact-ID404 permits same-ID create;403 is never treated as absence.
  Duplicate409 and lost responses require verifying the same ID, MIME/parent/marker
  and exact canonical bytes. Only verified data earns delivered status.
- Conflicting remote contents are preserved, with local intended bytes retained;
  no overwrite/replacement file. Bounded transient retries, saved future retry time,
  cancellation propagation and explicit reauthorization. A restarted model reads
  saved synthetic delivery status without a network request; no automatic send.

## Migration and private backup

`DELIVERY_MIGRATION_1_2` creates only the two new tables/indexes. Exported Room1 and2
schemas retained. Emulator migration fixtures are built from the actual exported
version1 schema, populated with completed and paused histories and pending rows.
Migration validates Room schema, every original value, frozen projection and paused
recovery; injected upgrade failure rolls back both new tables and version change.

`tools/backup_android_database.py` is an explicitly invoked local backup helper,
not an automatic exporter. It first inspects a private copy and refuses running
unfinished work. It stops the app only after the paused check, retains a raw
SQLite/WAL/journal bundle outside active app storage, and verifies a standalone
SQLite backup against integrity, foreign keys, complete receipt prefixes,
checkpoint/receipt agreement and a logical row digest. Private output permissions;
only counts, checksums and status evidence printed, never source/session contents.
No cloud transmission, restore, data clear or automatic resume. A failed backup
prevents phone installation/migration; captured data is retained for review.

Current phone application requires verified backup after tests. Do not infer
phone migration success from build or emulator results. Old version1 APKs cannot
read a migrated version2 DB; no automatic downgrade/restore is authorized.

## Evidence so far — 2026-11-03

- 36 JVM tests pass: prior17 plus10 delivery protocol,7 HTTP-domain and2 exact
  generated-sample tests. Lost acknowledgement/cancellation/local-ack failure reuse
  one provider ID without another create; mismatched accounts,403, changed payloads,
  expired auth and budget failures do not silently retry or widen scope.
- 16 real SQLite emulator tests pass, including unchanged paused-workout admission
  of the generated sample, migration success/failure and immutable intent dedup.
- Local Android12 process-recovery script passes with no automatic resume/data clear;
  observed541→948ms only after explicit resumes. Not drift/clock-accuracy evidence.
- 68 Python tests pass, including backup rejection/verification and bounded retries
  for truncated UI XML; validator19 fixtures/80requirements and repository hygiene pass.
- The emulator's uiautomator transport once returned truncated XML in separate PR16
  CI after its12 SQLite tests passed. Observation now re-dumps at most3 times for
  parse failure, never accepts partial trees and does not suppress app failures.
- Phone backup/install/migration/live delivery and supported-version CI for this
  implementation are pending. No delivery-stage Google request or additional budget
  reservation has occurred yet. Previous synthetic global allocation remains57/500.

## Updated pre-phone evidence

The private configured version5 APK passes36 JVM tests; certificate matches the
approved Android client. Emulator recovery+synthetic preview passed447→530ms only
following explicit resume, with exact checkpoint/source unchanged by both dialogs.
The preview was inspected without pressing send; no Google API traffic.

Backup rehearsal on that emulator succeeded with schema2: integrity/foreign keys
ok and matching logical/standalone evidence. Copied hot journals can require
SQLite recovery; validation now permits that only in the disposable host copy,
never in the active database or retained raw bundle. Four backup unit tests cover
validation, rejection and WAL-backed snapshot preservation. No phone backup yet.

The dedicated20-request delivery reservation was performed once in the localhost
page: global allocation **77/500**, including old native unused8 and new unused20.
Counted direct/setup attempts remain49; no delivery-stage request yet. Private
build flag is enabled only in the locally configured APK, not source/default CI.

Final retry checks also cover HTTP-date Retry-After, delay caps, pre-transmission
budget failure and wall-clock correction of a saved retry time. Latest JVM total39
passes. Latest private version5 APK SHA256:
`87808112806031903050af5a20094eb06489c4d4f9a2d5845e92ca3b9d297368`.
Earlier APK checksum in the handoff is superseded; signer unchanged. No physical
phone was visible to ADB at the pre-backup check; owner reconnect requested.
