# External service inventory & approval ledger

**Approval is per service. Paid activation requires separate, explicit confirmation even if provider was approved.** Empty status never means implicit approval.

| Service | Proposed purpose | Data/scope | Region | Monthly cost | Approval status | Billing confirmation | Owner |
|---|---|---|---|---|---|---|---|
| GitHub personal private repo | Source/CI and releases | Public-safe source only; personal auth as `LachimB` | TBD | Check user's personal plan | Approved concept; verify `gh api user` is `LachimB` before creation. Not created yet. | Required if any charge | LachimB |
| Google Drive/Sheets OAuth | User-owned training storage and derived journal | `drive.file` candidate (feasibility pending) | Google cloud | Evaluate API quotas | **Not yet provisioned** | Required if any charge | Personal user |
| Email auth provider | Email magic links and optional Google login | Account identity/email | Prefer EU | Compare proposals | **Not selected/approved** | Required | Personal user |
| Web hosting | Private beta and previews | Web assets and minimal technical metadata | Prefer EU | ≤€10 combined target | **Not selected/approved** | Required | Personal user |
| AI provider | Optional BYOK import/authoring | User-approved individual prompts/documents | Provider choice | API usage separate | **Not selected/approved** | Required for any charge | Personal user |

Before adding an entry, supply: alternatives, need, security/data handling, permission scope, terms, recurring cost and recovery/deletion plan. Preserve decision and timestamp in repository ADR/handoff. NEVER print or store token values in this inventory.
