# M1 local web history reader

The web app can open a local canonical Workout Session JSON file and display its
meaningful events, frozen exercise labels, outcome and separately recorded timing.
This is a read-only viewer; Drive retrieval and Android export delivery remain open.
Nothing selected is uploaded or persisted by this feature. Two explicitly labelled
synthetic examples allow inspection without personal data.

## Contract and validation

- Accept existing 1.0.0 and approved ADR-0004 1.1.0 formats using the checked-in
  schemas. Reject unsupported versions, invalid data, duplicate event identities,
  unsafe integers and oversized inputs (8 MB, 10,000 events/phases).
- For 1.1.0, verify the exact retained compiled-plan UTF-8 hash with Web Crypto,
  phase/source references, contiguous plan, timing, captured local date/time zone
  and final outcome. Do not repair or silently normalize files.
- Render meaningful events, including skips, known/unknown interruptions, pauses
  and partial completion. Observed running time includes planned rests; it is not
  presented as active exercise time. Reaching planned end does not prove every
  repetition was performed. Recorded event timestamps are not reconstructed
  phase-boundary timestamps.
- Preserve older recorded durations, set results, effort, quality and notes when
  available. Missing facts remain unrecorded; no per-set counts are inferred.
  Frozen family/variation labels are scoped by family. Captured organization
  names are displayed without resolving current Program/Season membership.
- Internal ticks/receipts are excluded from 1.1.0 rather than merely hidden.
  File content is rendered as text; labels use bounded safe YAML inspection.
  Asynchronous file reads cannot replace a newer selection or a cleared view.

## Evidence — 2026-11-03

- 20 web tests pass, including Kotlin-generated fixtures, hash/contract rejection,
  legacy results, separate quality/effort, escaped notes and label scoping.
- Production TypeScript/Vite build passes; history validation is loaded separately
  from the simulator. Dependency audit reports zero vulnerabilities.
- Actual production browser: partial example shows 16 meaningful events, observed
  5.75 s, pause 1.2 s, known gap 0.9 s and two unknown interruptions. Opening the
  completed synthetic JSON through the file chooser shows 21 events and 50 s.
  This is local-file UI evidence, not connected Drive or real-workout acceptance.
- No Android schema migration/build, account grant, real source import, production
  deployment or real-history cloud upload is introduced by this change.
