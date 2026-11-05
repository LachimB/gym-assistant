# Shared technical handoff

Current checkpoint — 2026-11-05: baseline before this cutover record was `aea8c75`;
the approved folder cutover has run and its hierarchy/identity/access checks
passed. Final owner phone probe/retry and web opening/value comparison passed
after correcting a retired-key reference. Verify current PR/merge/CI
state on GitHub under the startup rule. Read the latest
“Approved folder cutover” section below and `docs/milestones/m1-drive-consolidation.md`
before acting on older pending-gate notes. M1 remains incomplete.

Next preparation: the synthetic YAML media proof below follows the accepted
cutover. It has no live approval/reservation yet; preserve that boundary.

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

## Resumed ADR-0009 review

The recovered checkout was verified clean against live main at `68c1007` before
resuming review. The owner confirmed both Wi-Fi and mobile data for Template YAML
and sync metadata, including background/screen-off transfers. Media policy remains
separate. This resolves the earlier pending network-policy question; the earlier
pause checkpoint is historical. ADR-0009 remains proposed as a whole. No scheduler,
schema, phone update or cloud operation has been implemented by this review.

The owner confirmed field-by-field conflict resolution for ADR-0009. Preserve the
common base and both original branches, show each conflicting phone/Drive value,
validate the complete resolved candidate and require final review before adoption.
Structural ambiguity remains blocked rather than choosing a whole-version winner.
Whole-ADR implementation/live-operation approval remains pending.

The additive migration approach in ADR-0009 is confirmed: required tests and a
fresh verified private backup precede phone installation, followed by preservation
checks. No uninstall/clear. This is a design-review decision; whole-ADR approval
and pre-installation gates remain pending. Continue with the bounded live-test
allowance and concrete common-root consolidation review.

The ADR-0009 live-test allowance is confirmed: up to200 additional direct Drive
attempts within the existing500 allowance and6 explicit authorization launches,
without counter resets, billing changes or consumption by the review itself.
The proposed consolidation sequence is now written for review: compatible clients
first; a private exact-ID/source-parent/sharing inventory and owner preview; one
verified Gym Assistant common root; parent moves retaining existing area names,
IDs, markers and contents; then hierarchy, sharing and existing-client verification.
Templates are nested below the retained personal area. No legacy rename/copy/delete
is proposed. Whole-ADR approval and private inventory review still precede live
creation/relocation; no such operation was performed by writing this plan.

The owner confirmed the consolidation plan as the intended approach. All walked-
through design choices are recorded; final whole-ADR approval remains pending.
The exact private folder inventory still needs owner review before live relocation.
The revised proposal makes periodic discovery explicit at15 minutes (Android may
defer it), with checks on opening/Sync now and one-off approved local publications.
Next: request whole-ADR approval for implementation/testing under the reviewed
scope, retaining required CI/private-backup and exact-inventory gates. No code,
phone, Drive, budget or scheduler change occurred during this documentation review.

## ADR-0009 approved implementation continuation

The owner approved ADR-0009 as a whole after the walkthrough. This supersedes the
older proposed/pause entries for this work. Implementation starts on the recovered
repository, freshly verified at `68c1007`, preserving the accumulated review edits.
Keep exact private folder inventory review before live relocation, and successful
CI plus a fresh verified backup before any phone database update. No allowance is
reset or consumed by approval. Begin with provider-neutral immutable contracts and
field-level conflict resolution; durable Room5 jobs, guarded Drive operations,
background scheduling/UI and full acceptance follow. Whole M1 remains incomplete.

## ADR-0009 contracts and persistence foundation

The initial implementation adds revision/head1.0 contracts and synthetic fixtures,
strict source/compiled-hash verification, stable-field three-way merge primitives,
guarded per-file transfer protocol, immutable private intents/before-images and
additive Room5 persistence with serialized lease generations. Existing12 tables
and delivery meanings remain unchanged. Backup tooling includes all16 tables and
sync files. No worker or UI enables live synchronization yet.

Local validation:57 Android JVM,20 adapter JVM and119 Python tests pass; engine
JVM checks pass and Android SQLite test sources compile. No local AVD is configured;
current-head seven-version CI remains required. Privacy/Gitleaks/manual review
precede publication. No phone installation, live Drive call, folder relocation,
authorization acquisition or allowance reservation occurred. See
`docs/milestones/m1-template-sync.md` for implemented scope and outstanding gates.
Continue with durable execution, lossless source mapping, provider integration,
WorkManager/auth/budget reconciliation, source review UI and full device evidence.
Preserve the exact-inventory and CI/fresh-verified-backup gates.

