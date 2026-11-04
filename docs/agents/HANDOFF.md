# Shared technical handoff

## Verified baseline — 2026-11-04

- Repository: `LachimB/gym-assistant`, intentionally public. Confirmed personal
  author: `LachimB <17935459+LachimB@users.noreply.github.com>`.
- Privacy PR22 merged into main at `e2ef498b`; companion PR23 merged into the
  development branch at `d0790bb6`. Both reviewed heads passed all11 checks.
  PR26 on the clean implementation branch starts from sanitized main and preserves the
  reviewed companion tree. It supersedes PR21 without rewriting existing refs.
  Older unmerged PR21 commits fail the new per-commit publication gate.
- M0 accepted. M1 remains incomplete. Decisions #1–#97 and ADRs remain binding.
- ADR-0006 provides local reviewed Template publication and format2/history1.2.
  PR19 and PR20 merged after their required checks. Natural-completion regression
  covers absent active phase, durable reopen and explicit acknowledgement.
- ADR-0007 approved: explicitly selected finalized personal-session delivery,
  immutable payloads, separate destination and additive Room3→4 after tests and
  a new verified private backup. No automatic send, sharing, AI operation or
  billing expansion. This does not complete full synchronization or M1 acceptance.
- PR21 candidate includes system-bar-safe controls and explicit dialog waits.
  Recorded local baseline:49 JVM,28 SQLite,73 Python checks and23 schema fixtures.
  API31 recovery/import/publication/preview passed. Full current-head seven-version
  CI remains required; do not infer newer-OS acceptance from local results.
- Newer verified development evidence (`d48acc4`): PR21 head `74bc0392` passed
  all10 checks, including seven-version Android coverage. Physical version9/Room4
  upgrade was verified with prior application records/archives preserved. No
  personal destination or live upload created. Do not repeat the migration or
  operate/restart/update the phone during the current external acceptance gate.
  Detailed source characteristics, personal state and private backups stay outside Git.
- Direct API budget allocations and SDK-internal request accounting remain
  separate; preserve the existing reservations. No spending increase authorized.

## Publication privacy remediation

- Public records contain synthetic fixtures, implementation/test results and
  unresolved acceptance gates only. Detailed prior evidence is preserved outside
  Git in `<private-evidence-directory>` with restricted permissions.
- Do not restore account addresses, live provider IDs, private paths, private
  Template characteristics, personal outcomes, screenshots or conversation logs.
- Read `docs/operations/publication-privacy.md` before every public write. Run the
  content guard and secret scanner before staging/pushing. No tool guarantees
  complete detection; manual review remains mandatory.
- Original project code is all rights reserved, as selected by the owner. MIT may
  be considered at release; no future relicensing is implied. Third-party terms
  remain separate; see `THIRD_PARTY_NOTICES.md`.
- Historical commits still contain earlier disclosures. A coordinated rewrite
  and force-push require a separate explicit approval after reviewing the plan.
- Owner manually verified Restricted and owner-only access for the synthetic
  folder and all descendants, and enabled both GitHub email privacy settings.
  These are owner-reported checks; no sharing permissions were changed.

## Next work

1. Complete protected CI and merge clean implementation PR26.
2. Retain old refs; historical erasure requires separate reviewed approval.
3. Complete full offline/interruption/synchronization/web acceptance without
   publishing personal inputs, characteristics or outcomes.

## Privacy validation — 2026-11-04

- Python80 tests, Node19 transport/configuration tests,23 schema fixtures and
  deterministic golden trace pass. Android50 JVM tests pass; no APK assembled or
  phone installation performed by this privacy task.
- Worktree and all-ref Gitleaks scans pass with full redaction. The content guard
  passes; negative tests cover index/worktree mismatch and historical disclosures.
- Noreply preflight verified personal login, exact public remote and local author.
- Owner approved main-branch protection: required PR and privacy check, strict
  up-to-date status, administrators included, force-push/deletion disabled. Applied
  and verified through GitHub. PR21 must incorporate this privacy fix before merge.
- Owner manually verified Restricted access for the approved synthetic test
  folder and all descendant folders/files. Independent UI/grant inspection was
  unavailable. No Drive permissions, shares, grants or credentials changed.
- Historical cleanup plan: `docs/operations/history-privacy-cleanup.md`. No rewrite,
  force-push, ref deletion or garbage collection performed.

The standalone privacy PR did not import the delivery implementation. The
clean replacement preserves the reviewed implementation and private evidence
boundaries; its new commit range passes the publication gate.

Dependency/license inventory drift now fails privacy CI until a review record
is updated. This does not grant binary-release clearance.

## GitHub merge attribution follow-up

The merged trees match their validated PR heads. Both merges use the same owner's
legacy public noreply alias and GitHub's service committer; no real contact
address was exposed. The initial gate rejected that alias as a false positive.
The follow-up accepts only the two known public aliases of the confirmed owner;
local commit identity remains pinned to the numeric alias. Merge instructions
avoid the GraphQL authorEmail field and require post-merge metadata verification.
