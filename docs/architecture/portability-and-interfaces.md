# Portable contracts and adapter interfaces (logical, not finalized APIs)

The user chose provider-neutral storage and engine semantics with independently configurable auth and AI. These interface sketches are intentionally technology-agnostic so M0 can choose Kotlin/JS or two engines without losing protocol guarantees.

## Domain ports

```text
TemplateRepository:
  listTemplates(filter) → TemplateSummary[]
  loadVersion(templateId, revisionId) → ValidatedTemplate
  validateExternalEdit(rawYaml, baseRevision) → ValidationReport + semanticDiff
  createNewRevision(templateId, expectedBaseRevision, draft) → RevisionResult | Conflict

ExecutionCompiler:
  compile(validatedTemplate, engineFormatVersion) → ImmutableExecutionPlan | Errors
  planHash(plan) → CanonicalDigest

ExecutionRuntime:
  transition(state, inputEvent, monotonicClock) → NewState + DomainEvents + Effects
  replay(planSnapshot, actionEvents) → DeterministicTrace
  snapshot(state) → VersionedDurableCheckpoint

SessionStore:
  appendEvents(sessionId, expectedRevision, events) → CommitResult | Conflict
  readCheckpoint(sessionId) → Checkpoint
  finalize(sessionId, expectedRevision, summary) → FinalizedSession

SyncAdapter:  // Google Drive first, other providers later
  listChanges(cursor) → ChangesPage
  getObject(objectId) → ObjectBytes + Metadata + Revision
  putIfRevision(objectId, expectedRevision, object) → NewRevision | Conflict
  createObject(parent, uniqueKey, content) → ObjectId
  resolveUserFolder(userChoice) → FolderRef + GrantedCapabilities
  revokeAccount() → ConnectionResult

JournalProjector:
  calculateDeltas(sessionRevision, journalYear, month) → Diff
  applyIdempotently(jobKey, diff, protectedRanges) → Status | Conflict
  previewRebuild(period) → Diff + PreservationReport

AIProviderAdapter:  // optional, BYOK-first
  estimate(task, model) → CostEstimate
  draft(task, consent, input) → UntrustedDraft + Usage
```

All return types represent explicit success/error/unknown conditions. Avoid a happy-path-only API that silently converts conflicts to overwrites. Provider file IDs and access grants must never leak into portable domain IDs.

## Storage and concurrency

Use a local transactional outbox: commit state+events+pending sync job atomically. Network sync applies compare-and-swap revision checks and an operation ID. Duplicate operation IDs must be safe to replay; conflict branches remain accessible. Sheets job projection runs *after* successful canonical session sync. Rebuild and multiple-device processing need persistent idempotency, not just local in-memory locks.

## Stable formats and migrations

- `schemaVersion` distinguishes persisted domain schema; `planFormatVersion` and `engineVersion` distinguish execution semantics.
- All changes to persisted structures require documented forward migration, validation and rollback compatibility. Do not mutate historical executed plans.
- The included initial schemas demonstrate the shape, not exhaustive final support for manual waits, repeats, arbitrary control flow, locale fallback or all live session event kinds. Extend during M0/M2 via ADRs and conformance tests.
- YAML uses safe parsing only; no executable tags/custom arbitrary classes. Media referenced by ID, never embedded as base64 in template.