Foundation PR31 is open. Its trusted secret/hygiene checks passed; the trusted
privacy status requires independent contextual review. The policy currently
allowlists only the owner and forbids self-approval of an owner-authored PR.
An independent maintainer must be nominated before preparing a reviewed policy/
access change; no collaborator invitation or protection/policy change is authorized
by the ADR approval. Do not merge or install the migration while this gate is open.
Complete current-head CI and fix technical failures while awaiting that decision.


CI exposed an optional-schema-field assumption in the new populated migration
fixture loader: Room omits indices for unindexed tables. The fixture now accepts
that omission; actual migration SQL is unchanged. Rerun the complete seven-version
matrix on the fix. Separately, the owner explicitly requested an owner-only
self-review exception; its policy/activation plan is on the separate
`codex/owner-self-review-policy` branch. No live ruleset change is applied.

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


## Owner self-review activated — 2026-11-04

The owner approved the concrete bootstrap after a plain-language explanation.
PR32 merged at `02103c8`; only that policy PR was merged in the temporary window.
All four required contexts and publisher pins were immediately restored. Classic
protection is unchanged, including administrator enforcement, strict up-to-date
checks and disabled force-push/deletion. No bypass actor, collaborator, phone or
Drive operation was added. Earlier independent-review setup gates above are
superseded by the approved owner-only exact-receipt policy. Live receipt validation
is in progress; never label owner review as independent.

PR31 head `06470a3` passed all technical checks, including all seven supported
Android versions after the fixture-loader correction. Integrating current main
requires new-head CI and an exact fresh receipt before merging. All earlier
private-backup/physical-installation and exact Drive-inventory gates remain.

## Lossless YAML candidate adapter

The next approved ADR-0009 slice maps validated YAML scalar fields using declared
stable source IDs. It preserves all three original branches, patches only explicit
resolved values/new revision identity and compiles the complete candidate again.
No library head or active session is changed. Source comments, Unicode positions,
CRLF and block-header comments are retained. Comment/layout differences and arrays
without declared stable identities remain structural conflicts for source review;
existing BOM-prefixed import rejection is preserved rather than normalized.

Local Android65 JVM checks pass, including8 new lossless-merge regressions; Android
SQLite instrumentation still compiles. No dependency, schema, phone or Drive change.
Full candidate-review UI, durable remote execution and background scheduling remain
pending. Final CI and a fresh exact owner receipt precede this slice's merge.


Live owner-review validation is complete: invalid/stale receipts stayed blocked;
the exact current receipt passed. PR31 merged normally at `f0cbd6a` after all checks,
including all seven Android versions, passed on `eb56fca`. Full final ruleset and
classic protections were fetched again and verified unchanged. The one-time
waiver is closed. Current work is the separate lossless-YAML candidate slice;
future worker/provider/UI work and all physical/live acceptance gates remain.

## Durable working-file progress — 2026-11-05

PR33 merged at `d490bb1` and PR34 at `99af7f3` under the full protections after
all technical checks and exact owner receipts passed. The current implementation
adds immutable progress records linked to the original captured source, plus a
leased compare-and-swap of the private job pointer. Every previous receipt and
before-image is retained. Duplicate acknowledgements are idempotent; altered
facts, source/working-ID overlap, changed bindings and expired ownership fail.
No network or file work runs inside the pointer transaction; all12 pre-existing
tables and recording meanings remain separate. Schema5 is unchanged.

Local Android81 JVM checks and instrumentation compilation pass. Four additional
SQLite recovery/rollback/isolation cases await the current-head seven-version CI
matrix. No APK installation, phone migration, live Drive request, folder move,
token acquisition or allowance reservation occurred. Durable execution, offline
queue capture, whole-publication recovery, background scheduling and review UI
remain open, as do exact private inventory review and physical/live acceptance.

## Source-only offline publication queue — 2026-11-05

The next slice freezes an explicitly selected published revision into private
content-addressed source files and a bounded versioned local-publication intent.
It retains reviewed source/plan hashes, the existing base expectation and pinned
binding identity. It contains no reserved provider IDs or captured remote tags;
unknown remote state does not authorize creation. Enqueue checks enabled/active
account scope and the same selected head transactionally. Duplicate logical
operations keep one job; stale selection and reused identities fail. Reopening
recompiles exact bytes. This does not enqueue the whole library or alter sessions.

