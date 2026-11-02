# External service inventory & approval ledger

**Approval is per service. Paid activation requires separate, explicit confirmation even if provider was approved.** Empty status never means implicit approval.

| Service | Proposed purpose | Data/scope | Region | Monthly cost | Approval status | Billing confirmation | Owner |
|---|---|---|---|---|---|---|---|
| GitHub personal private repo | Source/CI and releases | Public-safe source only; personal auth as `LachimB` | TBD | Check user's personal plan | Existing private `LachimB/gym-assistant`; verify personal identity before writes. | Required if any charge | LachimB |
| Google Drive API + personal development project/OAuth clients | Synthetic-only M1 experiment in `m1-drive-scope-experiment.md`; Sheets excluded | `drive.file`, labelled test fixtures, ≤500 requests; feasibility pending | Google cloud | Target €0; billing disabled | **Approved 2026-11-02; dedicated project created, Drive API enabled; OAuth Testing, drive.file consent complete; synthetic probe passed; conditional writes unproven** | No billing/paid activation authorized | User-selected personal account; console verified |
| Email auth provider | Email magic links and optional Google login | Account identity/email | Prefer EU | Compare proposals | **Not selected/approved** | Required | Personal user |
| Web hosting | Private beta and previews | Web assets and minimal technical metadata | Prefer EU | ≤€10 combined target | **Not selected/approved** | Required | Personal user |
| AI provider | Optional BYOK import/authoring | User-approved individual prompts/documents | Provider choice | API usage separate | **Not selected/approved** | Required for any charge | Personal user |

One-time document consent (2026-11-02): the user selected **OpenAI in this Codex
chat** to extract the structure of one specifically supplied workout PDF. That
read-only operation is approved and was performed with the original and extracted
content outside Git. It does not approve a general AI integration, another
provider, paid API resources or further documents. Google Picker's separate API
terms and enablement were also explicitly approved; the dedicated project now
reports Picker API enabled. Restricted browser-key creation confirmation is
pending; no key or broader OAuth grant has been created.

Before adding an entry, supply: alternatives, need, security/data handling, permission scope, terms, recurring cost and recovery/deletion plan. Preserve decision and timestamp in repository ADR/handoff. NEVER print or store token values in this inventory.
