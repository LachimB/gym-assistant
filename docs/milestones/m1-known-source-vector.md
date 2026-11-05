# Complete known-source vector observations

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
The internal reader now captures all explicitly pinned working sources twice,
checking the bound head before, between and after those passes, and verifying
the namespace at both ends. It does not list or infer additional source files.

Both passes must agree on each file's complete pin, strong provider tag, original
metadata and exact content bytes. Head identity, tag, decoded fields and original
bytes must agree across all brackets. Missing sources, new/missing/changed heads,
weak tags, malformed UTF-8, duplicate/aliased pins or bounds violations cannot
return a completed vector. A consistently missing head remains explicit unknown
publication state; it never proves an empty namespace or permits replacement.

The existing leased read executor charges every HTTP request and durably retains
original responses before interpretation. A changed second pass therefore keeps
both observed branches even when capture fails. Cancellation, lease expiry or
namespace loss prevents returning a completed result. The in-memory vector is
only a read result: no completed-observation record, candidate, adoption, binding
advance or whole-job acknowledgement is introduced.

Compilation uses the existing bounded YAML schemas/compiler and the declared
Template/exercise roles. It sorts exercises by their declared stable family IDs,
requires the bound Template identity and preserves exact source bytes. Canonical
source and plan hashes match the existing import/export contract. An external
author's unchanged revision ID is preserved as an observed fact; proposing a new
revision identity remains part of the later explicit publication preview.
The working source may differ from the published head, so compilation does not
silently require equality with the head's immutable-source hash.

Equal repeated observations do not create a multi-file provider transaction or
prevent a later edit. Immutable manifest/base verification, explicit diff/review
and fresh pre-publication checks remain mandatory. Neither compilation nor an
absent head establishes publication/adoption authority. Invalid original bytes
remain in the existing raw capture; this component creates no executable default.

Local163 Android JVM and36 adapter JVM tests pass. Ten new JVM cases cover complete
capture order, changed tags/bytes/metadata, head changes, missing/aliased inputs,
UTF-8/size bounds, namespace loss, sink failure, cancellation, canonical hashes,
preserved external bytes/revision identity and invalid dependencies. Instrumentation
compiles with two new SQLite cases: complete capture/reopen and a changed second
pass, retaining raw provenance while preserving the original job and all twelve
pre-sync tables. Those cases still need actual current-head seven-version CI.
The two-file fake records39 charged GETs and37 retained observations; these are
offline evidence, never a live allowance or measured SDK traffic.

No schema, recording, default, dependency, enabled worker/UI, live Drive request,
new service/resource, allowance reset, authorization launch, APK assembly or phone
installation. All approved live authorization launches remain used. M1 and actual
native/provider/background/screen-off acceptance remain incomplete.
