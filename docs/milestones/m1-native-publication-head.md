# Native publication-head transport

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
This component implements the existing guarded head protocol without enabling
a worker, adopting a source, changing Room5 or modifying a Workout Session.

The scope fixes the common account/root and shared selected-Template prefix.
Current working copy, History/revision and Sync metadata must be distinct sibling
branches with the reviewed markers. Immutable/working IDs, hashes and receipts
must agree with the fully compiled manifest; aliases or retargeted branches stop.
The bounded explicit registry accommodates the complete source bundle. Existing
working transport callers retain their narrower defaults and YAML-only writes.

Every namespace check validates the exact account, parent/name/marker and
owner-only effective access. Object reads bracket content with matching metadata
tags. Original bounded metadata/content responses pass to a mandatory retention
sink before decoding, including malformed or changing observations. Sink failure
stops the operation. The sink still requires durable executor integration; an
in-memory test collector is not recovery evidence.

The socket gate permits registered reads and only the captured head mutation.
Replacement requires the original strong tag and exact intended JSON bytes.
Initial multipart creation requires the previously reserved ID and exact
metadata/body; an expected existing head never falls back to creation. Immutable
files and the complete working tag/hash vector are verified before publication.
Lost acknowledgements inspect the same head identity. Later working edits or a
competing head preserve the verified publication receipt as a separate fact.

Twelve synthetic native tests cover these boundaries, lost acknowledgement,
initial409/readback, stale412, missing existing head, malformed/changing original
responses, retention failure and lease expiry. Socket-gate tests throw before
network access even if a future allowlist regression occurs. Local126 Android
JVM/35 adapter JVM checks pass and instrumentation compiles. Current-head CI and
the seven-version Android matrix remain required before merge.

Durable raw-observation storage, immutable/head journal bridging, remote capture,
worker authorization/budget integration and source review remain open. No local
APK assembly, phone installation, live API request, new resource or allowance ran
for this component. All approved authorization launches remain used. Actual
native/background/screen-off acceptance and M1 remain incomplete.
