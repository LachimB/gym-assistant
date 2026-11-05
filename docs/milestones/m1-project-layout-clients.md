# Compatible clients for the approved environment layout

ADR-0009 defines one Gym Assistant root with dev/test/prod containers. Existing
areas keep their provider identities and markers; only their parents and display
names change. This client preparation performs no consolidation operations.

## Exact namespace checks

The optional private layout captures the stable account permission ID, root
binding ID, exact container IDs/parents/names/app-property pins, and the exact
source and final pair for each existing personal/synthetic area. The shared
synthetic fixture is `examples/project-layout/synthetic-layout.json`.

- `reserved`: only the captured source pair; new containers are not yet read.
- `verified`: only the source pair, plus verified root/environment ancestors.
- `adopting`: either the entire captured source pair or the entire final pair,
  with verified ancestors. Mixed old/new names and parents are rejected.
- `active`: only the final pair and verified ancestors. No legacy fallback.
- `blocked`: no provider file operation is permitted.

Each checked folder must have the pinned ID, marker, MIME, parent and name, be
owned by the verified account, untrashed, unshared, and have only that account's
owner permission. Another environment, duplicate name, changed marker, missing
ancestor or unexpected access stops the operation. Production is not an initial
area target and its subfolder design remains deferred.

## Phone and web integration

Android accepts bounded base64 JSON in the private `projectDriveLayout` Gradle
property; public builds default to no layout. Duplicate/unknown fields and invalid
bindings are rejected. Existing delivery setup IDs and connection/job rows stay
unchanged. Personal/synthetic senders and the metadata-only synthetic probe use
the same namespace gate. Simplified names require explicit verified-project
evidence, including on a previously delivered recording's read-only retry.

The gate checks the durable root/account binding before and after ancestor reads,
rejects blocked or regressed state, and prevents an account with a stored root
from silently downgrading to an unconfigured legacy client. The additive schema
approved earlier is unchanged; the DAO adds an exact account lookup. An absent
personal setup may adopt only the reviewed existing area after identity and full
metadata verification. Ordinary recording delivery no longer creates or recreates
a top-level area when its setup is absent or the folder is missing.

The web reader accepts the same optional `projectLayout` in its private localhost
configuration. It verifies the selected personal area's root/dev ancestry before
recording metadata/media. The helper binds each budget request to the normalized
layout hash, so an in-flight page with a superseded layout stops before its next
provider request. Existing allowance counters and caps are preserved. Strict,
bounded private-JSON parsing rejects decoded duplicate keys; no configuration
values enter errors. The configured destination label reflects the nested path.

No new dependency, service, scope or request allocation is introduced. The Gradle
input hash is refreshed for a private configuration field only; the dependency
and source-license inventory is otherwise unchanged. Private inputs and privately
configured APKs remain outside public Git and release artifacts.

## Validation and remaining gates

Local validation: Android97 JVM checks, adapter29 JVM checks, web45 checks and
three localhost-helper checks pass. The web production build and Android
instrumentation compilation pass. These are synthetic contract/regression
results, not evidence of live relocation or physical-device behavior.

Current-head CI, including the supported Android matrix, remains required before
merge or a phone update. The compatible build still requires a fresh verified
private backup and exact private layout configuration before installation. No
live containers have been created, existing areas moved/renamed, sharing changed,
tokens acquired or phone updates performed for this change.

Remaining work includes the reviewed consolidation intent/progress and conditional
folder-update proof, private from/to execution review, hierarchy activation,
preservation checks on the phone/web, and the remaining end-to-end Template
publication, source review and background/screen-off acceptance. Do not activate
an unverified root or opt a Template into synchronization automatically.
