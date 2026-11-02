# Storage adapters

`TrainingStore` is the provider-neutral read, list, and compare-and-swap write surface used by tests. `InMemoryTrainingStore` is the only implementation in Milestone 0. It does not open a network connection.

## Drive scope proposal, not a request to provision

A later milestone can add a Google Drive adapter if Michal approves a Google Cloud project and an OAuth client separately. The candidate scope is `drive.file`: the app would create and reopen files the user picks, and it would not ask for full-drive access. Folder selection under `drive.file` does not automatically grant every child file, so a future spike has to prove create, list, edit, and an externally changed YAML file before any scope is treated as sufficient.

Alternatives if that spike fails: a user-exported zip with no cloud account, or a different user-owned provider behind the same `TrainingStore` interface. No Google API is called from this module. Billing for a Google Cloud project would need its own confirmation. The personal infrastructure target remains about €10 per month or less, and Milestone 0 adds no hosted cost.

## M1 local contract experiment

`tools/storage_contracts.py` supplies a Python `StorageAdapter` protocol and a
single-threaded `MemoryStorageAdapter` reference fake. It supports immutable byte
reads, compare-and-swap updates, stable create keys, operation-ID replay, explicit
missing/conflict errors, and paginated change receipts. Opaque object handles and
revision tokens are adapter details, separate from portable domain IDs in content.

Repeated operation IDs return the original receipt, even after subsequent writes;
reusing an operation ID for another intent fails. A stale write never changes
remote bytes. The caller retains its attempted branch; durable conflict-branch
storage is not implemented. All fake state disappears on process exit. No
durability, multi-process concurrency, OAuth, permissions, folder grants, real
Drive behavior or production sync is claimed. Account/folder capabilities and
revoke flows remain pending the approved provider experiment. Conformance evidence:
`tests/test_storage_contracts.py`, run via the existing unittest/CI command.
