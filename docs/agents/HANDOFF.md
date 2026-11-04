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

1. PR26 merged at `25a8c23` after all11 checks passed on `2e3ae59`, including
   all seven Android versions. PR21 is closed; its branch/history is retained.
   The clean checkout matches verified main. No history rewrite or ref deletion.
2. Retain old refs; historical erasure requires separate reviewed approval.
3. Complete full offline/interruption/synchronization/web acceptance without
   publishing personal inputs, characteristics or outcomes.
4. ADR-0008 was approved after explanation for explicit read-only web access to a selected personal Drive
   recording. Implementation is in progress; private connection and budget settings stay
   outside Git. No database/session schema change; no live operation by drafting it.
   Detailed current acceptance evidence remains outside Git. Manual-file web
   reading does not prove direct Drive integration or Template synchronization.

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

## Approved direct web reader continuation

ADR-0008 is approved. The candidate adds an isolated GET-only personal reader,
explicit owner-driven Google authorization/Picker, bounded streaming and existing
independent history validation. A loopback-only helper serves private connection
settings and durably increments approved allowance counters before transmission;
it receives no tokens or recording content. Public defaults stay disconnected.
Local checks:38 web tests,2 loopback security/budget tests, production build and
privacy/license checks pass. Full current-head CI and actual browser/live selection
remain required. No Android build, phone migration or live read by implementation.
See `docs/operations/m1-web-personal-drive-reader.md`; detailed inputs/results stay
outside Git. Deferred timing-format styling is not changed.

## Direct reader continuation — browser fetch receiver fix

PR27 is open; its published head is `bbeb18b`. Do not merge that head as accepted:
a browser-native fetch receiver problem was reproduced with local-only HTTP
(native fetch as an instance method throws TypeError; correct global receiver
returns200). Injected transport tests had hidden this browser calling convention.
The local adapter now invokes fetch through the global receiver, has a dedicated
regression, uses base AbortController cancellation, and distinguishes transport
failure from malformed metadata.38 web tests and the production build pass.
The follow-up fixes are retained on this branch; require all current-head checks
and the private acceptance evidence before protected merge.

Synthetic browser HTTP validation and public-default disabled-access checks pass.
The approved loopback4173 origins and matching Picker referrers were saved and
verified with prior restrictions retained. Private connection/budget inputs live
outside Git; never recreate/reset them. The identified old project preview was
replaced by the loopback helper. Do not restart/update the phone for this web task.

Normal approval review is available again. Private configuration and durable
allowances remain intact. Never reset them or infer unmeasured SDK calls from
the direct-request counters. Inspect only non-content status during live checks;
real source/event text needs separate per-operation AI consent. Keep detailed
acceptance evidence outside Git. Full M1 and Template synchronization remain open.

## Continuous security gate implementation — 2026-11-04

- Isolated branch `codex/continuous-security-gate` starts from freshly fetched
  `origin/main` at `adac6c2`. Other worktrees and their uncommitted acceptance work
  remain untouched. This change affects publication controls only.
- Adds trusted default-branch Git-object scanning and stable separate secret,
  contextual privacy and hygiene statuses; preserves the existing `privacy` job,
  strict public attribution/content policy and license inventory check.
- New scans cover all introduced commits, full changed contents and every parent
  preimage. Gitleaks is version/checksum pinned. PR overrides have no authority.
- Metadata controller scans paginated PR text/comments/reviews, never executes PR
  code, and requires exact independent review receipts for contextual findings.
  Unsupported linked/binary materials and platform timing limits are documented.
- Prepared CODEOWNERS, narrow reviewed exceptions, local checks and a ruleset
  example. No remote rules/settings, resources, billing or permissions changed.
- PR28 is the draft implementation review. Existing contracts CI now installs
  the pinned scanner before discovering security tests; read-only privacy CI
  installs the existing Python test dependencies. GitHub validation is in progress.
- Activation and independent reviewer setup remain administrator/reviewer actions;
  see `docs/operations/continuous-security.md`. Current owner-only reviewer policy
  cannot self-approve owner-authored flagged changes. Initial rollout needs review.
- Local validation:115 Python tests (35 new security regressions),23 schema
  fixtures and deterministic golden trace; privacy/license guards and redacted
  Gitleaks pass. Checksum-verified actionlint validates both security workflows;
  shell syntax passes. All15 requested security test categories are covered.
- Read-only GitHub verification found baseline main green on all11 checks, no
  open PRs, and existing strict `privacy` protection with administrators included,
  force-push/deletion disabled. Approvals/code-owner enforcement still need rollout.
  Live fork/events/required-merge behavior remains unverified until activation.

