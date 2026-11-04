# Continuous security and privacy gate

Every contributor must keep credentials, real user records, private correspondence
and restricted material out of commits and GitHub discussions. Use synthetic
examples and reserved domains. Run the commands below before pushing. A clean
secret scan does not establish that publication respects someone's privacy.

## Implementation and trust boundary

`Publication privacy / privacy` remains the existing read-only PR check. It runs
regressions and scans PR commit ranges on opened, synchronize (including force
push), reopened, edited and ready-for-review events. Review submissions, edits,
dismissals and inline comment changes also run it. It has no write token or
repository secrets; it may run proposed scanner code as ordinary untrusted tests.
It preserves the license inventory check and existing strict privacy guard.
Its `--automated-only` scan reports contextual findings without failing this
supplementary job; only the trusted privacy status can resolve independent review.
It also retains immediate scanning of accessible PR/review/comment webhook text,
including previous title/body values when the event supplies them.

`Trusted security gate` is the authoritative controller. Its three stable commit
statuses are **Security / secrets**, **Security / privacy**, **Security / hygiene**.
All three must be required alongside the existing `privacy` check. It executes
only default-branch workflow/scripts/configuration, never a PR checkout, dependency
install, submodule, hook, build script or executable. It fetches PR refs as Git
objects into a disposable bare repository. No AI or external analysis service is
used. Gitleaks runs locally; only scanner binaries are downloaded from GitHub.
No scan results or source files are cached/uploaded as artifacts.

A narrowly scoped `pull_request_target` controller is necessary for trusted
required statuses on fork heads and metadata events. Unlike the preferred ordinary
`pull_request` job, it can write commit statuses. Its permissions are contents:read,
pull-requests:read and statuses:write; it cannot modify repository files, settings
or PR discussions. Authentication is supplied only to the GitHub API client and
removed before scanner/Git subprocesses. Repository secrets are never referenced.
Checkout credentials are not persisted. Git subprocesses isolate hook-exported
repository/configuration variables so scratch operations cannot target a developer
checkout; the local index scan explicitly retains partial-commit index selection. Actions are pinned to full commit SHAs;
Gitleaks 8.30.1 uses the existing verified release archive checksum.

The controller handles PR events, conversation comment creation/edit/deletion,
and completion of the read-only privacy workflow. That completion is a wake-up
signal only: no artifacts, code or conclusions from that workflow are trusted.
Review/inline events on forks can omit their PR association or need approval;
a twice-hourly scheduled poll and manual Actions dispatch re-assess every open PR.
The controller serializes publishers and checks head, base, metadata and approvals
again before publishing results. API/scanner errors, missing objects, unexpected
formats, timeouts and exceeded limits fail closed. No skip/path filter bypass.

## Two passes for every introduced commit

The exact range is all commits reachable from head but not base, in topological
order. It includes side branches of merges and historical commits newly introduced
by rebase/cherry-pick/force-push. The report lists every SHA and coverage count.
For every changed path it examines complete new contents and prior contents from
every parent, including deletions, overwrites, comments and extensionless files.
Commit objects (author, committer, signatures and message) and filenames are also
inspected. Objects are read directly, so export-ignore/export-subst attributes
cannot hide them. Identical path/content scans are deduplicated inside each run.
Unchanged baseline files and unrelated historical commits are not rescanned;
this is incremental forward enforcement, not a declaration that old history is safe.
The historical remediation plan remains separate and unapproved for execution.

Pass 1 runs Gitleaks built-in provider/entropy rules plus explicit URL credentials,
secret assignments and incomplete private-key headers. Inline allow comments,
PR-provided scanner config/ignore files and scanner environment overrides have no
authority in the trusted controller. Full redaction is enabled; scanner output is
captured and never forwarded. Reports contain SHAs, rule names, opaque finding IDs,
counts and status, never matching bytes or potentially sensitive filenames.

Pass 2 applies the existing publication privacy rules and centralized contextual
rules for contact/location/health/account fields, correspondence/restricted
material and private infrastructure. Suspicious fixtures/docs, changes to protected
controls, or linked material require independent review. Detection is contextual
triage, not a semantic proof. Legitimate synthetic addresses on reserved domains
and the explicitly approved public author aliases pass the existing policy;
other contact addresses still follow the stricter existing publication policy.
The existing owner-specific author/committer allowlist is also retained; changing
contributor attribution policy requires a separately approved policy change. Forks
are inspected safely even when that existing policy rejects their attribution.
Names and ordinary identifiers alone are not privacy findings. Review whether
multiple files together identify someone or expose their activities or organization.

