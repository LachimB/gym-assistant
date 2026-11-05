# Native selected-Template working-file transport

Approved ADR-0009 implementation; requirements GA-DATA-001/002/003/004/010/011.
This isolated transport does not enable a UI connection or execute a live batch.
Existing personal/synthetic session-delivery transport allowlists are unchanged.

## Implemented boundary

- Pinned stable account permission ID and a bounded exact-ID folder chain to the
  approved common project root. Every folder must match its parent, name, private
  app marker, MIME, owner and non-trashed state. Cycles, missing roots and unexpected
  parents stop. No name-only selection or root/parent creation fallback.
- Source access requires successful account/namespace verification; a failed
  recheck revokes it. Only the immutable intent's known working files are readable
  or writable. A batch factory derives the network allowlists from those pins.
- Drive v2 metadata/tag → raw bytes → metadata/tag read captures detect changing
  files. Validate ID, title, MIME, private marker, parent, ownership and trash state.
  Reject missing/malformed metadata, duplicate JSON keys, invalid UTF-8 and bounds.
- YAML replacement uses only the captured exact strong tag in If-Match. Wildcard,
  missing or retargeted tags fail;412 stops without an unconditional fallback.
- Reserved-ID multipart creates stay inside the pinned working folder.200/409
  still require exact same-ID readback by the guarded per-file protocol. A lost
  write/create acknowledgement resolves by inspecting that same saved object.
- Bearer tokens stay in memory, never URLs or persisted request records. Fixed
  Google HTTPS origin, strict paths/methods, no redirects, bounded I/O/timeouts and
  per-attempt budget reservation before network access. Closing clears the token
  and disconnects an active connection; stale-lease acknowledgement prevention
  remains the durable executor's responsibility.

## Evidence and remaining integration

Ten synthetic JVM tests cover exact-tag/lost-ack writes, stale-tag refusal,
reserved-ID create/409/retry, account/root/parent mismatches, changing reads,
malformed/oversized metadata/content, unknown IDs, graph cycles and native endpoint/
header gates. Invalid native requests fail before any socket or budget consumption.
They also verify that a failed namespace recheck blocks subsequent source access
and reject string-valued ownership flags before downloading source.
Android instrumentation compiles. Current-head CI is still mandatory.

No real token, Drive request, folder creation/move, allowance reservation, local
APK assembly or phone installation occurred. Live YAML conditional-write behavior is
still unproven. These fake responses do not establish screen-off/background or
physical-device behavior. Durable progress/history/head publication, observation/
review UI, scheduling/auth integration and compatible namespace adoption remain.
Keep exact private inventory approval before relocation and CI/fresh verified
backup before any phone migration. Each metadata/media/retry call must fit the
existing non-resetting allowance; exhaustion leaves pending work.

API mapping references: [Drive v2 file metadata](https://developers.google.com/workspace/drive/api/reference/rest/v2/files),
[Drive v2 media update](https://developers.google.com/workspace/drive/api/reference/rest/v2/files/update).
Actual current-tag/stale-tag YAML evidence must precede live mutable integration;
metadata documentation alone is not proof of that conditional-write boundary.