## Verified web integration and proposed next boundary

PR27 merged at `adac6c26` after all11 named checks passed on `1c165a76`,
including seven Android versions. Merge metadata/content guard passes; the clean
checkout was advanced to the exact merged tree before creating the proposal branch.
Direct-reader detailed acceptance evidence stays outside Git. No additional live
selection or budget reset is needed for that completed feature verification.

ADR-0009 is proposed for selected Template YAML publication, immutable revisions,
external-edit candidate review and additive Room4 to5 sync bookkeeping. No schema,
phone migration or cloud Template operation has been implemented by drafting it.
Await owner approval before those changes. Preserve all approved connection/budget
bindings; the proposal describes a separate bounded reservation, not a reset.
Full-workout acceptance, Template sync and remaining M1 gaps stay open.

## ADR-0009 review decisions

The owner confirmed review before adoption and one stable Current working copy
folder per Template, with historical revisions separate. All app-owned Drive
artifacts must be nested under one common project folder, including personal,
synthetic/test, session, metadata/staging and backup areas. No additional top-level
app folders. The draft now describes guarded per-file updates and incomplete-sync
recovery, and a proposed common-root registry. Full ADR approval is still pending.
Common-root selection and consolidation of existing areas need a concrete reviewed
plan; drafting this requirement did not move/create any Drive folder, change
permissions, retarget connections or implement a phone migration.

The common Drive root display name is confirmed as Gym Assistant. This is a
design decision only; whole-ADR approval and the concrete consolidation plan remain
pending. No root creation, folder move or connection change was performed.

ADR-0009 review now includes background and screen-off Template synchronization.
The proposed contract uses persistent constrained work, durable job reconciliation,
foreground/background serialization and in-memory tokens from existing Google
Play authorization. Interactive resolution pauses work until owner reconnects;
Android scheduling/Doze delays must be shown truthfully and tested. This supersedes
the earlier foreground-only scope in the draft. Network policy remains to review.
The ADR as a whole is still proposed; no scheduler dependency, schema migration,
background permission, cloud operation or phone update was implemented by review.

## Paused ADR review checkpoint

Work is paused at the owner's request. ADR-0009 remains proposed, not approved for
implementation. Resume the review at the network-policy choice: Wi-Fi and mobile
data versus Wi-Fi only for Template YAML/metadata synchronization. Then finish the
remaining design review and concrete common-root consolidation plan before asking
for whole-ADR approval. The confirmed layout, root name, review-before-adoption
and background/screen-off decisions are retained in ADR-0009. This checkpoint adds
no application code, schema migration, background job or cloud operation.

## Approved security rollout review — 2026-11-04

The owner approved reviewing/merging PR28 and activating the prepared repository
protections. Review of head `44dc714` found no blocking security/privacy issue; all
11 CI checks passed, the required privacy run passed again, and all35 security
regressions passed locally. The latest main merge `83b4c13` added only the paused
ADR-0009 proposal/handoff; both handoff sections are preserved in the integration.
No ADR-0009 implementation or external operation is authorized by this security task.

Rollout adds the ruleset alongside existing classic protection, with no bypass
actors. Preserve strict required privacy, administrators included, and disabled
force-push/deletion. Live activation must verify all three Actions-published
security statuses on an unmerged synthetic PR, negative metadata controls and
recovery. The existing owner-only reviewer list cannot self-approve flagged
owner-authored changes; a separate consenting trusted reviewer remains necessary.
Final configuration and smoke-test evidence belong in the PR28 rollout record;
never treat this pre-activation handoff as proof that remote settings are active.

## Owner self-review policy change — preparation

The owner explicitly requested relaxing the self-approval prohibition for
owner-authored PRs. The separate policy branch adds an exact COMMENTED-review
receipt exception only for the confirmed repository owner with administrator
permission, bound to the current PR head/digest. GitHub does not permit native
self-approval. All PRs must still receive a trusted exact receipt; other authors
need independent APPROVE reviews. Native review-count/code-owner/last-push gates
are replaced by that trusted-status enforcement; four required checks, no bypass
actors, strict up-to-date settings and force-push/deletion protection remain.

No live ruleset, required check or collaborator permission was changed by preparing
this source. A reviewed bootstrap remains necessary: default-branch policy cannot
self-adopt proposed policy code. Present the concrete exact-head bootstrap plan
before any temporary review waiver or merge. M1 foundation PR31 remains separate;
continue its current-head CI and return to ADR-0009 after policy activation.
