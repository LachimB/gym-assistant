# Cursor / coding-agent first assignment: Milestone 0

You are implementing **Milestone 0 only** in the personal GitHub repository `gym-assistant` at `<repository-root>` on macOS. This historical M0 prompt does not supersede the current M1 handoff or public-repository privacy policy.

**First read:** `AGENTS.md`; `docs/product/requirements.md`; `docs/product/decision-register.md`; `docs/architecture/overview.md`; `docs/architecture/execution.md`; `docs/architecture/seasons-programs-and-promotion.md`; `docs/architecture/open-questions.md`; `docs/milestones/m0.md`; `docs/agents/HANDOFF.md`.

## Hard gates

- Verify effective GitHub identity and repository-local personal author identity with `tools/preflight_personal_github.sh` before any GitHub write. The confirmed personal GitHub login is `LachimB`, author `LachimB <17935459+LachimB@users.noreply.github.com>`, public repo `LachimB/gym-assistant`. `gh api user` must return exactly `LachimB`. Never use company GitHub identity, credentials, email or resources. Use the user-confirmed `LachimB`. Do not copy authentication configuration from another project.
- No external services without individual approval; no billable resource without separate explicit confirmation. Use mock/in-memory storage first, then propose Google Drive access and permission scopes for approval.
- No personal sessions, original PDFs or secrets in Git; use synthetic fixtures.
- Do not build M1–M5 features beyond what is needed to validate M0.

## Deliverable

Make an **installable Android prototype** (Android 12+) and **browser simulator** able to execute the SAME synthetic composite workout. Show precise current phase, reps/sets, elapsed-time progression, pause/resume, and basic backward/forward navigation. Use a virtual-clock harness in automated tests. Compare Kotlin Multiplatform shared engine to independent Kotlin+TypeScript implementations and write a measured ADR. The shared engine is preferred only if it works cleanly with React/TypeScript; no dogmatic requirement.

The synthetic example is `examples/templates/composite-pyramid.yaml` and `examples/expected/composite-pyramid.timeline.json`. Read `schemas/` and make improvements through an ADR if necessary; these are **draft illustrative schemas**, not frozen final wire contracts. Record scheduled vs actual timing separately. Clearly distinguish simulated audio from supported physical-device audio.

The user will test frequently on their own Android phone and in the browser. Document build/install and run commands and supply a working preview mechanism without secretly provisioning services.

## Acceptance tests

Complete **all mandatory M0 checks** in `docs/milestones/m0.md`. Minimum: installable prototype, web simulator, composite fixture, pause/navigation/timing, deterministic cross-platform trace and CI tests, architecture findings. Basic Drive adapter integration belongs in scope, but **live Drive authorization is not a completion gate**; no cloud account setup without permission.

## Work plan requested from you

Before coding: propose an M0 task breakdown with no more than one high-risk experiment at a time, estimate dependencies/risks, and list any user approval needed. Work in small PRs; keep `docs/agents/HANDOFF.md`, issue tracking and ADRs current. Implement tests before extending features. When done, report pass/fail for every acceptance criterion, plus measured physical phone and browser test results (or explicitly state not tested).


## Updated domain requirements (approved after initial package, #79–#97)

The application must eventually support optional Training Seasons, Training Programs and immutable versioned Training Templates; an actual recorded execution is a Workout Session. Every Training Template works ungrouped. Program memberships may apply verified limited parameter overrides, with latest/pinned version policies and immutable resolved session snapshots. See `docs/architecture/seasons-programs-and-promotion.md` and the `GA-ORG-*` requirements.

**M0 acceptance scope is unchanged:** show one synthetic Training Template running on Android and web without organizational context. Validate newly added organization schemas/fixtures as contract tests only. Do NOT turn M0 into Season/Program UI construction; add proper domain interfaces in M1, complete management and promotion by M3/M5.
