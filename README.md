# Gym Assistant — AI-assisted development starter package

**Status:** approved product direction; implementation NOT started. This ZIP is a repository-ready **specification and bootstrap**, not an installable Android application or a deployed web app.

Gym Assistant is a private-first, offline-capable Android Workout Session execution application with a React/TypeScript web editor, simulator, import and history interface. Users own their workout data, initially stored in Google Drive. A shareable Google Sheets journal is a derived view of canonical JSON session records.

## Start here

1. Review [architecture and milestones](docs/architecture/overview.md) and the [open design / feasibility gap register](docs/architecture/open-questions.md). The gaps are **not silently decided**.
2. Read [all 97 recorded decisions](docs/product/decision-register.md), [the requirement catalog](docs/product/requirements.md), and [release acceptance gates](docs/product/release-acceptance.md).
3. Follow the [macOS + Cursor personal-account bootstrap](docs/operations/macos-cursor-bootstrap.md). New private repo name: `gym-assistant`; target location: `<private-path>`. **Personal GitHub credentials ONLY.** Existing project `<private-path>` is an example to inspect safely, not copy wholesale.
4. Open [the Milestone 0 starter prompt](M0_START_PROMPT.md) in Cursor, and begin only M0. Do not try to generate the entire app at once.
5. Validate this skeleton with `python3 -m pip install -r tools/requirements.txt` followed by `python3 -m unittest discover -s tests -v`.

## Optional Training Season / Training Program hierarchy (updated package)

The core structure is optional `Training Season ↔ Season participation ↔ Training Program → Training Template → optional Section → Block → Set → Repetition/Action`. A **Workout Session** is a separate actual execution, optionally attributed to a Season/Program and ALWAYS linked to the exact immutable Template revision and effective plan at start. Programs can participate across Seasons, and standalone Programs or completely ungrouped workouts remain supported. One Season may be Active at once. See [full domain architecture](docs/architecture/seasons-programs-and-promotion.md), [Decisions #79–#97](docs/product/decision-register.md), and [new P0 organization requirements](docs/product/requirements.md).

The user-approved **Decision #97** allows explicitly reviewed, selective promotion of completed-session deviations to a NEW Program override revision or shared Template revision; never automatic. First-release basic organization is required; advanced auto-scheduling remains future work. M0 core feasibility deliverables are unchanged.

See [v2 release notes](V2_RELEASE_NOTES.md) for what changed from the earlier 78-decision starter.

## Repository layout

| Path | Purpose |
|---|---|
| `docs/product/` | binding product requirements, decisions, acceptance gates |
| `docs/architecture/` | architecture, data, execution, sync, security, AI, gap register |
| `docs/milestones/` | feasibility prototype, vertical slices, dependency plan |
| `docs/operations/` | macOS, GitHub, approvals, deployment, privacy operations |
| `docs/agents/`, `AGENTS.md`, `.cursor/rules/` | agent-neutral and Cursor-specific instructions |
| `schemas/` | initial **illustrative, executable** versioned JSON Schemas; iterate through ADRs |
| `examples/` | entirely synthetic records, templates and expected timelines |
| `tests/` | initial schema/fixture checks, to be extended with engine conformance suites |
| `apps/` | reserved directories; implementation starts in M0 |
| `packages/` | portable contracts, future engine and storage adapters |
| `.github/` | proposed CI and issue/PR templates |
| `tools/` | local preflight and verification scripts |

## Non-negotiable safety / privacy gates

- Never use company accounts, company credentials, company email or company resources. Validate both Git author and effective GitHub authentication **before** repository creation, push or deployment. Confirmed personal GitHub owner is `LachimB`; repository-local author is `MichalB <<private-contact-redacted>>`. The starter package originally assumed `michal-buzek`, and a later note said `MichalB`. Both are withdrawn as the GitHub login.
- Require approval for **every new external service** and separate explicit confirmation before **any billable resource** is created/enabled. An approved development plan is not blanket approval to provision infrastructure.
- No personal workout history, original physiotherapy PDFs, cloud tokens, private notes or secrets in Git. Sanitized or synthetic test fixtures only.
- Bind no application logic to Google Drive, VITALISTA, one person, or a specific AI provider. Auth identity, data, storage, sharing and cloud APIs are separate concerns.
- Do not rewrite the legacy training journal; after verified migration it becomes read-only historical reference.
- Active workout sessions are authoritative on the Android device and survive connectivity loss. Sync never substitutes for a backup.

## What is already decided vs not decided

The **user-approved requirements** are binding. Framework choices, provider choices, auth service, exact OAuth consent scopes and Android foreground execution methods must be evaluated rather than assumed. M0 must produce evidence for critical choices. See [open questions](docs/architecture/open-questions.md).

## Reference documentation

Official platform references and caveats: [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android alarms](https://developer.android.com/develop/background-work/services/alarms), [Drive OAuth scope guidance](https://developers.google.com/workspace/drive/api/guides/api-specific-auth), [Sheets scopes](https://developers.google.com/workspace/sheets/api/scopes), [Sheets cell model](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/cells), [Kotlin web targets](https://kotlinlang.org/docs/web-overview.html).

## License

**No open-source license is granted by this private starter package.** Choose and record an explicit license with verified dependency/media rights before any public release. Do not copy source images from private physiotherapy PDFs into public fixtures.