Hygiene checks reject known sensitive file families, ignored-but-tracked entries,
machine/temporary files, non-UTF8/binary objects, recognizable renamed database/
archive/document/image headers, database export text (review), Git links and LFS
pointers whose underlying content cannot be inspected. An exact SHA-256 pin retains
the existing Gradle wrapper JAR; modifying it requires a separate reviewed policy
change, not an extension-wide exclusion. Each commit's ignore rules must cover
representative sensitive families. Limits: 2 MiB per object, 100 MiB total content,
10,000 unique path/content scans, 500 commits. Exceeding limits blocks coverage;
split the PR or design a reviewed bounded alternative. Nothing is silently truncated.

## GitHub content and independent review

The controller paginates current title/body, conversation comments, review bodies
and inline comments. Values are inspected in memory and withheld from output.
Commit messages are covered by the commit scan. Direct links in PR content trigger
review: attachments/screenshots/logs/linked pages are **not downloaded or OCR'd**.
No contributor-controlled URL is fetched; this avoids credential forwarding and
server-side request forgery. Maintainers must inspect accessible linked material
and redistribution rights separately, including combinations with committed data.
An inaccessible attachment remains unresolved until removed or reviewed through an
authorized local process. Do not upload private material to an analysis service.

To resolve contextual review findings, an independent allowlisted maintainer with
current write/maintain/admin permission submits an **APPROVE review** with exactly:

```text
Security-Privacy-Review: <full-head-SHA> <64-character-review-digest>
```

Copy the SHA/digest from the trusted run summary. Before approving, inspect every
listed commit, full relevant files and preimages, metadata, linked material,
fixtures' provenance and combinations of identifiers. Approve only synthetic,
publicly redistributable or explicitly consented material. Remove confirmed private
content instead of approving it. Receipt text has no free-form disclosure. It binds
the exact base/head, trusted policy, inspected metadata and findings. New commits,
metadata edits, other comments, changed findings, dismissal or a later change request
invalidate it. The receipt itself is excluded from the digest to avoid recursion.
Contributor checkboxes, labels, arbitrary comments, self-review and approvals on
another head do not resolve findings. Review cannot clear secrets, strict existing
privacy violations, hygiene failures or incomplete scans.

Initially only the confirmed owner is allowlisted. Owner-authored flagged PRs
therefore need another consenting trusted maintainer added in a separately reviewed
policy change before they can receive independent approval. CODEOWNERS also cannot
self-approve. Do not weaken the gate or impersonate a reviewer to work around this.
The initial rollout requires explicit manual review of the bootstrap PR because
the controller cannot execute before it exists on default branch.

## Exceptions and failure investigation

Start with the failed category and rule/finding IDs in the trusted summary. Inspect
locally without printing suspected values to CI or pasting them into GitHub. Remove
sensitive content from every introduced commit; a later deletion cannot erase an
earlier disclosure. Rotate an exposed credential through its owner and follow the
existing incident/history procedure; never rewrite shared history without approval.

`security/policy.json` centralizes limits, privacy patterns, protected paths, reviewer
identities, exact binary hashes and exceptions. There is no accepted legacy finding
baseline yet. Prior history is outside the new range, not silently accepted.
Exceptions apply only to contextual review rules, matching exact rule ID, content
SHA-256 and path SHA-256, plus an expiry, justification and allowlisted reviewer.
They cannot suppress secrets, strict privacy failures or hygiene. Never store the
suspect value in policy. Prepare a separate exception PR with a non-sensitive
justification, obtain independent control-owner review, merge it first, then rerun
the affected PR. Exceptions/configuration proposed in that PR have no effect on its
trusted assessment. Expired entries cease to apply; malformed entries fail closed.

For a new detection rule, add a specific policy regex and synthetic positive/negative
regression. Keep strict legacy rules when extending `check_repo_hygiene.py`. For
scanner upgrades, verify upstream release provenance, update version/checksum in
the installer and policy together, run the entire security suite against the actual
binary, inspect false positives, and obtain protected-control approval. Never
replace the checksum with an unverified download or run an unpinned installer.

## Local commands

Install Gitleaks **8.30.1** from its official release with checksum verification
(or a trusted package manager supplying that exact version). Python 3.9+ is enough
for the scanner; tests also use PyYAML from `tools/requirements.txt`.

