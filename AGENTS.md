# Agent contract (applies to Cursor, Codex, Claude Code and other coding agents)

**NEW DOMAIN RULES (#79–#97):** Read `docs/architecture/seasons-programs-and-promotion.md` and ADR-0001 before changing any schemas, Template resolution, session persistence, Program membership, lifecycle, analytics, or Google Sheets. Canonical names are Training Season / Training Program / Training Template / Workout Session. Grouping optional; no future-default changes without approval.

**Canonical requirements:** `docs/product/requirements.md`, `docs/product/decision-register.md`, `docs/product/release-acceptance.md`. **Architecture:** `docs/architecture/`. **Current work:** `docs/milestones/m1.md` and `docs/agents/HANDOFF.md`.

## Mandatory rules

1. Preserve all binding requirements; propose a documented change and request approval rather than silently weakening one.
2. Never use company credentials/resources or leak personal history, original PDFs or secrets. Run credential preflight before GitHub write actions. The confirmed personal GitHub owner is `LachimB` and must match `gh api user` exactly. Repository-local author remains `LachimB <17935459+LachimB@users.noreply.github.com>`. Do not change global Git config. The owner intentionally made `LachimB/gym-assistant` public on 2026-11-04 to unblock Actions; the preflight now pins PUBLIC visibility for this exact repository. This does not authorize publishing private source documents, real session payloads or credentials.
3. Require prior user approval for **each** new external service. **Every billable resource requires a separate explicit confirmation** before creation, activation or billing enablement.
4. Require approval for major architecture changes, destructive operations, schema/data migrations and security-sensitive changes. Routine changes within an approved milestone may proceed with CI and PR discipline.
5. Create small PRs, run tests, maintain acceptance traceability and concise ADRs, and update shared handoff documentation on every substantive change.
6. M0 is a feasibility prototype, not the full application. Do not claim tested Android/background behavior without device evidence. The M0 engine spike may defer an emulator matrix. Later milestones still require automated emulator testing across supported Android versions. Keep scheduled time, observed execution time, pause duration, and unobserved gaps as separate records. Never treat unobserved time as completed exercise.
7. Keep the timing engine deterministic and testable with virtual time. Actual and scheduled time are distinct; avoid claiming impossible guarantees after process death/reboot.
8. Never upload user-provided documents or sessions to AI without per-operation consent and provider selection. By default use synthetic fixtures.
9. Keep personal/source and import data segregated and backups outside the active app directory. Legacy sheet read-only after successful migration.
10. Do not create real accounts, resources, billable services, send invitations, deploy publicly, or perform data-destructive actions without explicit user approval.

## Communication with the project owner

Continue authorized work until the task is complete or user input/action is truly
required. Answer status questions and resume work; do not stop merely after a slice.
Show concise progress, and never imply work continues after the final response.
End completion responses with:

**🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴**
**🔴 🔴 🔴 🔴 TURN COMPLETED🔴🔴 🔴 🔴 🔴**
**🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴**

For a real input gate, first state the specific decision/action, why it is needed,
where to respond and the recommendation. End with:

**🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴**
**🔴 🔴 🔴 🔴 YOUR INPUT NEEDED🔴 🔴 🔴 🔴**
**🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴 🔴**

Test requests give steps, expected observations and results to send back. If an
execution/platform limit forces a stop, explain it honestly; the completion banner
marks the turn ending without claiming that the overall task is complete.

## Agent loop

Read requirements → read open gap/ADR decisions → select approved issue/milestone → implement smallest viable slice → tests and CI → document changes → verify actual device/web behavior → update `docs/agents/HANDOFF.md` → open small PR → obtain approvals where necessary.

See `docs/agents/working-agreement.md` for detailed review gates.

## Mandatory public-record privacy gate

Read `docs/operations/publication-privacy.md` before every change or external write.
Never publish personal account addresses, real provider IDs/links/markers, private
paths/project names, private Template characteristics, personal outcomes, device
nicknames, raw logs/screenshots or conversation transcripts, including in handoff,
PR/issue text, commit messages and CI/release output. Keep exact operational inputs
and detailed acceptance evidence outside Git. Public records contain only
synthetic inputs and technical pass/fail evidence with honest limits.
Run the content guard and Gitleaks before staging/pushing; CI must independently
pass. Do not bypass or weaken a privacy gate to unblock a commit. Review context
manually: automation cannot guarantee complete detection. Use only the confirmed
GitHub noreply identity. Preserve third-party licenses/notices; original code is
all rights reserved until the owner explicitly approves another license.
History rewriting, force-pushing, destructive cleanup and external permission
changes require a concrete reviewed plan and explicit approval. Sanitizing HEAD
alone does not remove historical exposure. Never restore private archived details
into public handoff records.
