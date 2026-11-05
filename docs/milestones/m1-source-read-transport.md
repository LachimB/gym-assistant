# Leased read-only source transport

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
The explicit reader captures known working files and the bound head without
depending on a future revision folder or permitting a provider mutation.

The scope fixes the account, common-root/area/selected-Template chain, working/
history/metadata roles and exact source IDs/names/MIMEs. Alias, duplicate,
disconnected or retargeted roles fail. The exposed reader has only namespace,
head and source reads. Its socket profile accepts registered GETs; listing,
name lookup, unknown IDs, folder media and every mutation stop before the request
budget or socket. Normal batches retain the fixed-origin transport.

Each read checks owner-only effective access and stable metadata/content/metadata
tags. Original bounded bytes reach the durable sink before parsing, including
explicit404 namespace/head/source results and malformed/changing observations.
Head Template/working-folder identities must match the binding. Returned source
objects are copied observations, not validated or adopted Training Templates.
Neither a missing head nor an empty pin vector establishes a clean remote state.

The IO executor obtains root/area marker authority from the current leased Room
binding and checks it before constructing a token-bearing batch. Before every
request it rechecks lease, frozen capture origin, binding and markers, then invokes
the caller's reservation. Raw callbacks commit through the same leased observation
store; cancellation/expiry cannot return a completed read result. Batches close
after success/failure. Tokens remain memory-only. No OAuth launch or counter reset.

Six new JVM tests cover exact copied reads, account/access/ancestry rejection,
malformed/changing raw retention, missing objects, sink failure, strict GET socket
bounds and aliased scopes. Local153 Android JVM/36 adapter JVM checks pass;
instrumentation compiles. Two additional SQLite cases exercise the composed
reader's marker/lease/budget checks, retained raw provenance and refusal to return
after expiry, with all twelve pre-sync tables and the original job unchanged.
They require actual current-head execution across all seven supported versions.
Injected HTTP is synthetic-only test wiring; normal callers retain the socket
profile and reservation callback.

Stable complete-vector capture/classification, prior immutable-base verification,
three-way candidate review, transfer-intent preparation, binding/job completion
and authorization/scheduling remain open. Known IDs are explicit inputs; no cold
discovery or filename inference. No enabled worker/UI, schema/default/recording/
dependency change, new live request, resource, allowance, APK assembly or phone
installation. All approved authorization launches remain used. M1 and actual
native/provider/background/screen-off acceptance remain incomplete.