Local Android86 JVM checks pass and instrumentation compiles. Four added SQLite
cases require current-head matrix execution. Room schema5 is unchanged. The
queue is not wired to UI/background scheduling or remote capture. That capture,
reserved-ID persistence, immutable publication/head recovery and source review
remain open. No phone operation, live request, token or budget change occurred.

## Captured working-file execution — 2026-11-05

The leased IO executor connects the guarded per-file protocol to the immutable
progress store. Same-ID readback recovers an uncertain write; acknowledged files
are only verified on retry, never repaired over an external edit. Every result
retains source/binding/lease checks, and a final vector recheck precedes returning
working-file verification. Cancellation propagates through the synchronous
provider/Room bridge. This result does not mark a job complete or publish/adopt
an immutable revision/head. Five synthetic SQLite/provider regressions compile;
full current-head matrix execution is required. Local86 JVM checks pass.

Room schema5 and all previous tables are unchanged. No UI/worker activation,
token acquisition, live request, allowance reservation or phone operation occurred.
Verified remote capture for source-only jobs, immutable history/head publication,
compatible common-root clients, background scheduling and review UI remain open.

## Persistent constrained Template wakeups — 2026-11-05

PR35 merged at `7a0dcdd` and PR36 at `78a8c3a` after all required checks, including
seven Android versions and exact owner receipts, passed. PR37 is the separate
captured-file executor. Scheduling preparation pins WorkManager2.12.0, records
affected declared-license/POM hashes and preserves its original license asset.
Unique periodic/one-off wakeups use connected network and exponential backoff;
reconciliation follows enabled same-account active roots and cancels opt-outs.
Only local binding identity enters WorkData. Owner-action results stop that
wakeup's automatic retry; missing handler/invalid inputs fail closed.

Local86 JVM checks pass and five WorkManager test-driver/manifest cases compile. Full
current-head CI remains required. No application handler/startup/UI wiring exists
yet, and no live background/device behavior is claimed. Unused foreground-service
and diagnostic components are removed; ordinary network/reboot support remains.
No phone update, token acquisition, live request, budget reservation or root move.
See `docs/milestones/m1-template-scheduling.md`; publication/observation integration,
compatible root clients, source review and physical/live acceptance remain open.

## Conditional publication-head recovery — 2026-11-05

PR38 merged at `36a2db7` after all checks, including the seven supported Android
versions and exact owner receipt, passed. WorkManager test-driver/manifest cases
are synthetic scheduling evidence, not actual screen-off/Doze acceptance.

The next provider-neutral protocol captures exact head lineage/tag and pinned
namespace, requires verified immutable source and working-file vectors before
conditional publication, and recovers uncertain writes through the same ID.
It retains publication separately from later working edits or competing heads;
no rollback, unconditional fallback, local adoption or whole-job completion.
Adapter29 JVM/Android86 JVM checks pass and instrumentation compiles. Native
history/head transport, durable journal and end-to-end integration remain open.
See `docs/milestones/m1-publication-head.md`.

The approved bounded Template allowance was reserved once after reconciling
private phone/browser counters and original account/folder bindings. A metadata-
only inventory of the previously authorized areas passed; no contents were read,
folders created/moved, sharing changed or APK installed. Exact inventory,
remaining counters and evidence stay outside Git. The owner has now clarified
the environment layout below; the exact from/to execution review,
compatible clients and fresh verified private backup remain gates before moves
or phone migration. Never reset or reuse the earlier exhausted Picker allowance.


## Native working-file transport continuation — 2026-11-05

PR33 merged at `d490bb1` after all technical checks, the seven-version matrix and
its exact owner receipt passed. The next independent slice adds narrow native
Drive v2 tagged YAML reads/conditional updates and reserved-ID v3 creates, with
pinned account/common-root ancestry and private namespace markers. Existing
session-delivery allowlists are unchanged. Metadata/media/metadata snapshots
reject changing tags;412 has no unconditional fallback. Failed namespace checks
revoke access, and malformed ownership types fail before source download.

