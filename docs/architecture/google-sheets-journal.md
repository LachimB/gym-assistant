# Annual Google Sheets journal projection

## User-facing view

- One Google Spreadsheet per calendar year (`Gym Assistant Journal 2027`); individual tabs `2027-01` ... `2027-12`. Optionally an `Overview` tab and current-month default selection where API/client behavior permits.
- Dates are columns, exercises and their session context are rows, grouped by warm-up, strength, coordination and stretching as relevant, with compact resistance/reps/sets/hold notation. Include duration, location, subjective personal session rating, overall RPE, personal journal notes and links to source session record.
- Workout Session header includes its captured optional Training Season and Training Program names (or neither), without altering monthly tabs/exercise rows.
- One session can include the same exercise more than once; distinguish occurrence/block, do not collapse values into a wrong row.
- Projected values are the canonical history; edits to workout values inside Sheet **never propagate back**. External feedback is retained separately.

## Preservation strategy

Distinguish Google Sheets **cell notes** (available in `CellData.note`) from Google Drive/Sheets threaded comments (different product/API behavior). Do not assume a plain Sheets values API can reliably read/rebuild all comments. To guarantee preservation, add designated **physiotherapist comments/notes** tabs/columns or protected user-managed ranges; do not overwrite/clear them during rebuild. If true threaded comments need preservation, create an explicit validation spike and document limitations. Use narrowly scoped update masks and target cells so unaffected notes/formats/annotations survive.

A monthly rebuild must locate prior session columns by immutable session ID metadata, not mere date (two sessions same day supported). Show diff preview and preserve external annotations. Idempotent retry and optimistic concurrency tests are mandatory.

Official references: https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/cells and https://developers.google.com/workspace/sheets/api/scopes .
