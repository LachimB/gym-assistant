# M1 Drive/OAuth scope experiment

Status: **Approved by the user on 2026-11-02 / not provisioned.** The user
explicitly approved the synthetic-only, billing-disabled experiment described
below, including a dedicated personal Google Cloud development project/OAuth
clients, `drive.file`, labelled test files and a maximum of 500 requests.
The personal Google account is not yet selected. No Google user/cloud API call,
project creation, OAuth grant or test-file upload has occurred.

This approval excludes Sheets, real workouts/history/documents, billing activation,
paid resources, storage upgrades, service accounts and broader Drive permissions.

## Approved setup scope

1. Google Drive API as the M1 storage service, using a user-selected **personal**
   Google account (not inferred from the GitHub account).
2. A dedicated personal Google Cloud development project and OAuth consent/client
   configuration for the Android debug app and localhost web test, or a named
   existing personal project explicitly chosen by the user. Enable only the APIs
   required for the experiment, including Picker if that selection path is tested.
3. Create a clearly labelled synthetic-test folder/files in the selected personal
   Drive and perform the read/write/retry/revocation tests below. No legacy Sheets,
   real workouts, PDFs, personal notes, sharing invitations or public hosting.

No billing account may be linked/enabled, quota increased, storage plan upgraded,
service account created or chargeable resource activated under this approval.
Any such requirement stops for a separate explicit confirmation.

## Data, scope and credentials

- Synthetic YAML templates/exercise definitions and synthetic JSON session/event
  fixtures only. Provider IDs/ETags remain adapter metadata, not domain IDs.
- Request only `https://www.googleapis.com/auth/drive.file` initially. Do not
  request full-Drive, Sheets, email/profile or application-sign-in scopes by default.
- Show the personal account, requested permissions, exact test folder/files and
  consent before use. Do not reuse company accounts or the unrelated Drive plugin's
  OAuth grant as proof that the application's own scope works.
- Keep access tokens out of Git, build artifacts, logs, portable exports and issue
  reports. The experiment uses transient credentials; durable refresh-token storage
  needs a reviewed platform-specific design before production use.

## Experiment and evidence

| Case | Required outcome |
|---|---|
| App-created visible folder/files | Create/read/list YAML and JSON using only the granted scope |
| Existing selected file and folder | Prove exact access, including an externally created descendant; never assume inherited grants |
| Manual YAML edit | Detect changed version; invalid content cannot replace the last valid execution source |
| Retried session write | Stable operation/session IDs; no duplicate history after a lost acknowledgement |
| Concurrent revision change | Prove the provider's precondition behavior; preserve both branches on conflict rather than overwrite |
| Rename/move/relocation | Identity survives path change; report missing grants instead of widening scope silently |
| Offline/revoke/restore | Local Room records survive; queue/error state remains visible; no silent account switch |
| Cleanup | Retain test artifacts for review; no automatic deletion of user files or legacy data |

Cap the experiment at 500 requests and stop on quota/billing/permission issues.
Record request counts/status codes and synthetic identifiers only, never tokens or
real content. A failed least-privilege experiment is an open feasibility result,
not permission to request broader scopes or remove a binding requirement.

## Cost and alternatives

Expected experiment spend: **€0**, conditional on billing staying disabled and
existing personal Drive storage capacity. Google documents standard Drive API use
at no additional cost, with quota-overage charging planned later in 2026. Recheck
current pricing/quotas at setup; do not treat the proposal as a perpetual free-tier
guarantee. No paid resource or quota increase is authorized.

Alternatives during the experiment: keep the local workflow and reviewed portable
files, or propose another user-owned provider with separate service approval.
Neither alternative changes M1's approved Drive acceptance gate automatically.

Official sources checked 2026-11-02:
[Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth),
[Drive quotas and pricing](https://developers.google.com/workspace/drive/api/guides/limits).