Local combined Android75 JVM tests pass, including10 new synthetic transport
regressions; SQLite instrumentation compiles. No real token, Drive request,
allowance allocation, folder mutation, local APK build or phone update ran.
See `docs/milestones/m1-drive-working-files.md`. Live YAML conditional-write proof,
durable progress/head publication, source review UI and background scheduling are
still open. Preserve exact private inventory approval and phone CI/verified-backup
gates; use a fresh receipt/current CI before merging this slice.

## Environment layout confirmed and work resumed — 2026-11-05

The owner resumed work and requested updating ADR-0009 with one Gym Assistant
root containing `dev`, `test` and `prod`. Existing areas become children:
`dev/PERSONAL M1` and `test/SYNTHETIC M1`, with concise display names that omit
the repeated project/environment labels. Future areas follow the same pattern.
The environment containers do not replace the existing area identities. Preserve
IDs, contents, namespace markers, sharing and saved connection/job bindings.
Production subfolder structure and any data promotion remain deferred.

This supersedes the earlier Personal/Development layout and initial no-rename
rule. The common root does not currently exist; no additional existing-root link
is needed. A private metadata inventory is retained, but its older destination
mapping must be revised. Compatible clients must support the exact captured old
and reviewed final names/parents for the same IDs during adoption; no prefix/
wildcard fallback. Exact execution review, current CI and a fresh verified backup
still precede their live operations. No folder creation, move, rename, sharing
change or phone update has occurred. Continue the approved implementation.

## Compatible environment clients — 2026-11-05

PR39 merged at `a6c4e41` after all required checks, the seven-version Android
matrix and the exact owner receipt passed. ADR-0009 now records the approved
dev/test/prod structure and simplified existing-area names. Work continues from
that verified baseline without changing any existing provider identities.

The compatible-client change adds a bounded private layout with exact account,
root/environment/area pins and reserved/verified/adopting/active/blocked phases.
Phone personal/synthetic delivery and metadata probes verify that hierarchy;
simplified names require verified project evidence. Existing setup/job identities
remain fixed and ordinary recording delivery cannot create a new top-level area.
The web reader verifies root/dev before recording reads, with layout-hash-bound
localhost budget grants that reject stale configuration without resetting usage.
No dependency or Room schema changes; the Gradle inventory hash changes only for
the private layout configuration field.

Local Android98 JVM, adapter29 JVM, web45 and helper3 checks pass, along with web
build and instrumentation compilation. Current-head CI and the full emulator
matrix remain required. No live request, token acquisition, folder creation/move/
rename, sharing change, APK installation or root activation ran for this change.
See `docs/milestones/m1-project-layout-clients.md`. Finish conditional consolidation
and its private execution review, preserve original identities and allowance,
and use a fresh verified backup before the approved phone database update.

## Compatible client adoption and next execution gate — 2026-11-05

PR40 merged at `a3cb5ac` after all required checks, including the seven-version
Android matrix and the exact owner receipt, passed on `dad223b`. The installed
private compatible build passed physical4→5 integrity/foreign-key checks after
a fresh verified private backup. All twelve prior tables, retained app-file
archives and original request counters compared exactly unchanged. Previous APK,
before/after backups and detailed evidence remain outside Git. App reopen did
not resume a workout. No root binding was activated or Template opted in.

Bounded metadata-only setup retained future common/environment and labelled
conditional-proof identities privately. No folders were created, moved/renamed
or shared. The exact private execution review now includes the existing source
IDs, reserved target IDs, empty-folder conditional/stale-write proof, preserved
descendants/connection identities and bounded use of the original reservation.
Owner execution review still precedes any root creation or relocation. Stop if
conditional folder metadata updates cannot be proved; do not force a move.

The small reader-helper follow-up supports only equal or lower per-allocation
caps beneath its existing maxima; it cannot revive an exhausted earlier ledger.
Four local synthetic helper checks pass. A later verification allocation must
be explicitly reserved from the approved parent before use, with the original
allocation/counters retained. No new live allowance was created by this helper
change. Remaining Template publication/review/worker integration and actual
background/screen-off/Doze acceptance remain open; M1 is not complete.

## Approved folder cutover — 2026-11-05

PR41 merged at `aea8c75` after all required checks, its seven-version Android
matrix and exact owner receipt passed. The owner then approved the exact private
consolidation execution plan. Its bounded desktop/web shares were reserved once
within the existing parent allowance; the earlier web connection and exhausted
ledger were retained unchanged.

