# Cross-agent handoff (maintain on every significant change)

## Current status
- Date: 2026-11-01
- Product specification: approved through Decision #97. Decisions #1–#78 remain binding unless a later decision refines one. Decisions #79–#97 add optional Training Season, Training Program, immutable Training Template revisions, and Workout Session provenance.
- Current milestone: M0. Task **M0-01** (repository skeleton and personal GitHub preflight) is in progress and **blocked** before `git init`.
- Working code: none. Schemas and synthetic fixtures are draft contracts. Python contract checks are the current automated baseline.
- Git: not initialized in `<private-path>`. Do not run `tools/bootstrap_private_repo.sh`; the directory already contains the starter package.
- GitHub repository: not created. Intended private repository: `LachimB/gym-assistant`.
- Confirmed personal GitHub login: `LachimB`. Repository-local author, not yet configured: `MichalB <<private-contact-redacted>>`. Do not change global Git config.
- Identity check: `GITHUB_TOKEN` in the parent environment still selects `michal-buzek`. Preflight run with that variable removed from the process passes as `LachimB`. GitHub writes must keep the token variables unset. Author name stays `MichalB`. Withdrawn logins: `michal-buzek` and `MichalB`.
- Cloud resources / OAuth / hosting: none provisioned.
- Local toolchain observed: Python 3.9.6 (CI workflow asks for 3.12), Node.js 24.13.0, OpenJDK 21.0.13, Android Debug Bridge 1.0.41. Contract tests on 2026-11-01: `python3 -m unittest discover -s tests -v` passed 23 tests; `python3 tools/validate_starter.py` passed (17 schema-checked examples, 50,000 ms golden trace, 80 requirements).

## Task identifiers

Use [`docs/milestones/m0.md`](../milestones/m0.md). Do not reuse these IDs for other work.

| ID | Task |
|---|---|
| M0-01 | Repository skeleton, personal GitHub preflight, CI, local docs |
| M0-02 | Schema/fixture confirmation and virtual-clock verifier for the 50,000 ms plan |
| M0-03 | KMP Kotlin/JS versus independent Kotlin and TypeScript engines; ADR-0002 |
| M0-04 | Minimal Compose Android UI and debug install |
| M0-05 | React browser simulator |
| M0-06 | Background timing and clock-semantics notes on a real Android 12+ phone |
| M0-07 | Provider-neutral in-memory store and a written Drive scope proposal |
| M0-08 | Architecture findings, demo, and explicit pass/fail matrix |

M0-03 stops for explicit approval of ADR-0002 before Android or web product UI.

## Session to-dos

Same nine IDs as the Milestone 0 plan. No second list.

| ID | Status | Boundary |
|---|---|---|
| m0-01-repo | in progress | Prep done: docs, script pin to GitHub owner `LachimB` and author `MichalB <<private-contact-redacted>>`, contract tests, local `main` commit, preflight passes as `LachimB` when token overrides are unset. Next: create private `LachimB/gym-assistant` and push with the token override removed. |
| m0-03-spike | pending | Starts only after M0-01 is pushed. Draft ADR-0002. Do not adopt it in product code. |
| engine-checkpoint | pending | Approval gate before any Android or web product UI. |
| m0-02-engine | pending | After ADR-0002 approval. Four separate time records, including unobserved-gap recovery. |
| m0-05-web | pending | Localhost simulator. No hosted preview. |
| m0-04-android | pending | Debug APK only. |
| m0-06-device | pending | Physical phone. No process-death accuracy claim. |
| m0-07-store | pending | In-memory store. No OAuth. |
| m0-08-close | pending | Pass/fail matrix. |

## Next actions
1. Create private `LachimB/gym-assistant` only with `GITHUB_TOKEN` unset and push through `tools/personal_git_push.sh`. `LachimB/gym-assistant` did not exist at the last check.
2. Keep repository-local author `MichalB <<private-contact-redacted>>`. Do not change global Git config.
3. After CI is green, start m0-03-spike. Draft ADR-0002 before product UI, then continue the remaining slices.
5. After M0-01 is pushed and CI is green, start m0-03-spike. Stop again at engine-checkpoint before product UI.

## Clock categories

Scheduled phase time, observed monotonic execution time, pause duration, and unobserved gaps stay separate. Never record an unobserved gap as completed exercise. Recovery shows the last checkpoint and waits for an explicit decision. Details: [`docs/architecture/execution.md`](../architecture/execution.md).

## Testing scope

The M0 engine spike may defer an Android emulator matrix. M0 still requires the composite fixture, virtual-clock tests, deterministic traces, and automated checks, plus the physical-phone smoke test. Automated emulator testing across supported Android versions remains mandatory from M1 onward (Decisions #48 and #54).

## Record each session
- Branch/PR/commit: none. Git is not initialized.
- Tests executed and results: 23 Python contract tests passed. `tools/validate_starter.py` passed, including the 50,000 ms golden trace. Preflight `--bootstrap` stopped because `GITHUB_TOKEN` is set.
- Android physical-device tests and Android version: not run.
- Web preview/test URL: none. No hosted preview is approved.
- New external services proposed/approved: none.
- Unresolved issues and next smallest task: m0-01-repo is in progress. Shell identity pin is still open. Effective `gh` login is `michal-buzek` while `GITHUB_TOKEN` is set; confirmed login is `LachimB`. Create/push stays at the approval gate.

## Approved domain extension: Decisions #79–#97

Read `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001. Canonical names: Training Season, Training Program, Training Template, Workout Session. Basic organization is P0 for the daily-use release. M0 executable workout fixture remains standalone, and M0 acceptance is unchanged. Organization schemas and contract tests exist; do not claim a prototype engine or real Google Drive integration. Most important unresolved M2 detail: deterministic expansion of limited set-use adjustments within an explicit composite step order. Before implementing user-facing promotion, require explicit approval, a scope preview, and a stale-revision check.
