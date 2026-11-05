# Native history-first transfer composition

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
The explicit native batch now composes immutable history, strict working transfer
and delayed head publication through the durable leased journal. It does not
enable a worker, create namespaces or select/adopt a local source.

The working socket profile accepts only captured metadata/media reads, reserved
new-file identities and exact intended YAML bytes. Existing writes require their
captured strong tag, MIME and body; no wildcard, changed body, folder media or
fallback create for an existing file is allowed. Bounded reads check owner-only
effective access and stable metadata/content/metadata tags. Original malformed,
missing or divergent responses reach the mandatory durable sink before decoding.
Retention failure stops before a mutation. Legacy transport defaults stay narrow.

History-first execution requires a read-only head preflight before the first
mutation and again before editable transfer. A valid competing/missing head is
retained before conflict is reported; malformed head bytes remain quarantined.
An already-intended head permits recovery only with complete immutable/working
receipts. A read-only transport cannot mutate even if both vectors verify.
This remains a point-in-time check, not an atomic folder or editing lock.

Initial namespace-role validation can run with incomplete working receipts, but
both head-write transport constructors still reject that state. The actual head
batch opens only after durable receipt completion and current capture/lease
validation. All token-bearing batches close after failure or completion. The
optional internal injected HTTP seam is used only by synthetic instrumentation;
normal callers retain the fixed-origin socket profiles and request callback.

Eight new native JVM regressions cover strict source writes/readback, same-ID409/lost
acknowledgement, stale412, missing existing files, owner/access failures, malformed
raw retention, exact socket bytes, incomplete head scopes and read-only mutation
refusal. Two further regressions verify canonical SHA-256 vectors and fresh-file
tamper/deletion/size/malformed-input checks after syntax reuse. Local142 Android
JVM/36 adapter JVM checks pass; instrumentation compiles.
Three new SQLite cases cover the composed native protocol across close/reopen,
same-ID recovery and delayed head opening, plus missing/conflicting preflight
refusal before any source mutation. The full fake counts every direct call before
execution and stops at400; this offline bound does not authorize live requests.
Current-head execution across all seven Android versions remains required.

Repeated private-record reads retain fresh file bounds and hash verification.
Only syntax for those identical verified bytes is reused, bounded to2048 entries
and4MiB of text. Source/compiler, lineage, scope, lease and SQL checks are never
cached. Canonical lowercase SHA-256 bytes remain unchanged while hex conversion
avoids per-byte formatter allocation. Reopening still checks the full audit chain.

Verified initial/base remote capture, binding/job completion, account/allowance-
bound authorization/scheduling and source review remain open. No Room or recording
schema, dependency, default or timing change. No local APK assembly, phone install,
live request, new resource or allowance; all approved authorization launches remain
used. Native provider/physical/background/screen-off acceptance and M1 remain
incomplete. A separately reviewed live plan and existing CI/backup/device gates
precede any actual native batch.
