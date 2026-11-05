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

Prepared diagnostic snapshot: `16ce6048afe1d73cb7feb4e23bdd8caa85814421`,
based on `e66847fb68147e07c912799b35068d9caed4daaa`. The manual-only workflow
checks out exactly that snapshot and targets PR51 at
`a97faab93190ccaeb7af3e9053a3b841ec4fc75b`, with the same pinned base. It refuses
a changed target before calling the existing assessment. Only the confirmed
owner may dispatch it. Four additional offline tests validate those guards and
unchanged permissions; actionlint passes.

The workflow override is retained only on the isolated diagnostic branch. It
will not be merged as a replacement for the ordinary event-driven workflow.
Approval would permit pushing this reviewed branch and one manual run, then
inspecting its content-free result. No retry or merge-protection change is included.

AGENTS.md rule 4 requires approval for this security-sensitive change. If the
target or diagnostic code changes, refresh the review before execution. A failed
diagnostic does not authorize retries or protection changes.
