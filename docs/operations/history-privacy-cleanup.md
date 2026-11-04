# Historical privacy cleanup plan — approval required

Forward sanitization does not remove disclosures in earlier commits or PR diffs.
The user authorized public-file remediation and future noreply attribution. No
history rewrite, force-push, ref deletion or garbage collection has been authorized.

## Prepared scope

- Repository: `LachimB/gym-assistant` only. Default branch main plus every affected
  remote branch/tag discovered at execution time; exclude unrelated repositories.
- Preserve a private full-ref bundle, current remote-ref manifest and each active
  worktree's branch/HEAD/uncommitted state before any transformation. The existing
  private evidence archive is not a substitute for a complete Git recovery bundle.
- Work in a separate private mirror outside all active checkouts. Dry-run the
  approved redactions on every historical text blob and commit/tag message.
- Apply the same public sanitization policy to historical documents/source pins.
  Preserve code/acceptance semantics and synthetic tests. Replace personal author
  attribution with the approved noreply identity; preserve legitimate third-party
  attribution and hosting-service metadata. Do not publish replacement maps.
- Scan all resulting refs/objects, inspect changed trees and representative history,
  and verify code/fixture behavior. Record only counts and pass/fail publicly.
- Present the exact ref old→new mapping and affected worktree/PR recovery plan
  privately. Obtain explicit approval for the exact force-push batch. Coordinate
  active work before writing; use expected old refs as leases, never blind force.
- After approved remote updates, verify every ref and CI; coordinate safe clean
  re-clones/rebases. Do not reset or delete another chat's worktree or local changes.
- GitHub PR diffs/caches, forks, clones, Actions artifacts and other copies may
  retain old data. Review them separately; request hosting-provider removal where
  applicable. Do not claim Git rewriting can recall every published copy.
- Keep recovery material private. Deleting backups or pruning old objects requires
  a separate explicit decision after recovery verification.

Main-branch force-push protection, if enabled, must not be disabled without a
separate reviewed exception for this exact operation.
