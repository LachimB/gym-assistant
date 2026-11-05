# Replayable captured source evidence

Approved ADR-0009 continuation; GA-DOM-006 and GA-DATA-001/002/003/004/010/011.
Internal evidence files retain a checked capture pointer, exact folder pins and
the start of a complete response range. Known-base evidence also retains its
prior publication pointer and revision-folder pin. Records are strict, bounded
and immutable; source/raw bytes stay in existing hashed private files.

Every load revalidates the capture/origin, all retained files and the complete
bounded response sequence. Vector replay repeats the existing double-vector/head
bracket protocol using recorded metadata/content only. Base replay repeats the
known-publication scope, namespace, identity, owner-only access, tag, exact-byte
and complete compiler/hash checks. Skipped, partial, reordered or trailing frames
cannot become a complete proof. Changed retained metadata or publication files,
unknown record fields/kinds/versions, nested references and invalid ranges fail.

Replay performs no provider call and is not a new account/token authorization
receipt. It validates stored owner/namespace facts against the frozen binding;
future provider activity still requires live account/permission/lease checks.
Malformed working YAML and a consistently absent head remain original observed
facts. A vector proof deliberately contains no stored valid flag and no execution
or adoption authority. Compilation/classification remains a separate decision.

Preparing proof files leaves the observation's SQL pointer/state unchanged.
They are inert until a future classified-observation transaction verifies the
current lease, matching capture origin/ranges and exact expected SQL pointer.
Old valid evidence remains historical evidence; it must not be treated as the
latest observation after another capture. Interrupted preparation can retain an
unreferenced private file, never an acknowledged candidate or new default.

Local174 Android JVM/36 adapter JVM tests pass. Five new JVM cases cover replay
after reopen, preserved malformed bytes/absent head, incomplete/skipped/trailing
ranges, strict decoding/nested references and namespace/file tampering. Two
additional SQLite cases compile: combined base/vector replay after reopen, with
the actual observed competing head kept distinct, and rejection of wrong ranges/
kinds or a changed retained publication. Original job and all twelve pre-sync
tables remain checked. Actual current-head seven-version CI is still required.

No database/canonical schema, recording, default, dependency, live request,
authorization launch, allowance reset, resource, enabled worker/UI, APK assembly
or phone installation change. Existing private observation-file preparation is
extended; classification, explicit field-by-field review, transfer preparation/
completion and authorization/scheduling remain open. All eight live authorization
launches and both diagnostic executions remain used. M1 and actual native/provider/
background/screen-off acceptance remain incomplete.
