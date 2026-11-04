# Local personal Drive history reader

ADR-0008 was approved after explanation on2026-11-04. This implements explicit,
read-only selection of one delivered format1.2 recording. It does not implement
Template synchronization, automatic history browsing, execution transfer or M1
completion. Local-file1.0/1.1/1.2 reading remains available.

## Private activation

Build the ordinary public-default web app, then use the checked-in loopback
helper instead of a plain static preview when performing the approved live test:

```sh
npm --prefix apps/web ci
npm --prefix apps/web run build
GYM_PERSONAL_DRIVE_CONFIG='<private-config-file>' \
GYM_PERSONAL_DRIVE_LEDGER='<private-ledger-file>' \
node tools/serve_web_history.mjs
```

The helper binds only loopback port4173. Never terminate another process without
first identifying it and preserving the owner's open work. Public builds contain
no live identifiers; a normal static preview fails closed on connected access.
Do not put private inputs under the served `dist` directory or any checkout.

Private configuration has exactly `clientId`, `apiKey`, `appId`, `email`,
`permissionId`, `folderId`, `marker`, `allocationId`. Reuse and privately verify the
existing approved project/client/key and the Android-established personal binding;
never create another root or select an account by name alone. No client secret.
The API key remains restricted to Picker and the exact approved web/Picker
referrers. Existing origins/referrers remain; only the approved loopback4173 pair
is added. Store the configuration and ledger with owner-only access outside Git.

The ledger contains `version:1`, a matching `allocationId`,
`reservationVerified:true`, reconciled `globalAllocated` no greater than500,
and `used` counters for `request`, `authorization`, `picker`. Initialize those
only while reserving the approved30 direct attempts once within the existing
allowance. Caps are30/4/2. Keep the older Android/experiment reservations. Do not
reset counters, refund allocations, regenerate a ledger after loss, or treat
unmeasured Google SDK/Picker traffic as part of a proven direct HTTP count.

A same-origin helper endpoint fsyncs the incremented ledger and directory before
allowing a transmission. Browser reload/storage clearing cannot restore allowance.
Missing/invalid ledger or failed writes block live access. The helper receives
configuration/budget operations only; tokens, selected file IDs and session bytes
travel directly between the browser and Google, never through the local helper.

## Owner flow and verification

Workout history → Open from personal Drive → review the account/destination →
Connect and choose recording. Google's account chooser and single-file Picker
remain owner-controlled. Cancel if the account or permission differs. Choose one
already-delivered recording in the approved personal folder. The reader verifies
account, root marker, file identity/parent/name/MIME and the complete frozen/effective
history contract before displaying it. It makes no Drive writes.

Disconnect and clear, Clear view, another source selection or leaving history
invalidates earlier results. Tokens are held in memory only during the operation
and forgotten after completion/failure; opening another file requires explicit
authorization again. No revoke/sign-out of other apps. No background token refresh.
File/account/source values and provider bodies never belong in public evidence.

## Validation and limits

Synthetic transport tests cover wrong account/scope/root/file, malformed or
oversized content, exact session filename identity, cancellation, stale callbacks,
provider errors, persistent budget failure and separate authorization per open.
Loopback tests cover host/origin confinement, traversal, missing private paths,
monotonic counters and exhaustion. Existing history tests validate frozen/effective
plans independently. The production build and privacy/license checks must pass.
Actual browser behavior and the bounded owner-selected live read remain required.
No physical phone migration or application update is introduced by this web slice.
