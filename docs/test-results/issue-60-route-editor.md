# Issue 60: route editor and stop map

Validated locally on 2026-11-18, branch `feat/60-route-editor`.

## What is included

- Route section below WebUntis: home/school stop suggestions, walking times and a suggested **10-minute buffer**.
- Create, edit, select and delete saved routes; deletion asks for confirmation.
- Whole-minute validation: walking times 0–60, buffer 0–30, two different selected stops.
- Debounced search, cancellation of outdated queries, cached stop names and retries respecting `Retry-After`.
- Unavailable search, no matches, server validation errors, save/delete failures and retry states.
- Actual OpenStreetMap tiles and public Linz AG stop locations. The dashed connector is explicitly labelled
  as a stop overview, not a calculated bus journey. No invented departure or arrival times.
- Only stop ids and timing settings are saved. Stop names and coordinates are held in memory and resolved
  again after a reload; no home address, GPS position or browser storage is used.
- The admin/student permission extension is excluded from this PR. S02/A13 keep admins restricted to feeds
  and health; the extension needs explicit team/PO approval in a separate security PR.

## Checks

- **53 frontend tests pass**, covering the form, keyboard picker, debounce, stale results, Retry-After,
  create/update requests, validation, failed-save retry, delete confirmation and delete retry.
- **155 backend tests pass**, including admin login and ownership, optional/malformed coordinates, stop filtering
  and requesting WGS84 coordinates. Full `mvn verify` succeeds with packaging in `target/route-verify` to avoid
  Windows file locks from the already running app. OpenAPI was regenerated into `docs/api`.
- Production frontend build passes. Existing admin CSS and the route stylesheet exceed the 4 kB warning
  threshold but remain below the 8 kB error threshold. Leaflet 1.9.4 also has the expected CommonJS warning;
  its map code is loaded lazily.
- **Lighthouse accessibility: 100/100 at 360 × 800**, using a temporary loopback test API with synthetic data.
  Only this summary is retained in Git; generated full reports are not committed.
- axe-core reports no violations in the populated editor and saved-route views at 360 px.
- Browser keyboard check: select both stops with suggestions, set walking times, save, edit the buffer and
  delete with confirmation. No browser reload is needed after saving or editing.
- Reload check: the edited buffer, stop names and both map pins return from the saved route ids.
- No horizontal overflow at 360 px or 1440 px. The mobile header keeps logout alongside the brand.
- One live public Linz AG lookup verified stop-id resolution and WGS84 coordinate output. Automated tests use
  recorded fixtures or fake servers, not repeated production queries.

## Screenshots

- `docs/screenshots/route-editor-desktop.png`
- `docs/screenshots/route-editor-360.png`
- `docs/screenshots/saved-route-desktop.png`
- `docs/screenshots/saved-route-360.png`

Screenshots and Lighthouse use synthetic account/route responses, with real public stop positions and map tiles.
They demonstrate the UI and do not claim production acceptance.

## Still required before Done Done

Deploy after review and green CI. On production, save and edit a route with keyboard only and confirm that
admins still see the admin panel without requesting student data. Any extension to administrator permissions
requires separate team/PO approval before merge.
Time tracking and product owner acceptance remain separate sprint requirements.

## Review fixes

- Privacy information names OpenStreetMap and Linz AG and explains IP/map-area and stop-search sharing.
  The editor links to that information.
- The suggested buffer is 10 minutes in the UI, API, OpenAPI schema, entity and database default. Migration V7
  changes future inserts only; existing students' saved values are unchanged.
- Stop-id resolution uses one shared queue with duplicate requests shared. Tests cover four distinct stops,
  duplicate ids and continuing after a failed lookup; a production-build browser check used six routes and
  observed at most one active stop lookup.
- HTTP 403 from student endpoints displays an explicit logout/reconnect message on Home and Route.
- Map resizing calls invalidateSize without fitting bounds, preserving manual navigation. Leaflet styles load
  only when the map opens. The initial stylesheet is back to 2.21 kB; a production browser check verified both
  lazy assets and Leaflet's CommonJS/default-export compatibility.
- Prettier formats the route files. The keyed editor loop explains why it recreates the editor.
