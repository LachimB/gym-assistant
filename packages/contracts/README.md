# Data contracts

Draft JSON Schemas live in `/schemas`. YAML/JSON fixtures live in `/examples`. The contracts are not final until M0 compiler/compiler tests and an ADR. Maintain schema versions and compatibility tests; reject duplicate IDs/dangling references in semantic validation beyond JSON Schema.

Canonical names: Training Season, Training Program, Training Template, Workout Session. Optional organizational layers are modeled as Season, Program, separate Season participation and Program→Template membership with stable IDs and immutable structural revisions. Schema drafts in `schemas/` include `training-season`, `training-program`, `season-participation`, and `promotion-proposal`. JSON Schema alone cannot enforce one Active Season, valid cross-object references or safe history; use collection-level validation (see `tools/organization_contracts.py` example).
