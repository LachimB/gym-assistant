# Durable publication progress and original head observations

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
This internal file/pointer layer does not change Room5, recording schemas,
library defaults or a running Workout Session.

## Retained facts and transitions

The additive `publicationProgress` record links a completed `workingProgress`
pointer and its original captured source. Reopen verifies every linked hash and
recompiles that source through the existing strict loader. Unknown formats,
cross-job links, duplicate receipts, aliased IDs and mismatched revisions fail.
Original records are never rewritten. Only an explicitly observed expected head
or verified initial absence can start this stage; unknown is not absence.

Reserved immutable source/manifest IDs, revision-folder ID, exact expected and
intended head, stable account and binding fingerprint remain fixed. Every file
receipt must match the captured ID/hash; repeated identical receipts reuse the
pointer, while changed facts stop. A repeated identical begin reuses the saved
stage rather than generating another publication identity.

Head snapshots now carry defensively copied, bounded original metadata and
content bytes, separately from the decoded semantic head. The journal validates
their agreement and native namespace/access facts, then retains the exact bytes
by hash. Distinct later competing or missing heads append observations. They do
not erase an earlier verified publication. Original byte order/whitespace remains
available; receipts contain no token or credential.

All immutable receipts and an observation of the exact intended head/tag are
required before retaining a publication receipt. That receipt does not declare
the latest head current, complete the whole job, adopt a downloaded source or
change a future default. The list and record byte bounds stop instead of trimming
facts. Invalid/unsupported raw-response quarantine remains separate integration
work; malformed observations cannot masquerade as valid heads here.

## Transaction and recovery boundary

The store checks the current lease, frozen job/source, enabled binding, active
root/account and exact namespace identity. File writes, fsync and full compilation
finish outside SQL. A short transaction rechecks the original pointer/scope before
advancing it, then rechecks lease expiry before commit. An expired owner,
retargeted binding or injected failure cannot acknowledge the candidate. Inert
file evidence may remain after rollback; it is never automatically cleaned up.
The source-revision SQL check selects only its two hashes, avoiding full source/
plan payload reads under the transaction. No network runs in these transactions
and no library/head binding is advanced.

Local114 Android JVM/35 adapter JVM checks pass and instrumentation compiles.
Seven new file-journal tests cover strict capture, reopen/raw-byte retention,
idempotency, competing/missing heads, false acknowledgement, tampering and bounded
scope. A core snapshot test verifies copied bytes and limits. Four SQLite cases
cover reopened publication, repeated begin, pointer rollback/retry, lease expiry
and namespace retargeting, comparing all twelve pre-sync tables unchanged. Those
SQLite cases require actual current-head execution on the seven-version CI matrix;
local compilation alone is not execution evidence.

Native publication-head transport/coordinator, immutable journal bridging, remote
capture, worker authorization/budget integration and source review remain open.
No local APK assembly, phone installation, migration, new live request or allowance
ran for this source component. Actual native/background/screen-off acceptance and
M1 remain incomplete.
