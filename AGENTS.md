# Agent contract (applies to Cursor, Codex, Claude Code and other coding agents)

**NEW DOMAIN RULES (#79–#97):** Read `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001 before changing any schemas, Template resolution, session persistence, Program membership, lifecycle, analytics, or Google Sheets. Canonical names are Training Season / Training Program / Training Template / Workout Session. Grouping optional; no future-default changes without approval.

**Canonical requirements:** `docs/product/requirements.md`, `docs/product/decision-register.md`, `docs/product/release-acceptance.md`. **Architecture:** `docs/architecture/`. **Current work:** `docs/milestones/m0.md` and `docs/agents/HANDOFF.md`.

## Mandatory rules

1. Preserve all binding requirements; propose a documented change and request approval rather than silently weakening one.
2. Never use company credentials/resources or leak personal history, original PDFs or secrets. Run credential preflight before GitHub write actions. The confirmed personal GitHub owner is `LachimB` and must match `gh api user` exactly. An earlier `MichalB` login instruction was corrected. The starter package's `michal-buzek` assumption is also withdrawn. Repository-local author remains `MichalB <<private-contact-redacted>>`. Do not change global Git config.
3. Require prior user approval for **each** new external service. **Every billable resource requires a separate explicit confirmation** before creation, activation or billing enablement.
4. Require approval for major architecture changes, destructive operations, schema/data migrations and security-sensitive changes. Routine changes within an approved milestone may proceed with CI and PR discipline.
5. Create small PRs, run tests, maintain acceptance traceability and concise ADRs, and update shared handoff documentation on every substantive change.
6. M0 is a feasibility prototype, not the full application. Do not claim tested Android/background behavior without device evidence. The M0 engine spike may defer an emulator matrix. Later milestones still require automated emulator testing across supported Android versions. Keep scheduled time, observed execution time, pause duration, and unobserved gaps as separate records. Never treat unobserved time as completed exercise.
7. Keep the timing engine deterministic and testable with virtual time. Actual and scheduled time are distinct; avoid claiming impossible guarantees after process death/reboot.
8. Never upload user-provided documents or sessions to AI without per-operation consent and provider selection. By default use synthetic fixtures.
9. Keep personal/source and import data segregated and backups outside the active app directory. Legacy sheet read-only after successful migration.
10. Do not create real accounts, resources, billable services, send invitations, deploy publicly, or perform data-destructive actions without explicit user approval.

## Communication with the project owner

- Explicitly state whether user input/action is needed now. When none is needed,
  say so and continue authorized work without requesting redundant confirmation.
- Requests requiring the user's response must stand out under a standalone
  **🔴 YOUR INPUT NEEDED** heading (bold with a red marker; do not rely on HTML
  text colors being supported). Immediately give the exact action and location.
  Reserve this signal for real requests, not routine status. Keep other output
  concise and avoid repeated no-input-needed messages during routine progress.
- When input is needed, specify the exact decision/action, where to do it (this
  chat, GitHub, or the phone), and the expected response. Distinguish required
  input that blocks work from optional feedback, and say whether work is waiting.
- End task-completion updates with **🔴 YOUR INPUT NEEDED** followed by the concrete
  request, or a brief **No input needed** when there is no request. Do not leave the user
  to infer a review, approval, merge, or test request from a progress report.
- Test requests must give steps, expected observations, and what results to send
  back. Approval requests must identify the exact scope/action being approved.
  Explain whether approval is a chat reply, GitHub review/merge, or an environment
  permission prompt; do not imply that a separate approval screen exists.

## Agent loop

Read requirements → read open gap/ADR decisions → select approved issue/milestone → implement smallest viable slice → tests and CI → document changes → verify actual device/web behavior → update `docs/agents/HANDOFF.md` → open small PR → obtain approvals where necessary.

See `docs/agents/working-agreement.md` for detailed review gates.
