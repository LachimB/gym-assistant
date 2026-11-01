# Authentication, privacy, AI and provider approvals

## Identity

Provider-independent internal app user ID; passwordless email magic link and optional Google Sign-In for MVP. Reserve identity-link interfaces for Apple, Microsoft, Meta or additional providers (post-MVP). Storage OAuth consent/login is distinct and can use a different Google account. Invite-only beta, session/device management foundations, recovery, account export and deletion. Offline app access to previously synced workouts after initial authorization; token expiry must not destroy locally owned workout data.

## Security / privacy

Personal session history and import originals remain on client device + user-owned storage. Do not store copies on app-managed servers. Transport and provider storage encryption as default; optional client-side E2EE designed for later with documented key recovery limitations and Sheets/AI trade-offs. Private by default, least privilege, explicit sharing consent, deletion/export and revoke/disconnect; credential rotation. Threat model must cover stolen phone, leaked browser key, compromised OAuth scopes, malicious YAML, unsafe PDF content, sharing links, web XSS and backups.

## BYOK AI

MVP: optional AI-assisted PDF extraction and template draft, optional authoring/analysis. User configures AI provider/model per task; usage/cost visibility; fallback only among explicitly approved and configured providers. AI-generated output is untrusted draft, validated and approved before applying. Manual import must work with AI disabled.

**Browser key handling is unresolved.** Never embed BYOK secrets in client bundles, logs, Git, URL params or persistent plaintext localStorage. Evaluate on-device ephemeral request vs approved minimal proxy/credential vault with threat assessment, session isolation, CORS and cost controls. A backend gateway that processes documents is not a permission to store training history. No service may receive private PDFs/sessions without per-operation informed approval.

## New-service approval procedure

For *each* identity, AI, hosting, monitoring, email, analytics, key vault or payment provider, open a proposal before use documenting vendor, why needed, data exposed, OAuth scopes, geographic region, minimal alternatives, estimated monthly cost, any free-tier restrictions, account owner and revocation path. User must explicitly approve. **Before a resource is chargeable, a separate explicit confirmation is required.** Record in `docs/operations/external-service-inventory.md`. Personal accounts only.

## Sharing

Initial workflow: derived Google Sheets journal, plus YAML exchange. Later app sharing: private/shared/public ACLs, per-session/time-range read-only grant, comments and proposal objects, recipient-owned template copies, opt-in version updates; never retroactively alter active sessions. Media licenses and original PDF rights must be checked before publishing shared assets.
