# Season / Program / Training Template domain acceptance scenarios

**Status:** binding scenarios for first daily-use release (Decisions #79–#97); validation references GA-ORG-* and ADR-0001. `tests/test_organization_contracts.py` covers **only the noted synthetic contract subset**. Most cases still need real Android/web/offline integration and UI evidence.

| Scenario | Given | When | Then | Priority / gate |
|---|---|---|---|---|
| Optional grouping | A Template exists, no Seasons/Programs | Start workout from Library | A real Workout Session starts with no grouping, stored exact Template revision and effective plan | P0, M1 |
| One active Season | Season A Active, B Planned | Activate B | Requires a previewed, confirmed action to pause/complete/abandon A; no two Active Seasons | P0, M3/M5 |
| Season transition cascade | A has three participating Programs | Complete A | Ask disposition for each Program; no Program silently globally completed | P0, M3/M5 |
| Multi-Season continuity | Program P participates in A and planned B | Activate B and carry P over | Same P ID, history continuous, no duplicated Templates or sessions | P0, M3 |
| Standalone coexistence | Season A Active and Program S standalone Active | Start Template from S | Capture S Program only, no A Season | P0, M1 |
| Direct unassigned execution | Same active Season and several Programs | Start Template from Library, choose no context | Valid unassigned Workout Session; don't prompt needlessly | P0, M1 |
| Context ambiguity | Two Active Programs reference Template T | Start T from context-less library and choose context | Clear optional choices; selected Program captured or blank | P0, M3 |
| Participation statuses | P Active but participation in A Paused | Start via P→A | No new A/P-attributed session unless participation made Active | P0, M3 |
| Clone defaults | Dated Active Season with Programs and pinned/latest Templates | Clone Season | New IDs and Draft; dates cleared; template references/policies preserved; no sessions copied | P0, M3 |
| Clone optional Templates | A Program has Template T | Request independent copy | New Template identity only when explicitly selected | P0, M3 |
| Immutable Templates | T revision v1 already used | Edit/release T revision v2 | v1 preserved; older sessions still use v1; newer sessions may use v2 | P0, M1/M3 |
| Program versions | Program references T with latest or pin | Publish compatible T v2 | `latest` adopts validated v2; `pinned` remains v1; session-start snapshot immutable | P0, M2/M3 |
| Incompatible override | Program overrides a set later removed | Publish T new revision | Keep last validated effective plan, mark pending with three-way diff; no silent loss | P0, M2/M3 |
| Parameter overrides | One T shared in Programs A and B | Override resistance in A | B unchanged, base T unchanged, A effective plan validated and traceable | P0, M2/M3 |
| Limited set usage | Composite block has explicit interleaved order | Select/repeat existing sets | Deterministic valid plan with stable source/derived IDs or reject as ambiguous; never silently reorder | P0, M2 |
| Previous performance | Same Template used in A and B | Start from A | Prefer strict same-Program prior across Seasons; fallback SAME Template with disclosed other context; never other Template | P0, M2 |
| Session immutable labels | A Program renamed/moved after a workout | View old Workout Session | Display captured historical IDs/labels/revisions and original effective plan | P0, M1/M3 |
| Session promotion | Completed session differs from planned | Select ONLY one deviation, choose Program override scope, approve | Creates NEW Program revision; no Template change or history rewrite | P0, M3 |
| Shared Template promotion | Completed session differs | Choose Template scope, approve | NEW Template revision; affected follow-latest memberships validated/flagged, no silent upgrade | P0, M3 |
| No automatic promotion | Session completed with modified resistance | Exit review without approval | Future Template/Program defaults unchanged | P0, M3 |
| Stale promotion | Promotion reviewed against P revision r1 | Another device changes P to r2 | Reject stale approval; require fresh compare/consent | P0, M3 |
| Season/Program reporting | Sessions from multiple Seasons and no Program | Open annual journal and web filters | Captured labels in session headers, annual layout unchanged, unassigned included globally | P0, M4 |
| Backups | Organization revisions, participations, session contexts exist | Export and restore safely | All identities and revisions preserved, no duplicated session or lost provenance | P0, M4/M5 |

**Evidence:** CI unit/contract test IDs, device/browser scenario, offline tests, expected vs actual, non-destructive audit outcomes, and user approval before first-release cutover. A passing draft schema test does NOT establish implementation completeness.