A complete synthetic runner rehearsal passed before live operations. Metadata
preflight included nested descendants and owner-only effective access. Reserved
containers and labelled empty proof folders were created with their previously
reserved identities.
The live folder-metadata proof accepted the correct tag and rejected a stale
parent/name update with412 before either existing area moved. Compatible adopting
and final phone configurations were verified against fresh private backups with
all sixteen prior tables, retained files and counters unchanged.

The two existing areas were then conditionally moved/renamed beneath their
reviewed environments. Final metadata comparison retained original area/child
identities, markers and access; production is empty. No copies, deletion, sharing
changes, automatic recording upload or Template/background binding activation.
Detailed inventories, before-images, exact usage, APKs and backups remain private.
Final phone probe/retry and explicit web verification are still pending owner
acceptance; do not claim those passed without results.

The reusable bounded empty-folder metadata probe and four synthetic cases are
in `tools/drive_folder_metadata_probe.*`; CI runs them with the existing probes.
See `docs/milestones/m1-drive-consolidation.md`. Continue the remaining approved
Template publication/review/runtime work after these acceptance gates; M1 is
incomplete. Preserve review before adoption and never reset earlier allowances.

Cutover-record CI also exposed an existing private-source read/write race during
concurrent offline enqueue. The new two-store regression failed locally before
the fix. Owner-only directory/file modes are set atomically, removing the access
revocation window; reads also share the process-wide write boundary. Concurrent
JVM/SQLite cases verify these modes. Permissions, hashes, fsync, rename and schema
stay unchanged. Run the complete corrected-head
Android matrix; local99 Android JVM/29 adapter JVM checks pass and instrumentation
compiles. The27 synthetic Drive/helper safeguards passed. Do not rerun the old
test merely to mask this failure. Separate
upstream504 dependency downloads are infrastructure failures. The installed
cutover builds precede the coordination fix and have no enabled Template queue.

## Cutover client checks and reader configuration — 2026-11-05

PR42 merged at `cb15d62` after all required checks, all seven supported Android
versions and the exact owner receipt passed on `c202e4b`. The owner reports that
the moved synthetic-area phone probe and existing personal-delivery retry passed.
Do not repeat either successful check or any consolidation operation.

The web Picker stopped before opening a recording. Read-only inspection of the
approved Cloud project showed that the configured previous key had been deleted
after rotation. The private localhost configuration now uses the existing
replacement with the approved restrictions; no new key or Cloud change was made.
The failed child allocation is retained and reconciled once. The owner approved
one additional authorization/Picker attempt, raising ADR-0009's batch ceiling
from6 to7, and transferred one unused desktop metadata request to fund the
six-request retry alongside the failed batch's five unused requests. The80-request
consolidation and200/500 parent limits remain unchanged. Exact accounting and
operational configuration stay private. The owner completed that single retry
and confirmed successful opening and matching values. All six direct calls and
the authorization/Picker launch were reconciled once; the retry is exhausted.
All post-cutover client checks have passed. Do not repeat them, reset counters
or launch another selection under either retained web allocation.

The installed phone cutover build still precedes the source-read coordination
fix. No Template/background binding is active; M1 remains incomplete. Continue
the approved publication/review/runtime work after closing cutover acceptance.

## Synthetic YAML media proof preparation — 2026-11-05

PR43 merged at `58d4d94` after all required checks, its seven-version Android
matrix and exact owner receipt passed on `11fe326`. The folder consolidation and
all three client checks are accepted. The failed and successful web attempts
remain counted; both allocations are exhausted. No further authorization is
available under ADR-0009's current seven-launch batch ceiling.

The next required provider proof covers actual YAML content with the native
v2 media PUT/header shape and `text/plain` MIME, separately from the earlier
JSON and folder-metadata results. The new synthetic-only helper validates pinned
account/root/test/fixture identities, access, tags and exact fixed public bytes;
persists the original intent before writes; requires current-tag acceptance and
stale-tag412; and verifies retained bytes/tag. Lost acknowledgements inspect the
same ID, never invent another object or overwrite an external edit.

Eight new synthetic cases and all35 Drive/helper checks pass locally. CI includes
the new tests. See `docs/milestones/m1-yaml-conditional-proof.md`. Current-head CI,
the concrete private fixture/execution review and an explicit additional-launch
approval precede any live operation. No new reservation, live request, resource,
credential change, APK build or phone installation ran for this preparation.
Native publication/review/runtime and background acceptance remain open.
