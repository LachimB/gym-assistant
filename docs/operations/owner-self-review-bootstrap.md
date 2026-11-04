# Owner self-review activation plan

Status: activated with owner approval on2026-11-04. PR32 merged at `02103c8`;
all four required contexts were immediately restored and classic protection was
verified unchanged. Only the policy PR merged during the bootstrap window.
Live receipt validation follows separately on the M1 foundation PR. The sequence
below records the reviewed activation plan. See `continuous-security.md` for the
final exact-receipt rules.

## Permanent change

The trusted controller accepts a COMMENTED review receipt only from LachimB on
that owner's PR in this repository, with current administrator permission and
the exact head/digest. Other authors require independent APPROVE review receipts.
Every PR requires a trusted receipt, including an otherwise unflagged PR.

GitHub cannot accept a native self-approval. Change only the ruleset's native
minimum-review count1→0, code-owner-review true→false and last-pusher-approval
true→false. The trusted required privacy status replaces that review enforcement.
Keep PRs, resolved threads, strict up-to-date checks, administrator enforcement,
all four existing required checks and their GitHub Actions publisher pins,
zero bypass actors, force-push/deletion protection and all scanner rules.

## Why a one-time bootstrap is required

The current trusted workflow intentionally runs only main's controller/policy.
The proposed policy cannot authorize its own adoption. Under the current rule,
the owner's policy PR therefore remains blocked despite the requested exception.
Do not execute PR code as trusted, forge a passing status or use administrator
merge bypass to conceal that boundary.

Before activation, obtain owner approval for the concrete policy PR head and this
temporary waiver. Prepare private exact before/during/after ruleset payloads from
freshly fetched settings. Stop on unexpected settings or a changed PR head.

1. Inspect the exact policy diff and require all technical CI, the existing
   publication-privacy check and trusted secret/hygiene statuses to pass. Verify
   that the remaining trusted privacy failure is review-only, with complete scans
   and no unresolved secret, strict privacy or hygiene failure.
2. Retain the original ruleset privately. Apply the three native review settings
   above and temporarily omit only `Security / privacy` from required contexts for
   this policy bootstrap. Keep the other three required contexts, publisher pins,
   PR requirement and every other rule unchanged. No bypass actor is added.
3. Merge only the exact approved policy head through its ordinary PR after all
   technical checks pass. Do not merge any other PR during the bootstrap window.
   If the merge fails, restore the original settings immediately and stop.
4. Immediately restore `Security / privacy` to the required contexts with its
   original publisher pin, retaining the three permanent review settings. Fetch
   and compare the complete ruleset. Restore original settings on a pre-merge
   failure; after a successful merge, prioritize restoring the final protected
   state and stop if verification cannot complete. Never leave a waiver pending.
5. Dispatch the default-branch trusted workflow. Verify on the separate open M1 PR
   that absent/stale/invalid receipts fail and a valid owner receipt passes only
   contextual review. Secret/hygiene/strict privacy failures remain blocking.
   Do not merge M1 or install its migration until its current-head CI and normal
   required statuses pass. No phone, Drive, collaborator or billing change occurs.

This is an explicitly bounded administrative transition, not an atomic GitHub
transaction. The owner must approve the temporary review-status waiver separately;
source preparation does not authorize its application. Keep detailed settings
snapshots and exact operational evidence outside Git.

Local validation:118 Python tests pass, including38 trusted-gate regressions.
The new cases cover owner-only COMMENTED receipts, exact head/digest, administrator
permission, repository binding, cancellation/stale reviews, no-review failure
even without findings, and unchanged required-check/protection configuration.
