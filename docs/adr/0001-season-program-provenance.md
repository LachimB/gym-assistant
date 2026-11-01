# ADR-0001 — Optional Seasons and Programs, immutable workout provenance

- Status: **Approved product direction** (#79–#97), technical mechanics to validate in M1/M2
- Context: A reusable Training Template may be reused across multiple Training Programs and Seasons. Programs may be standalone or span Seasons, while Workout Sessions must remain stable after edits.
- Decision: Model Seasons, Programs, memberships and Season participations by independent stable IDs. Use immutable revision IDs for Training Templates and structural changes to Programs/Seasons. Capture immutable optional context at Workout Session start. Prefer same-Program previous performance with transparent same-Template fallback. All organizational groupings optional. Optional Program parameter overrides are compiled to a validated effective plan. Selective post-workout promotion needs explicit approval.
- Rejected: strict nested ownership; duplicating history on clone; mutating executed Template revisions; automatic promotion of performance deviations; automatic dropping of incompatible overrides.
- Trade-offs: more identity/schema/validation logic than folders; explicit migration and conflict handling for membership revision changes; history remains auditable and future schedules optional.
- Confirmation: see `docs/product/decision-register.md` #79–#97.
- Implementation follow-up: exact set-pattern repetition semantics, expected hash calculation, and multi-device revision conflict policy need ADRs/tests before production.
