# Android synthetic Drive connection probe

Part of the user-approved M1 synthetic-only Drive experiment and registered Android
OAuth client; not production synchronization or a broader service/data approval.
Uses the existing personal development project, same debug package/signature and
only `drive.file`. No new Cloud project/API/key, billing or hosting. ADR-0004's
human history export is separate; this probe never calls that export or uploads
Workout Sessions.

## Prepared implementation

- Google Identity `AuthorizationClient` from `play-services-auth:22.0.0`, as shown
  in the current official Android authorization guide. Package/signature-bound
  Android client was already registered in the approved synthetic project.
- Explicit button only, before starting a workout. No automatic sign-in/network
  request at startup, background sync or account switching. Local test APK is
  pinned to the already selected personal email via a private Gradle property;
  default/CI builds have a blank account and disable the test button. Email is an
  identifier embedded in that private APK, not a token, and is not committed here.
- Request exactly drive.file and opt out of including previously granted scopes.
  Reject any returned scope set other than the exact requested scope. No OpenID,
  profile/userinfo, server auth code, offline-access request, backend or client
  secret. Existing Google Play services may manage their own account/token cache;
  the app does not save access/refresh tokens in preferences, Room or logs.
- Pin `AuthorizationRequest.setAccount` to the configured personal account. Then
  verify `about.get(fields=user(emailAddress,permissionId))` before file reads.
  Drive documents about.get as allowed under drive.file; do not infer identity
  merely from an authorization token or account hint. Do not persist the returned
  permission ID in this probe.
- Read only the already labelled synthetic parent and selected external JSON's
  metadata. Verify exact name, MIME, marker, ID and sole parent; no arbitrary URL,
  file search, file-content read or write. Report only result text and whether a
  native HTTP ETag header is present; do not expose its value. Native preconditions
  remain untested even if an ETag is available.
- HTTPS fixed endpoints, no redirects/cache, 10-second connect/read timeouts,
  65,536-character metadata limit. Error bodies and SDK/network exceptions are not
  logged or shown. No retry/scope escalation. Duplicate callbacks cannot start
  another simultaneous probe.
- Application Internet permission added; no contacts/account-list permissions.
  Existing no-backup policy and Room schema remain unchanged. App versionCode 3,
  versionName 0.1.1-m1-drive-probe; update installation preserves the package/data.

## Budget

Existing direct/setup counter was 30/500. The localhost experiment UI now reserves
20 requests once for this one-phone stage, advancing its counter to **50/500**
conservatively, before any native request. Reservation is not a claim that those
requests have occurred. Android persists a non-secret counter before every direct
request, refuses request 21 and allows at most six explicit authorization attempts.
No counter reset/clear-data/reinstall workaround. A success uses three direct Drive
requests. Google authorization SDK internal traffic, like earlier Picker traffic,
is not measured by the direct Drive counter; keep UI attempts bounded and record
actual results separately. Do not run this reserved stage on multiple devices.

## Build and phone test

The local property must be supplied only with the user-approved personal account:

```sh
./gradlew -PsyntheticDriveAccount=YOUR_APPROVED_PERSONAL_EMAIL :android:testDebugUnitTest :android:assembleDebug
```

No credentials go in Gradle properties files or Git. Default builds disable the
probe. The user must connect/unlock their authorized phone for `adb install -r`;
never uninstall/clear data and never install instrumentation tests on that phone.

1. Open the start screen, scroll to **Drive connection test**. Confirm the account
   displayed is the approved personal account.
2. Tap **Test personal Drive connection**. Complete Google's consent if presented;
   verify the app name and specific-files permission. If a different account or
   broader scope is shown, cancel and report it. Do not accept new legal terms
   without a separate user confirmation if any are unexpectedly presented.
3. Send the final app status text. Expected: personal account verified, synthetic
   folder/file accessible, then ETag availability; or an explicit inaccessible
   selected-file result. Either is feasibility evidence, not production sync.
4. Existing workout records must remain intact. This test does not start a workout,
   change its source, upload its history, revoke account grants or share/delete files.

## Validation and limits

Eight fake-transport JVM tests cover exact scope, account-first checks, expected
synthetic metadata, inaccessible child grant, identity/folder/child mismatch,
no retry on auth/redirect/server error and fixed-endpoint rejection before budget
or network. They make no Google requests. Existing nine history JVM tests and
12 real SQLite tests also pass locally. Phone OAuth/Google Play services behavior
and native ETag availability remain unverified until the user test.

Primary sources checked 2026-11-03:
[Android authorization](https://developer.android.com/identity/authorization),
[AuthorizationRequest account/scope controls](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationRequest.Builder),
[authorization result](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult),
[Drive about.get scopes](https://developers.google.com/workspace/drive/api/reference/rest/v3/about/get).


Prepared local APK SHA-256 (private account-configured build):
`f38797518d4434df63163c0785ecff2d8513e712d16100a08deed94bdb5b998f`.
Installed successfully with `adb install -r` on the user-confirmed Pixel 10a /
Android 17 after verifying the APK hash and registered signing SHA-1. No uninstall,
data clear, instrumentation install or phone database export. Google consent and
final probe status are pending the owner’s action. Remote CI APKs
use blank account configuration and are not the phone-test artifact.
