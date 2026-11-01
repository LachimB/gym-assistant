# Storage adapter interfaces

Future interfaces: list/get/write-with-revision, detect external YAML changes, sync outbox, media references, immutable backups, account connect/disconnect, journal job updates. M0 uses mock and optional approved basic Google Drive adapter. Never assume folder selection under `drive.file` grants access to all descendant files.
