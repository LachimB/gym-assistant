# Gym Assistant starter v2 — revision summary

**Approved decisions:** #1–#97, including the newly approved optional organization hierarchy and Decision #97 on explicit selective promotion.

## Changed from the original starter

- Canonical naming: **Training Season**, **Training Program**, **Training Template**, **Workout Session**.
- Optional, many-to-many Season↔Program participation, at most one globally Active Season, independent standalone Programs and optional ungrouped workout execution.
- Full lifecycle semantics (Draft, Planned, Active, Paused, Completed, Abandoned; Archived separate), explicit transition and per-Program completion review.
- Immutable Training Template revisions and significant Season/Program structure revisions; latest/pinned membership policies and version-compatible Program overrides.
- Safe clone defaults: new IDs, Draft state, **clear start and end dates**, no duplicate historical sessions, reuse existing Training Templates by default.
- Workout Sessions preserve immutable execution plan, structural identities, optional Season/Program identifiers/revisions/captured labels, and strict prior-performance matching with Program preference and transparent same-Template fallback.
- **Decision #97:** completed-session planned-vs-actual review may selectively promote chosen deviations to either a new Program override revision or a new shared Template revision; never automatic.
- Added four new JSON Schemas for organizational objects and promotion, additional draft session fields, 9 synthetic fixture records, an acceptance matrix, ADR-0001, and updated milestone requirements.
- Retained original M0 feasibility focus and 50-second synthetic workout; added negative contract tests; no external services configured or provisioned.

## Validation and limitations

- Runnable checks: `python3 -m unittest discover -s tests -v` and `python3 tools/validate_starter.py`.
- Includes **draft contract checks, not functioning app code**. The reference override resolver deliberately refuses nontrivial set pattern selection/repetition until M2 defines a deterministic compiler expansion. Cross-device transitions, UI, real Drive OAuth, background audio and journal rendering still require integration testing.
- Architecture gaps tracked in `docs/architecture/open-questions.md`. New organizational scenarios in `docs/product/organization-acceptance-scenarios.md`.

## Migration from prior ZIP

For an uninitialized repository, use **this v2 package instead of the original ZIP**. If an older starter was already committed, review the diff and merge documentation/schemas/tests carefully; never overwrite user implementation or real data. GitHub personal-identity and service/billing approval preflights remain required.
