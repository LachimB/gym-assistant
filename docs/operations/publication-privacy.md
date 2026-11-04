# Public repository privacy policy

Binding owner instruction, 2026-11-04. Applies to code, comments, fixtures,
documentation, handoffs, commit messages, PRs/issues, CI logs, screenshots,
release assets and generated artifacts. Public visibility is not consent to
publish private information. Never copy a chat transcript into project records.

## What may be public

Technical design, requirement decisions, implementation revisions, synthetic test
inputs, test counts, platform/API versions, pass/fail results and precise limits.
Use reserved example domains and visibly synthetic provider identifiers. A private
input may be referenced as an approved private input; do not describe its shape,
duration, exercises, personal outcome or identifying source metadata.

## What must remain outside Git

Personal account addresses, live cloud resource IDs/links/markers, credentials,
user/source documents, real Training Templates or Workout Sessions, personal
outcomes, device nicknames/identifiers, raw screenshots/logs, conversation history,
private backup hashes and local usernames/directory layouts. Store detailed
acceptance evidence in `<private-evidence-directory>` outside every checkout,
with owner-only directory/file permissions and an appropriate retained backup.
Use `<private-backup-directory>` and `<repository-root>` in documentation; shell
examples may use `$HOME` and runtime-discovered paths. Never copy unrelated
private project names or authentication setup into this repository.

Defense-in-depth ignored directories exist as an emergency staging boundary:
`private-config/`, `private-evidence/`, `private-backups/`, `private-exports/`.
The preferred location remains outside Git. Arbitrarily named personal JSON/YAML
files must never enter a public directory. Ignoring a file does not untrack it.

## Private operational configuration

Account identity and exact provider resources must be supplied privately and
verified before live access. Android uses private Gradle properties
`syntheticDriveAccount`, `syntheticDriveParent`, `syntheticDriveChild`, and
`syntheticDriveMarker`; keep them outside the repository, never on public command
lines/logs. Defaults contain only synthetic placeholders. Public builds disable
live synthetic probes/delivery until exact private configuration and the existing
budget gates are present. Never publish privately configured APKs/build output.
Existing connection identities must be preserved; do not silently bind an
existing delivery to another folder. Do not clear counters or create resources
because configuration is absent.

The localhost experiment accepts reviewed private IDs/marker in memory and pins
them for that page. Reload to review different inputs; tokens/IDs must not be
reported in public evidence. Continue to verify exact account, name, parent,
marker, MIME, content and existing request budget. No new service/scope allowed.

## Required checks

Before committing: inspect the staged diff, run `python3 tools/check_repo_hygiene.py
--staged`, and Gitleaks with full redaction. Before pushing: run the personal
identity preflight, worktree privacy guard and a new-commit/history secret scan.
Use `tools/personal_git_push.sh`. The checked-in hooks fail closed when a required
scanner is unavailable. Enable them with repository-local `core.hooksPath` only;
never change global Git configuration. CI independently checks content and new
commit authors/history; do not bypass it or merge a failed privacy job.

No regex, hook or scanner can guarantee detection of every personal fact. Human
review is mandatory, including names, telephone/address/location details and
contextual fitness disclosures. Never suppress a real disclosure with an
allowlist. Negative regression tests must construct forbidden synthetic strings
at runtime instead of committing plausible personal values.

## Attribution and publication

Future commits must use `LachimB <17935459+LachimB@users.noreply.github.com>`.
The preflight pins this exact identity and rejects token overrides, another
account/remote or unexpected visibility. No global identity changes.

The owner selected all rights reserved for original code; MIT is only a future
proposal. Read `LICENSE` and `THIRD_PARTY_NOTICES.md`. Never relicense dependencies,
remove upstream notices or publish a binary without its exact dependency/license
inventory and required notices. Keep package publishing disabled until reviewed.

## Historical remediation and external sharing

A forward sanitization does not erase previous commits, tags, PR diffs, forks,
clones, caches or CI artifacts. Preserve a private recovery copy, enumerate affected
refs and collaborators/worktrees, prepare a mapping/filter dry run outside the
active checkout, and obtain explicit approval before rewriting or force-pushing.
Do not garbage-collect recovery objects or alter other active worktrees.

For Drive sharing review, use the owner's normal signed-in interface. Read only
permission metadata for the explicitly approved experiment artifacts. Verify
General access is Restricted, inspect direct/link grants, and avoid opening file
contents. Record only pass/fail and outstanding concerns publicly. Do not change
permissions, invite people, revoke grants or rotate credentials merely because
resource IDs were published. This remediation does not prove remote permissions.

Main branch protection was explicitly approved and applied on2026-11-04: pull
requests and the privacy check are required, branches must be up to date,
administrators are included, and force-pushes/deletion are disabled. Do not weaken
or remove these protections to merge a change. The privacy-fix PR must pass first.

Run `python3 tools/check_license_inventory.py` after dependency changes. The
review record pins dependency inputs and declared licenses; review and update
upstream notices before accepting a changed record. Binary release remains
uncleared until the separate release review is completed.
