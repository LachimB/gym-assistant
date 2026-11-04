# ADR-0008 — Open a selected personal Drive session in the web reader

- Status: **Approved by owner in chat on 2026-11-04 after explanation**
- Date: 2026-11-04
- Requirements: GA-WEB-004, GA-DATA-001/010/011, GA-SEC-003
- Builds on ADR-0004/0006/0007; no session/schema/database change.

## Decision for the owner

Add **Open from personal Drive** to Workout history. The owner connects the
approved personal account, chooses one delivered session in Google's file chooser,
and the browser reads and validates it directly. Downloading the JSON manually
remains available. The reader cannot edit, upload, delete or share Drive files.

This extends the existing browser experiment beyond synthetic files. ADR-0007
authorizes selected real-session delivery from Android, but does not authorize
this browser access or changes to its connection configuration. Approval is needed
for that security/data-access boundary under AGENTS.md rule4.

## Exact authorization requested

- Reuse the existing approved personal development project, browser OAuth client,
  Google Identity Services, Drive API and Picker API; no new service or client.
- Use only the approved personal account and exactly `drive.file`. Verify its
  stable Drive permission ID and displayed account before file access. The scope
  includes write capability in Google's consent even though this reader issues
  only narrowly allowlisted GET requests. Do not request whole-Drive read access.
- Add only `http://127.0.0.1:4173` and `http://localhost:4173` to the existing
  browser client's authorized JavaScript origins, and the corresponding `/*`
  referrers to the existing Picker-only API key. Retain existing approved
  restrictions, including its API restriction; no wildcard host or public origin.
- Bind the reader privately to the existing verified personal folder and marker.
  No folder/file creation, relocation, permission changes or replacement root.
- First live check: the owner explicitly chooses one already-delivered finalized
  session in that folder. Read metadata/content, validate it and compare the view.
  Further opens always require a user action and explicit file choice.
- Reserve up to30 direct requests within the existing overall500 allowance,
  after reconciling the private allocation ledger. At most4 authorization launches
  and2 Picker launches for the acceptance batch. Do not reset/refund prior
  counters. SDK/Picker-internal traffic remains unmeasured. Missing or exhausted
  budget blocks live access instead of creating another allowance.
- Billing stays disabled. No paid resource, deployment, public build publication,
  AI processing, token persistence or server-side session copy.

The development project/client/key IDs, account binding, folder ID/marker and
allocation details stay outside Git. A privately configured local build may
contain browser-visible client identifiers and the referrer-restricted Picker
key; these are not client secrets. Never publish that build or its configuration.
No OAuth client secret is used. Public defaults leave live access disabled.

## User-visible flow

1. Choose Open from personal Drive. Show the intended account, destination and
   read-only behavior before launching authorization. Load Google scripts only
   after this deliberate action, with bounded load/cancellation handling.
2. Request exactly the approved scope, excluding unrelated previously granted
   scopes. Check the granted scope and `about.user` account binding. A different
   account, missing scope or cancelled popup stops with a clear message.
3. Open Picker for a single existing JSON file; no upload/create view. Explain
   that the owner must choose a delivered recording from the personal folder.
   Picker filtering or a displayed name alone is not a security boundary.
4. Independently verify the bound folder's identity/marker and the selected file's
   exact ID, non-trashed status, JSON MIME, expected delivery naming and parent.
   Reject shortcuts, folders, unrelated files and unexpected parents. Never
   follow a URL supplied by Picker or the document.
5. Read bounded bytes directly from the fixed Google API origin, then run the
   existing history parser and independent frozen/effective-plan hash checks.
   Initially accept delivered format1.2 records only on this connected path;
   preserve existing local-file compatibility. Do not repair malformed history.
6. Show the verified recording in the existing read-only history view with
   account/source attribution. A later selection, Clear view or disconnect
   invalidates every earlier pending callback; stale responses cannot reappear.
   Errors leave no partially verified content. Reading does not change delivery
   acknowledgements on the phone or create an execution on web.

## Implementation and privacy boundaries

Use a separate read-only adapter rather than expanding the synthetic experiment's
write-capable transport. Allow only fixed `about`, exact-folder metadata,
exact-selected-file metadata and media GET endpoints. Encode IDs and parameters;
reject redirects, alternate hosts, caller-supplied URLs and non-GET methods.
Use no ambient cookies; authorization headers go only to the fixed Google API
origin. Enforce request/response limits and consume budget before transmission.

Browser tokens, chosen file IDs and session bytes stay in memory. Never use URL
parameters, local/session storage, IndexedDB, service-worker caches, console logs,
analytics, error bodies or public artifacts for those values. Forget app token
references on disconnect, expiry, account mismatch and reload; do not revoke the
owner's project-wide grant or sign out other apps. On401, stop and require a new
explicit authorization. No silent refresh/background polling or stored refresh
token. Clearing the view also releases retained session/file references.

A loopback-only helper serves private connection configuration and persists only
the reviewed request/allocation counters outside Git. It never receives OAuth
tokens, file IDs selected by the owner, or session bytes. Its same-origin budget
endpoint reserves before each transmission; browser reload/storage clearing cannot
reset it. Missing/invalid counters fail closed. Existing Android and experiment
allowances remain independent within the reconciled global budget.

Private connection settings may be provided through ignored local development
configuration. Verify them against the previously established binding before
activation; never guess identifiers from names or retarget a saved connection.
Keep default/public builds synthetic and disabled. No source document, receipt,
canonical recording or token is sent to the local static server or an AI provider.
Google's own authorization/Picker requests remain separate from app GET counts.

## Evidence required before acceptance

- Synthetic unit tests: exact account/scope/namespace confinement, forbidden
  methods/hosts/redirects, cancellation/expiry,403/404/429/5xx, budget persistence
  and exhaustion, bounded streaming, malformed JSON/source hashes, stale callback
  races, and no write/token/content persistence paths.
- Local production browser: deliberate script loading, Picker cancellation,
  one-file selection, existing file fallback, visible validation errors and clear.
- CI privacy/contracts/engine/web and required platform checks pass. Review the
  actual diff and any changed dependency/license inventory; no new dependency is
  assumed by this proposal.
- Bounded live test only after approval, with owner-controlled consent/selection.
  Compare the displayed recording locally; keep identifiers and detailed results
  in private evidence. An AI must not inspect the real record without separate
  per-operation consent and provider selection.

No phone migration/build is required by this proposal. Android recording,
delivery identity and existing source defaults remain unchanged. Full workout
acceptance, Template working-copy synchronization/external-edit conflict handling,
automatic synchronization and execution handoff remain separate binding gaps;
this reader does not close M1 by itself. Timing-format styling is deferred.

## Alternatives and references

Manual download/local open already works but does not establish direct web Drive
retrieval. Whole-Drive scopes and a server proxy introduce broader access/data
storage without a need here. Reusing synthetic write helpers would unnecessarily
expose mutation methods. Keep these alternatives out of the connected reader.

- [Google token model](https://developers.google.com/identity/oauth2/web/guides/use-token-model)
- [Google Picker overview](https://developers.google.com/workspace/drive/picker/guides/overview)
- [Drive scope guidance](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
