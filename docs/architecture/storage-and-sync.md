# Storage, synchronization, backups and migration

## Visible Drive layout (logical, not a fixed root path)

```text
My Drive/
  Gym Assistant/                        # user-selectable active data folder
    production/                         # isolate environments
      manifest.json                     # format, owner IDs, provider adapter version
      personal/
        exercises/*.yaml
        templates/<templateId>/<revisionId>.yaml
        seasons/<seasonId>/<revisionId>.yaml
        programs/<programId>/<revisionId>.yaml
        season-participations/<participationId>/<revisionId>.yaml
        promotion-proposals/<year>/<promotionId>.json
        sessions/<year>/<sessionId>.json
        event-logs/<year>/<sessionId>.json
        media/<mediaId>.*
      imported/
        source-documents/<importId>/*    # private PDF originals
        extracted/<importId>/*           # raw text, provenance, review notes
        import-manifests/<importId>.json
      export-jobs/*.json                # retry/outbox status
    staging/                            # separate credentials, data and app IDs
  Gym Assistant Backups/                 # OUTSIDE active app directory
    production/<timestampedSnapshot>/...
    staging/<timestampedSnapshot>/...
  Gym Assistant Journals/                # separate from mutable session source
    Gym Assistant Journal 2026          # native Google Sheet, month tabs
```

These are examples, not hardcoded paths. Root folder selection/relocation must be transactional and verified. Restore must snapshot current state before applying. Separate folder does **not** mean independently secure protection: backup deletion permissions and shared-account compromise are still risks; implement lifecycle and integrity tests.

## Sync invariants

- Local-first, durable transaction before showing completion. Outbox for all remote changes; retry with bounded backoff/jitter and clear status.
- Each object carries globally stable ID, object revision, base revision/ETag when available, client device ID, logical change ID, and a schema version; implement idempotent writes.
- Sync nonconflicting changes with a well-defined merge policy; conflicting edits preserve both branches as recoverable copies. Do not use last-write-wins for sessions.
- External YAML changes are validated, diffed, and do not affect active plan snapshots; invalid Drive text remains visible to user, last good validated artifact remains execution source.
- Handoff between devices must use version check and single-writer fencing; offline originating device may keep running. A second device cannot force takeover while offline without explicit conflict branch management.
- No server-side historical copies; coordinate with cloud provider and local devices. Closed-device/background updates are not guaranteed beyond platform scheduling.

## Google Drive OAuth caveat — **high-priority feasibility task**

Google recommends `drive.file` for app-created or user-opened files, and the user wants visible, editable and optionally relocatable folders. **Do not assume that selecting one arbitrary Drive folder with `drive.file` grants blanket read/write permission to every pre-existing descendant or future externally created file.** Validate exact selection flow, Picker and file-by-file grants, move/rename behavior, shared-folder access and provider permissions before claiming a working workflow. Assess `drive.appdata` only for non-human-readable configuration if useful; it is unsuitable as the sole visible template folder. Review consent/verification obligations and request approval for any scope escalation.

References: https://developers.google.com/workspace/drive/api/guides/api-specific-auth and https://developers.google.com/workspace/sheets/api/scopes .

## Backups

Versioned snapshots contain schema/manifest, valid templates, exercise definitions, media references, actual sessions, migration records and user settings (exclude auth tokens from portable archive by default). Backups are outside app data root; check completeness + hashes, allow granular preview/restore and whole archive export. Backup before bulk imports, migration, destructive changes or restore; verify safe rollback with dry-run. A concurrent backup must not corrupt an in-flight active session. Avoid claiming that cloud-location separation equals independent provider redundancy.

## Historical import and cutover

Read historic Sheets with scoped user consent. Map rows, dates, section headers, exercise labels, repetitions and resistance details; preserve source sheet/cell references and confidence/uncertainty. Show import diff and require review; never invent unknown repetition/weight/variation. Allow partially understood records. Keep original sheet untouched during migration, then read-only **after approved verified cutover**. Use a separate *new* automatically updated annual journal.

## Idempotent Sheets jobs

Outbox key concept: `(journalYear, journalMonth, canonicalSessionId, canonicalRevision)` with a stable session→column/index mapping stored independently of displayed text. Writes should update only app-managed ranges; third-party protected content should occupy specific user-owned regions. Selective rebuild should snapshot annotations and show changes before write. Cross-client retries must be de-duplicated using persistent job ownership/revision checks; no duplicate columns or sessions. Detail design is an open issue because Google Sheets does not provide the same transactional semantics as a database.


## Organization and promotion persistence

Season and Program structural revisions, Season↔Program participation records, Template membership overrides/version policies, and explicit promotion audit records belong to the same provider-neutral personal data area, not a new centralized backend database. Refer by stable IDs, not folder paths or display labels. Only committed versions are executable; invalid external YAML must be quarantined. Backups and full portability exports contain all organizational revisions and associated audit history. A completed Workout Session includes optional captured Season/Program context independent of future organization changes. The one-active-Season rule and participation referential integrity are checked on the logical collection during sync, and conflicting cross-device edits preserve both alternatives for manual resolution.
