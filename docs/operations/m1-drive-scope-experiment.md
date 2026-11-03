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
  [Gym Assistant synthetic test](https://drive.google.com/drive/folders/1r2LHV_D0E97USw2eb6tDnZGPD1zWMNMT).
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

## Externally created child probe — 2026-11-02

The connected Drive skill/connector was used only to prepare an external fixture,
not as proof of the application's grant. Its profile matched the user-selected
personal account. Four connector setup calls: profile, exact synthetic-parent
metadata, upload and exact child metadata readback. Created
`SYNTHETIC-external-child.json` in the existing labelled test folder. Readback
confirmed JSON MIME type, the exact parent and `shared=false`. Content is a fixed
made-up probe with `containsRealTrainingData=false`; no real materials were used.

The application's own token then returned **200** for the synthetic parent and
**404** for that exact externally created child. No child content was read by the
application. This demonstrates that access to this app-created parent did not
expose this externally created descendant; no blanket inherited-grant assumption
is valid. The connector's separate grant is not reused as application auth.

Counter: **18/500**, including the four conservatively charged fixture-setup calls
and two direct app requests. The bookkeeping helper can only consume budget, not
reset it. Six Node tests cover budget safety, exact synthetic metadata, native
fetch receiver and fixed-origin/redirect behavior. The external fixture remains
private and retained for review:
[synthetic external child](https://drive.google.com/file/d/1XMMi-8UlkMPy4z3X4dUk5Zg1hNAVVtbo/view?usp=drivesdk).

Prepared the next Google Picker selection in the local tool, restricted to JSON
and the known test folder/query, with exact picked-ID checking. No Picker request
has run. The user explicitly approved the separate Google APIs Terms of Service
and enablement on 2026-11-02. The Cloud console now reports **API Enabled** for
`picker.googleapis.com` in the dedicated synthetic project. Later evidence below
records approved restricted-key creation and the exact-file selection result.
The key is restricted to Picker API and the
necessary localhost/Google Picker iframe referrers; never create a service account,
link billing or commit the key. Key/token fields remain memory-only. No broader
Drive scope is requested. Internal Picker UI calls are separate from direct Drive
transport accounting and must be recorded if that stage runs.

Official setup/restriction reference:
[Google Picker web integration](https://developers.google.com/workspace/drive/picker/guides/web-picker).

### Explicit selection result — 2026-11-02

The user separately approved persistent browser-key creation and restriction.
Created `Gym Assistant M1 synthetic Picker localhost` with restrictions already
set: **Google Picker API only**, websites `http://127.0.0.1:5173/*`,
`http://localhost:5173/*` and `https://docs.google.com/*`. Reopened the key details
and verified the saved restrictions. No service account or billing activation.
Key value held only in browser-tool memory and a password input, never Git.

First Picker attempt returned “API developer key is invalid”; no selection.
The iframe URL exposed a short-lived OAuth token and browser key in tool output.
Owner informed; page reloaded to drop local credentials, subsequent observation
filters omit credential-bearing iframe URLs. Reload is **not** token revocation.
No exposed value is reproduced in this ledger or repository. This diagnostic
exposure precludes claiming credentials never appeared in tool output.

The page's blanket no-referrer setting was incompatible with a website-restricted
key. Changed document policy to `strict-origin-when-cross-origin`; direct Drive
transport still explicitly uses `no-referrer`. A subsequent attempt succeeded
with all key restrictions unchanged; key propagation delay may also have mattered,
so the cause was not isolated experimentally. Picker showed only the exact named
synthetic JSON, whose selected ID matched the verified fixture. The application's
unchanged `drive.file` grant then read parent metadata, child metadata and verified
synthetic content: **200 / 200 / 200**, compared with child 404 before selection.
This proves this file's explicit selection path, not a blanket descendant grant.

Counter **23/500**: prior 18, one parent check on each of two Picker attempts,
then three post-selection direct requests. Picker made two UI attempts; its
internal HTTP call count is not measured by the direct Drive counter. No other
files selected, no real contents read through this experiment, no sharing/deletion.

### Selected file relocation result — 2026-11-02

Seven further requests, all HTTP 200: verify the labelled synthetic parent;
verify the exact selected JSON's name/MIME/original parent; create a labelled test
subfolder; move that JSON into it; read the same file ID; return it to the original
parent; read and verify that same ID and original parent again. No content change,
sharing, deletion or grant expansion. The empty destination remains available for
review under the test parent as `SYNTHETIC-M1-MOVE-TEST-<random-id>` (provider ID
`1eL_pcbtgNtrzBNze62yYS_ZdSkpoBg5b`). Explicitly selected file access survived this
move and return; this is not a blanket folder/descendant or shared-drive claim.

Current direct/setup counter **30/500**; two Picker UI attempts in addition, with
internal request count unmeasured. The move tool stops on every failure, does not
silently retry or automatically undo an uncertain write, and retains artifacts.

The user separately proposed a real-workout specification folder. Connector
metadata returned 404 and the signed-in browser showed You need access for the
selected personal account. No folder contents were opened or access-request
message sent. The user then supplied an accessible individual PDF and explicitly
selected OpenAI in this Codex chat for this one extraction operation on 2026-11-02.
The PDF was downloaded outside the repository and its complete single page was
text-extracted and visually inspected. Source, extracted personal content and
source identifiers are excluded from Git and this synthetic experiment. Execution
ambiguities were sent to the owner for review; no executable real template or
real-workout acceptance is claimed. This operation does not approve another AI
provider, paid AI API, broader document access or real-data Drive synchronization.


## Native Android read-only stage prepared — 2026-11-03

The already-approved Android OAuth-client experiment is prepared in the app:
explicit account-pinned drive.file authorization, memory-only app token use and
read-only identity/labelled-fixture metadata checks. No raw file contents, history
upload, refresh-token storage, schema migration or new Cloud resource. See
`m1-android-drive-probe.md` for protocol, SDK/cache distinction and phone steps.
The browser counter reserved 20 direct requests once for the single-phone stage:
**50/500 conservatively allocated**, including the prior30 and unused native20.
No native Google requests or successful phone authorization are claimed yet.
Google SDK internal authorization traffic is separately unmeasured; UI attempts
are capped at six. Do not reset counters or run the allowance on multiple phones.


Native phone result: owner reported personal identity and exact synthetic
folder/selected child metadata access passed on Pixel10a/Android17. Native HTTP
metadata response had no ETag, so conditional updates remain unproven; no write
attempted. Independently read only synthetic counters: auth attempts4, direct
requests12. Global allocation remains50/500 (prior30 + reserved20; eight native
requests unused). See ../milestones/m1-native-drive-probe.json. No real workout,
phone session history, token or raw provider response uploaded/exported by this
probe; system Google Play services cache is distinct from app credential storage.

## Conditional content update result — 2026-11-03

The v3 browser/native metadata observations above remain valid. A separately
bounded test of **Drive v2** now establishes conditional content writes for this
exact synthetic file. This supersedes the blanket feasibility-unproven status;
production synchronization and v2/v3 tag interoperability remain unproven.

Official references: [v2 File etag](https://developers.google.com/workspace/drive/api/reference/rest/v2/files),
[v2 media update](https://developers.google.com/workspace/drive/api/reference/rest/v2/files/update)
and [v2 content read](https://developers.google.com/workspace/drive/api/reference/rest/v2/files/get).
The experiment tests behavior rather than inferring concurrency guarantees from
`version` or another Google product's API. No extra API/service/scope/resource.

`tools/drive_conditional_probe.mjs` pins the exact labelled parent and selected
synthetic child. It verifies selected personal identity, parent marker, child
name/MIME/parent and made-up contents before writing. Missing/weak/malformed ETags
stop without writes. Current-tag media PUT returned200 and a different strong
ETag. Different content with the stale tag returned412. Final read matched the
first updated content. All seven statuses are recorded without tokens/raw ETags
in `../milestones/m1-drive-conditional-write.json`.

The original three synthetic fixture fields are retained, with a random
`conditionalProbe` field. No rename, sharing, deletion, private contents or real
Workout Session upload. The first attempt used a cached old transport and was
rejected locally before transmission; explicit module versioning fixed that.
No automatic retries. Page token and Picker key forgotten after the result;
existing Google grant unchanged.

Budget: previous global allocation50 plus seven requests = **57/500 allocated**.
Native reserved20 includes12 consumed and8 unused. Thus49 counted direct/setup
attempts include the consumed native portion; unmeasured SDK/Picker internal
traffic remains separately disclosed. Two browser authorization refreshes were
used in this stage, with no new scope or files selected. Do not refund/reset
reserved counters. Fourteen Node tests cover request budgets, endpoint confinement,
identity/fixture gates and conditional success/failure paths; CI includes them.

This proves a v2 current/stale content update case. It does not provide durable
outbox acknowledgements, multi-device execution fencing, account-binding storage,
network retries, real-history delivery or general safety of every Drive endpoint.


## Finalized synthetic phone delivery and same-grant browser readback — 2026-11-03

ADR-0005 separately approved the account-bound delivery/migration boundary. Verified
private backup preceded physical Room1→2 migration; owner initiated synthetic
phone delivery and retry. One durable job/connection,11 direct HTTP requests and2
native authorization attempts; original workout untouched, only synthetic outbox
acknowledged. New20-request reservation increased global allocation57→77 once.

Existing web grant then verified identity/parent/exact new file, listed its exact
name and read matching canonical bytes: five200 responses, allocation77→82. One
matching file visible to the app grant in the test parent; download hash equals
phone receipt. Actual local web history file chooser displayed21 meaningful events
and50s observed, zero pauses/gaps. This is a verified synthetic download route,
not completed product Drive history integration. No extra scope/API key/service.

Current allocation82/500 =65 counted direct/setup attempts +17 unused reservation
(native read8 + session delivery9). Google SDK/Picker internal traffic remains
unmeasured. Page credentials forgotten after readback. Public synthetic source
only; no personal workout, source PDF or raw recovery log uploaded. See
`../milestones/m1-native-session-delivery.json` and `m1-session-delivery.md`.

## Approved personal delivery boundary — 2026-11-03

The owner approved ADR-0007 after its explanation. This explicitly expands the
existing personal project's synthetic-only data boundary for manual selected
finalized human1.2 sessions. Exactly drive.file, same personal account/client,
one private PERSONAL M1 DEV folder; billing remains disabled. No AI analysis,
automatic uploads, history backfill or sharing change. Implementation and tests
are in progress; no live personal folder/upload has occurred yet.

Reserve50 additional direct attempts once: previous82 → **132/500 allocated**.
Old native counters/reservations are not reset/refunded. The new personal path has
its own persistent50-request/4-authorization allowance; private build activation
requires personalDeliveryBudgetReserved=true after verified source CI. SDK
internal traffic remains unmeasured. Tests use generated synthetic data and fake
transports; the final live test requires owner-selected real session + exact
readback + one explicit retry. Room3→4 installation requires a fresh verified
private database, library, payload and setup-files backup after successful checks.
