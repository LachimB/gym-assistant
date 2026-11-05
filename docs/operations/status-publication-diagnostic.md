# Proposed bounded status-publication diagnostic

Status: prepared for owner review; adoption and privileged execution are not approved.

The source scan and exact-head review can pass while publishing the resulting
GitHub statuses fails. The current controller withholds all exception details and
correctly blocks the merge, so it cannot distinguish HTTP, parsing or transport
failures. No root cause has been established.

The proposed change adds only a fixed diagnostic category, validated context and
state, and an HTTP numeric code when available. Exception text, URLs, bodies,
headers and credentials remain withheld. The original exception propagates;
scanner, receipt, status payload, retry behavior and fail-closed decisions remain
unchanged. Offline tests cover success, validation and each error category.

A separately reviewed manual workflow may run the exact diagnostic snapshot once
against one pinned open PR, head and base. It must execute no PR checkout/code,
repeat all existing scan/review/concurrency checks, retain minimal API permissions
and preserve every required branch check. No manual success status or merge
bypass is permitted. The default-branch-only workflow remains unchanged on main.
The exact snapshot and target identities must be presented before approval.

AGENTS.md rule 4 requires approval for this security-sensitive change. If the
target or diagnostic code changes, refresh the review before execution. A failed
diagnostic does not authorize retries or protection changes.
