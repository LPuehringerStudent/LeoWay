# Proposed: student features for administrators

**Status: proposed. Team/product-owner approval has not been recorded.**

This is a separate security change from route editor PR #98. It deliberately changes S02/A13 and must
not be merged as routine UI work. Merge #98 first, then review this proposal against `main`.

## Proposed behavior

- An administrator also gets the student role, on login and when restoring the session cookie.
- Admins can use their own timetable, routes and account settings as well as feed monitoring.
- Admin WebUntis access keys are now stored encrypted, like student keys, rather than only checked at login.
- Private endpoints still resolve `CurrentStudent` and enforce record ownership. Another student's route
  continues to return 404; student users still cannot access admin endpoints.
- Admins with old sessions must log out and reconnect once to create their account and encrypted connection.
  A 403 on Home or Route gives an explicit reconnect instruction.

## Review/acceptance

Local validation: 53 frontend tests and 155 backend tests pass; both production builds pass with the
existing CSS-size/Leaflet warnings. `LoginTest` uses an explicit `List` import as requested in review.

- Team/PO explicitly agrees to the S02/A13 change and the storage of admins' own school data.
- Tests cover login/session roles, own data, forbidden access to other users' records and stop search.
- Check an existing admin session and a fresh admin login after deployment.
- After #98 merges, change this PR's base to `main` before deleting the route-editor branch.
