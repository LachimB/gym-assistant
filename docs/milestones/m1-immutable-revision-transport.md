# Native immutable revision-file transport

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
This component does not enable a worker, a Template binding or a live operation.

## Contract

- Full retained-source hash verification and existing compiler validation finish
  before transport construction. Source/manifest IDs are distinct and cannot
  overlap any pinned namespace folder. YAML history uses `text/plain`; the
  canonical manifest uses `application/json` and `revision.json`.
- An immutable per-file step copies its bytes and captures the reserved ID,
  folder, marker, name, MIME, byte count and hash. Its caller must retain those
  facts durably before use. Current ownership and a still-pending creation are
  required before a create. An acknowledged object that later disappears cannot
  be recreated by this protocol.
- A bounded exact-ID chain verifies the stable account and common root through
  the revision folder. v2 metadata must match ownership, owner-only effective
  access, private properties, parent/name/MIME, trash state and a strong tag.
  Failed namespace rechecks revoke subsequent source access.
- Metadata/media/metadata reads bracket the bytes with equal tags. Missing
  objects can be created only at their originally retained reserved IDs. A409 or
  uncertain acknowledgement requires exact same-ID readback, never replacement
  or another ID. Different existing bytes stop without repair.
- The immutable socket gate has no PUT/PATCH/DELETE route. It checks every
  multipart field and byte against the captured object before request accounting
  or network access. Only pinned metadata/media reads and reserved creates reach
  the existing fixed Google HTTPS transport, with its no-redirect policy,
  bounded I/O and memory-only token lifetime. Closing disconnects/clears it.
- The existing working-file client keeps its original metadata allowlist by
  default; the immutable wrapper explicitly selects the additional permission
  metadata projection. Legacy recording transports are unchanged.

## Evidence and limits

Local107 Android JVM and34 adapter JVM checks pass; Android instrumentation
compiles. Five new protocol cases cover same-ID lost-ack recovery, immutable
conflicts, durability/lease/namespace refusal, copied bytes and refusal to recreate
an acknowledged missing object. Eight native cases cover source/manifest create
readback,409/lost acknowledgement, whole-source validation, wrong account/root/
sharing, malformed or changing reads, revoked namespace access and exact socket
allowlists. Valid multipart bodies reach a synthetic budget stop before any
socket; mutated/duplicate metadata bodies do not consume a request. No test uses
a real token or provider.

The separate approved browser YAML provider proof passed; see
[m1-yaml-conditional-proof.md](m1-yaml-conditional-proof.md). It is not evidence
that this native component executed on a phone. Current-head CI remains required.
No APK was assembled locally, phone installed, schema changed, live native request
made or extra allowance reserved. Durable Room receipts, publication-head native
transport/coordinator, remote capture, source review and worker integration remain
open. Creating all files is not publication, local adoption or job completion.
