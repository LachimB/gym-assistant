# Start here — personal macOS / Cursor

**Do not immediately run commands that create GitHub repos, cloud OAuth clients, services, billing, or infrastructure.** Start by reading `README.md`, `AGENTS.md`, `docs/architecture/open-questions.md`, `docs/architecture/seasons-programs-and-promotion.md`, and `docs/milestones/m0.md`.

1. Extract into a temporary folder; verify all documentation and fixtures.
2. Create or initialize `/Users/michal/repos/private/gym-assistant` **only after** checking the directory doesn't contain existing data and confirming the user's intent.
3. Run `tools/preflight_personal_github.sh` with explicitly supplied personal email and expected owner. This script is read-only and checks the effective authentication path; no secrets printed.
4. Initialize Git and create the **private** repo after interactive confirmation. Never copy the `.git` directory, credentials, remote or tokens from `mobile-data-switch`.
5. Open the project in Cursor. Paste `M0_START_PROMPT.md` and work only on Milestone 0.

Implementation state: **zero functional Android and web app code**. No Google services have been provisioned by this package.
