# M1 Drive/OAuth scope experiment

Status: **Approved by the user on 2026-11-02 / setup in progress.** The user
explicitly approved the synthetic-only, billing-disabled experiment described
below, including a dedicated personal Google Cloud development project/OAuth
clients, `drive.file`, labelled test files and a maximum of 500 requests.
The user selected their personal Google account and completed browser sign-in.
The dedicated project `gym-assistant-m1-synthetic-dev` was created with no
organization; the billing page explicitly reports no linked billing account.
Drive API is enabled. The user separately approved the Google API Services User
Data Policy at the final agreement step. OAuth remains in Testing mode with only
the selected account as test user and only `drive.file` declared.
A localhost web client is registered for `http://127.0.0.1:5173` and
`http://localhost:5173`; no redirect URI, deployment or production publishing.
Android debug client registration is bound to `dev.gymassistant.m0.debug` and
public APK certificate SHA-1 `CE:DE:EC:BE:F3:A8:92:F2:45:D2:83:EC:48:31:A0:07:57:79:12:BD`.
The user completed the app's Google consent screen showing the selected account,
app name and only the specific-files permission. The page verified the returned
scope is exactly `drive.file`. The first synthetic file probe passed; evidence below.

The Cloud console exposed its generated web-client secret in an accessibility
response during setup. It was not copied into source, local configuration or
artifacts. The browser-only token experiment does not use a client secret; replace
that secret before any future server-side use. No refresh token is stored.

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

## Local browser experiment runner

Serve only the tool directory on the registered loopback origin:

```sh
python3 -m http.server 5173 --bind 127.0.0.1 --directory tools
```

Open `http://127.0.0.1:5173/drive_scope_experiment.html`. Enter the approved web
client ID and selected personal account; neither is a client secret. Google's
browser token model requests only `drive.file`, explicitly excludes previously
granted scopes, and keeps the short-lived token only in page memory. Reload or
Forget clears that local token; it does not revoke Google's account grant.
No API request starts until the separate synthetic-test button is pressed.

The first probe reserves stable provider IDs, creates a labelled folder and a
made-up JSON probe (not canonical Workout Session history), replays the same
create ID expecting HTTP 409, reads exact content, checks there is only one child,
renames it and rereads the same ID. It makes eight Drive requests on its successful
path. No automatic network retries, scope widening or artifact deletion. The
persistent non-secret counter refuses request 501, counts failed requests, and
fails closed if counter persistence fails. Never clear it to evade the approved cap.

This runner proves only its explicitly reported cases. It does not yet prove
Picker grants/descendants, YAML invalid-edit quarantine, version preconditions,
Android outbox sync, offline/revoke/restore, or real-session acceptance. Retain
both synthetic artifacts for review. The UI renders no API error bodies, tokens
or real file content. Requests target only the fixed Google Drive API origin,
refuse redirects and carry no browser cookies. Transport safeguards have four
Node tests and a CI gate, including the browser-native fetch receiver regression.

Official implementation reference: [Google browser token model](https://developers.google.com/identity/oauth2/web/guides/use-token-model).

## Observed browser evidence — 2026-11-02

- Google consent displayed **Gym Assistant M1 Synthetic Dev** and only the specific
  files permission; user completed consent. Token response scope matched exactly
  `drive.file`. Billing remains unlinked; no new API, paid resource or wider grant
  was enabled by consent.
- An initial transport invocation failed before its first HTTP response. No create
  was attempted. Counted conservatively against the budget; no hidden retry. Fixed
  the native browser `fetch` receiver and added a regression test. Reload cleared
  the in-memory token; the unchanged existing grant supplied a fresh token.
- Eight-request probe then passed: generate IDs 200; create labelled folder 200;
  multipart JSON create 200; replay same provider ID 409; exact read 200;
  folder-constrained list 200 with exactly one child; rename 200; same-ID read 200.
  The made-up JSON probe is not a canonical or real Workout Session export.
- Retained synthetic folder for user review:
  [Gym Assistant synthetic test](<private-drive-link>).
  Nothing was shared, deleted or read outside the app-created synthetic artifacts.
- Three further requests verified that folder's exact synthetic label/app marker,
  located its sole matching child, and read child concurrency metadata (all 200).
  **No ETag was exposed to this browser client.** No conditional write attempted;
  numeric version is not substituted for an ETag. This leaves provider conditional
  update feasibility open; it does not establish absence of HTTP conditional
  support through other authorized clients. No overwrite/conflict policy adopted.
- Persistent budget counter: **12/500** including the initial failed invocation;
  11 HTTP responses observed. No automatic budget reset. Token still exists only
  in page memory and is not evidence of a durable Android authorization design.
- Remaining experiment cases: explicitly selected existing files/folders and
  externally created descendants; invalid external YAML edits; proven version
  preconditions/conflict preservation; offline/revoke/restore; Android outbox sync.
  M1 real-workout and physical-phone acceptance remain separate and unfinished.
