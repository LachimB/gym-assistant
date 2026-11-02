# External service inventory & approval ledger

**Approval is per service. Paid activation requires separate, explicit confirmation even if provider was approved.** Empty status never means implicit approval.

| Service | Proposed purpose | Data/scope | Region | Monthly cost | Approval status | Billing confirmation | Owner |
|---|---|---|---|---|---|---|---|
| GitHub personal private repo | Source/CI and releases | Public-safe source only; personal auth as `LachimB` | TBD | Check user's personal plan | Existing private `LachimB/gym-assistant`; verify personal identity before writes. | Required if any charge | LachimB |
| Google Drive API + personal development project/OAuth clients | Synthetic-only M1 experiment in `m1-drive-scope-experiment.md`; Sheets excluded | `drive.file`, labelled test fixtures, ≤500 requests; feasibility pending | Google cloud | Target €0; billing disabled | **Approved 2026-11-02; not provisioned** | No billing/paid activation authorized | Personal Google account selection pending |
| Email auth provider | Email magic links and optional Google login | Account identity/email | Prefer EU | Compare proposals | **Not selected/approved** | Required | Personal user |
| Web hosting | Private beta and previews | Web assets and minimal technical metadata | Prefer EU | ≤€10 combined target | **Not selected/approved** | Required | Personal user |
| AI provider | Optional BYOK import/authoring | User-approved individual prompts/documents | Provider choice | API usage separate | **Not selected/approved** | Required for any charge | Personal user |

Before adding an entry, supply: alternatives, need, security/data handling, permission scope, terms, recurring cost and recovery/deletion plan. Preserve decision and timestamp in repository ADR/handoff. NEVER print or store token values in this inventory.
