# Synthetic YAML conditional-content proof preparation

Approved ADR-0009 continuation; GA-DATA-001/002/003/004/010/011. The existing live
JSON-content and folder-metadata proofs do not establish the YAML media/MIME
boundary used by the native working-file transport.

`tools/drive_yaml_content_probe.mjs` accepts only an explicitly pinned synthetic
subject named `SYNTHETIC-conditional-source.yaml`, MIME `text/plain`, below a
labelled test proof folder under the verified common root. It checks the stable
account, exact hierarchy, ownership, owner-only access, private markers and strong
tags. It accepts only its three fixed public made-up YAML strings; unexpected
content stops before writing. It neither discovers nor opens another document.

Before mutation, the caller must durably retain the exact pins and original
snapshot under a current approved journal/lease. The proof uses the native
working-file endpoint shape: v2 media PUT with `Content-Type: text/plain`, an
exact `If-Match` tag and `fields=id`. A metadata/media/metadata read brackets the
content with tags. After a current-tag write, it sends different fixed content
with the old tag, requires412 and verifies that the accepted bytes/tag remain.

An uncertain accepted-write acknowledgement resumes by inspecting the same
saved subject ID. Already accepted content is not written again. A different
source, namespace, tag, account, lease or journal stops; there is no unconditional
fallback, automatic retry, copy, deletion or permission change. Completed proof
replay remains read-only. The caller must supply fixed-origin bounded transport,
per-attempt accounting and atomic durable journal transitions.

Eight synthetic tests cover exact current/stale writes, same-ID lost-ack recovery,
account/namespace/access refusal, malformed and changing reads, false-success
prevention, lease expiry, changed journal facts and caller pin mutation. All35
Drive/helper checks pass locally. These tests use fake providers, not live Drive.

The normal proof consumes27 direct requests, excluding fixture preparation and
authorization. A concrete private bounded fixture/execution plan, owner approval
for any additional authorization and current-head CI precede a live run. Preserve
all earlier ledgers and limits. No live YAML request, new reservation, fixture
creation, credential change, APK build or phone installation has run as part of
this preparation. This does not establish native runtime/background behavior,
immutable-history/head integration, Template review or M1 completion.

## Approved live provider result — 2026-11-05

PR44 merged at `6cd73dd` after all required checks, seven supported Android
versions and the exact owner receipt passed on `a50e5b3`. After the private plan
walkthrough, the owner approved the labelled synthetic fixture,40 requests from
the existing reservation and one additional authorization launch. No provider
operation ran during the review.

The live batch used38 direct requests and one launch. It created only the
reviewed test folder and fixed synthetic YAML, verified their exact identities,
hierarchy, private markers and owner-only effective access, accepted the exact
current-tag media PUT with200, rejected different content with the stale tag
with412, and verified the accepted bytes and unchanged resulting tag. All request
receipts and the completed journal are retained privately. Earlier usage was
preserved; accounting occurred before each attempt, with no subsequent recount
or refund. Two unused request slots remain retained, not silently reallocated.

The runner forgot its in-memory token and was stopped. Existing source/session
content, permissions and phone data were untouched. This establishes the tested
provider `text/plain` YAML media boundary through the browser harness. Native
Android authorization/transport, immutable publication, interruption and actual
background/screen-off acceptance remain separate gates.
