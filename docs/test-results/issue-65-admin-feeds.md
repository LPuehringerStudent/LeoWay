# Issue 65: admin feed status

Local validation on 2026-11-18, branch `feat/65-admin-feed-status`.

This records the initial #65 checks. The route editor keeps the existing admin-only boundaries;
any student-feature access for admins is a separate security proposal requiring team/PO approval.

## Implementation

- Admin navigation between Home and Route, visible only for an authenticated administrator.
- `GET /api/session` reports the authenticated username and admin capability without reading student data.
- `/admin/feeds` uses the existing, server-authorized `GET /api/admin/feeds`.
- Feed table: identifier, OK/STALE/DOWN with text and colour, last success, last error, expandable error details.
- Automatic refresh every 30 seconds, manual refresh, request timeout and no overlapping requests.
- A failed refresh retains the last successful report and explicitly marks it out of date.
- HTTP 401/403 clears the report, stops polling and shows "Not allowed"; guests get a login link.
- Vienna timestamps, mobile cards at 360 px, semantic table and visible keyboard focus.
- `npm start` reads the optional admin allowlist from the root `.env`, with shell settings taking precedence.
- Logout from the header or Route returns directly to the WebUntis login form; a late session response cannot
  restore the logged-out user. Browser verification confirmed the form appears without a document reload.

## Validation

- Backend: all 136 tests passed, including real HTTP authentication for admin/student sessions, anonymous
  rejection and the existing admin-only feed tests. Normal packaging was blocked by a Windows JAR lock from
  the already running app; packaging into `target/admin-verify` succeeded with tests skipped after that test run.
- Frontend: unit tests cover role-based navigation, admin login destination, polling interval, manual refresh,
  component cleanup, overlapping requests, empty/loading/error states, recovery, denied access and escaped error text.
  All 37 tests pass, including actual router navigation after logout and the late-session-response regression.
  Admin sessions on Home and Route never call student-only APIs: Home shows its guest view, while Route
  explains the admin role and links to the admin panel. Both paths have regression tests that retain the admin session.
- Production Angular build succeeds. The page stylesheet is approximately 6.1 kB after compilation, above
  the 4 kB warning threshold and below the 8 kB error threshold.
- Playwright: desktop 1280 px and mobile 360 px have no horizontal overflow. Admin is between Home and Route.
  Keyboard reaches the skip link, brand, navigation, Refresh and Error details with visible focus; Enter opens details.
- axe-core 4.10.3: no detected WCAG 2 A/AA or 2.1 A/AA violations in the mobile OK and STALE/DOWN states.
- Browser checks also cover retained data after a failed refresh, student denial, guest denial and hidden Admin navigation.
- Screenshots in `docs/screenshots/admin-feeds-desktop.png` and `admin-feeds-360.png` use intercepted,
  synthetic API responses. They demonstrate layout, not production availability.

## Remaining sprint acceptance

Not deployed or checked against an authenticated production admin. After merge and deployment, an administrator
must open `/admin/feeds` on production and confirm `linz-ag` reports OK. A teammate's review, green CI,
time tracking and product owner acceptance are still required before Done Done.