```sh
# Full commit-range assessment; use exact SHAs, not mutable refs.
python3 tools/security_gate.py --base "$(git rev-parse origin/main)" --head "$(git rev-parse HEAD)"
# Include privately prepared PR text without echoing its contents:
python3 tools/security_gate.py --base "$BASE_SHA" --head "$HEAD_SHA" --metadata-file "$PR_TEXT_FILE"
python3 tools/security_local.py
python3 tools/check_repo_hygiene.py --staged
python3 -m unittest discover -s tests -p test_security_gate.py -v
# Supplementary local hooks; repository-local configuration only:
git config --local core.hooksPath .githooks
```

The complete range command exits nonzero on blocking **or contextual review**
findings, and on unavailable scanner. Local hooks scan complete staged files and
new pushed commit ranges. Contextual staged findings warn because independent
review happens in CI; secret/technical staged findings block. Local tooling uses
current local policy and can be bypassed, so it never replaces GitHub enforcement.
Use the existing personal credential preflight and push wrapper for remote writes.

## Administrator activation (prepared, NOT applied)

No remote settings were changed by this implementation. Existing main protection
must remain in place. Read-only verification on 2026-11-04 found strict required
`privacy`, administrators included, PRs required, zero required approvals, code-owner
review disabled, and force pushes/deletion disabled. No open PRs existed at that
verification; the baseline main commit passed all eleven existing checks.
After reviewing/merging the bootstrap and observing a genuine
trusted run, an administrator must review `security/ruleset.json` and add its rules
to existing protections, retaining all existing required checks. It targets default
and `release/**` branches, requires PRs/independent code-owner approval, dismisses
stale approvals, requires approval after latest push and resolved review threads,
requires strict up-to-date checks, and blocks force pushes/deletion with no bypass
actors. Verify the GitHub Actions app integration ID (example: 15368) against actual
runs before applying; do not accept a required status from an arbitrary publisher.
Activate **all four** named checks, not the trusted controller's run-level job alone.
Require missing checks to stay pending/blocking. Protect every release branch using
this controller and a reviewed baseline. Do not enable merge queue without adding
and testing merge_group handling; it is not supported by this implementation.

GitHub Free supports branch rulesets for this public personal repository. Some
organization-only required-workflow/push rules are unavailable here. A required
status source can be bound to the Actions app, not to one specific workflow on
this plan. Independent CODEOWNERS approval on every workflow/config/policy change
is therefore essential to resist another same-repository workflow spoofing a
status with write permission. Fork PR tokens cannot publish these trusted statuses.
Never approve an untrusted workflow merely to get a check to run. Restrict repository
write access, disallow bypass, protect settings/admin access and retain all existing
checks. Administrators can change settings; repository files cannot prevent that.

Validate after activation with synthetic fork and same-repository PRs: ordinary
success, secret failure, temporary add/delete secret, metadata edits, discussion/
review edits/deletions, workflow removal, protected-policy edits, missing scanner,
and stale/dismissed approval. Confirm each exact status is required and a failing
or absent result blocks merge. No live merge/protection validation is claimed by
local tests. No permission or paid plan upgrade is authorized or required here.

## Remaining platform boundaries

GitHub content is already exposed when submitted; Actions detects it afterward,
not before publication. Edited-away comment versions/deleted attachments cannot
be reconstructed reliably by a current API snapshot. Event delivery, Actions
approval for first-time forks, scheduling delays/disablement and API outages can
delay checks. Metadata changes do not change a commit SHA: an old green status can
remain visible briefly before its event runs. Serial execution/rechecking narrows
races but cannot create an atomic metadata-and-merge transaction. Review and freeze
PR discussion immediately before merge, verify the latest trusted run is newer than
all content/review changes, and use manual dispatch when delivery is uncertain.
Do not merge during a pending/unavailable assessment. There is no claim of absolute
pre-merge enforcement of mutable GitHub discussion content through status checks.

Images, encrypted archives, arbitrary binary formats, attachment metadata, remote
linked logs, Git LFS payloads, private repository links, deleted/inaccessible content,
issues outside a PR, release assets and unrelated workflow logs require separate
review. Uninspectable committed material blocks; linked material requires independent
review. Rules cannot detect every name, indirect inference, encoded disclosure,
medical fact or proprietary text. Reviewers must use judgment. The retained JAR pin
is an explicit existing dependency acceptance, not a claim of new binary analysis.

References: [GitHub workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
[GitHub rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets),
[Gitleaks](https://github.com/gitleaks/gitleaks).
