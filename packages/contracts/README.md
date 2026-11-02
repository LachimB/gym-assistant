# Data contracts

The local M1 contract loader is `tools/template_loader.py`. Run it from the repository root:

```sh
python3 tools/template_loader.py examples/templates/composite-pyramid.yaml \
  --exercise examples/exercises/bridge-band-family.yaml \
  --exercise examples/exercises/plank-family.yaml
```

It reads local files only and returns a fresh validated document through
`load_template(template_path, exercise_paths)`. It rejects duplicate YAML keys,
aliases/merge keys, unsafe tags, non-JSON values, files over 1 MiB, nesting over
64 levels, schema errors, duplicate structural IDs, unresolved family/variation
and block-local set references, and incomplete/duplicate explicit repetition
coverage. Phase IDs must be unique within each set. Errors omit source values.
These limits describe this loader, not new product-wide content limits.
The loader itself does not compile plans, resolve versions, cache last-valid
revisions, publish immutable records, apply Program overrides or integrate with Android.
Read-only external-edit review is available as the separate experiment below.

## Local external-edit review experiment

```sh
python3 tools/template_review.py examples/templates/composite-pyramid.yaml \
  examples/templates/composite-pyramid.yaml --expected-revision revision_demo_v1 \
  --exercise examples/exercises/bridge-band-family.yaml \
  --exercise examples/exercises/plank-family.yaml
```

Use a separate candidate path to review an edited copy. `review_external_edit`
validates the known-good base, checks the caller's expected revision, validates
the candidate, and reports changed JSON Pointer paths plus structural identity
additions/removals and retargeted occurrences/sets/steps. Ordering changes remain
visible while identity matching uses IDs. Reused revision IDs with changed content
and changed Template IDs are rejected. Both source files remain untouched in
every case. CLI exit 0 means a preview was produced or the input was unchanged;
it never means approval or adoption. Invalid/stale inputs exit 1. Output contains
local paths/IDs and must not be sent automatically to issue reports or services.

Durable quarantine, accepted revision publication, full effective-plan validation
and commit-time concurrency guards are pending. The existing schema and reference
exercise library determine validation; this is not a general compatibility oracle.

Draft JSON Schemas live in `/schemas`. YAML/JSON fixtures live in `/examples`. The contracts are not final until M0 compiler/compiler tests and an ADR. Maintain schema versions and compatibility tests; reject duplicate IDs/dangling references in semantic validation beyond JSON Schema.

Canonical names: Training Season, Training Program, Training Template, Workout Session. Optional organizational layers are modeled as Season, Program, separate Season participation and Program→Template membership with stable IDs and immutable structural revisions. Schema drafts in `schemas/` include `training-season`, `training-program`, `season-participation`, and `promotion-proposal`. JSON Schema alone cannot enforce one Active Season, valid cross-object references or safe history; use collection-level validation (see `tools/organization_contracts.py` example).
