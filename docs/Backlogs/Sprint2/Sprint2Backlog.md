# Sprint Backlog — Sprint 2

**Sprint Duration:** 3 weeks (2026-11-05 – 2026-11-26)
**Team Size:** 4 developers (Laurenz, Ayan, David, Behrang)
**Product Owner:** Behrang (@BehrangEl11)
**Board:** [Sprint2](https://github.com/users/LPuehringerStudent/projects/15)

**Sprint Goal:** A student sees a real leave-by time on Home and gets a warning when their bus is late.

This finishes Block A of the specification (F02) and starts Block B (F03, first channel of F04). Every task has an
issue with tasks and acceptance criteria on GitHub. Sizes: S = up to a day, M = several days.

### From the Sprint 1 review (2026-11-12)

| Feedback | What we do in Sprint 2 |
|----------|------------------------|
| Time tracking | Everyone logs hours in the teachers' time-tracking Excel on SharePoint (link from the teachers, not in this public repo). At the end of the sprint a copy goes to `docs/time-tracking/Sprint2.xlsx`. |
| Done Done column | GitHub moves issues to **Done** on merge. **Done Done** is set by hand when the whole Definition of Done below is met and Behrang accepted it. |
| No bypassing code reviews | Every pull request needs one approval from a team member other than the author; all four are code owners now. Nobody merges with the admin bypass. |
| Priority tags | Every open issue has a `priority: high`, `priority: medium` or `priority: low` label, taken from the [product backlog](../ProductBacklog.md). Work starts with `high`. |

---

## Epic 1: Sprint 1 carry-over

#27/#3, #24, #50, #51 and #15 were finished before Sprint 1 closed and are counted there
([Sprint 1 backlog](../Sprint1/Sprint1Backlog.md#finished-after-the-review)).

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| ~~Linz AG morning poll~~ (done in Sprint 1) | Laurenz | Full 06:00–08:30 run on 2026-11-06; results posted in #3; #3 and #27 closed | S | [#27](https://github.com/LPuehringerStudent/LeoWay/issues/27), [#3](https://github.com/LPuehringerStudent/LeoWay/issues/3) |
| ~~Recorder test on the server (A09)~~ (done in Sprint 1) | Laurenz | At least three days of data checked against the ~15,000 records/day estimate; restore tested with recorder data; results file updated | S | [#24](https://github.com/LPuehringerStudent/LeoWay/issues/24) |
| ~~Stale feed alert~~ (done in Sprint 1) | Ayan, Laurenz | `/api/health/feeds` deployed; Kuma monitor alerts the team when a feed is stale | S | [#50](https://github.com/LPuehringerStudent/LeoWay/issues/50) |
| ~~Server power schedule vs. S06~~ (done in Sprint 1) | Laurenz | Power-on moved to before 05:00, or S06 changed in the specification | S | [#51](https://github.com/LPuehringerStudent/LeoWay/issues/51) |
| Recorder: one trip id per departure | open | A departure losing and regaining `AVMSTripID` keeps one trip id (recorded feed test); 0 split departures in new production data | S | [#69](https://github.com/LPuehringerStudent/LeoWay/issues/69) |
| Deleted accounts stay deleted after restore | – | After restoring an older backup, deleted accounts are gone again (A13) | S | [#55](https://github.com/LPuehringerStudent/LeoWay/issues/55) |
| ~~Android alarm spike: phone test~~ (done in Sprint 1) | Behrang | Spike app tested on a real Android 10+ phone: permission denied, Doze, reboot, push moves the alarm; results in #15 | S | [#15](https://github.com/LPuehringerStudent/LeoWay/issues/15) |

---

## Epic 2: Leave-by time (F02)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Spike: journey planning with Linz AG | Ayan | Clear yes/no whether Linz AG trips can be used for leave-by and alternatives; if no, fallback written down | S | [#56](https://github.com/LPuehringerStudent/LeoWay/issues/56) |
| Stop search and saved route API | Ayan | Students create, change and delete their route; another student gets 404; no address or coordinates stored | S | [#57](https://github.com/LPuehringerStudent/LeoWay/issues/57) |
| Leave-by calculation | Laurenz | Latest journey that arrives in time (spec 5.4); live delay when available, otherwise "timetable only"; tests for cancelled first lesson, nothing fits and daylight-saving changes (A02) | M | [#58](https://github.com/LPuehringerStudent/LeoWay/issues/58) |
| Background timetable sync | Laurenz | First lesson synced evening, morning and noon; a lesson cancelled overnight moves the stored first lesson; no keys or school data in logs | M | [#59](https://github.com/LPuehringerStudent/LeoWay/issues/59) |
| Route editor | David | Route can be saved and edited in Route and alerts with keyboard only at 360 px; Lighthouse accessibility ≥ 90 | M | [#60](https://github.com/LPuehringerStudent/LeoWay/issues/60) |
| Home with real data | Laurenz | Home shows the real first lesson (done in Sprint 1, #72), leave-by time and bus delay on production; every time labelled (U03); fits 360 px (U01) | M | [#61](https://github.com/LPuehringerStudent/LeoWay/issues/61) |

---

## Epic 3: Delay warnings (F03, F04)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Delay warning with alternative | Ayan | A delay that makes the student late creates one warning and an alternative connection; no warning without live data (A03) | M | [#63](https://github.com/LPuehringerStudent/LeoWay/issues/63) |
| Discord alerts | open | Test message arrives in a real Discord channel; webhook URL stored encrypted and never logged | S | [#62](https://github.com/LPuehringerStudent/LeoWay/issues/62) |
| ~~Android app with exact alarm (stretch)~~ (moved to Sprint 3) | Behrang | Alarm rings on a real phone and moves when the leave-by time changes (A04) | M | [#64](https://github.com/LPuehringerStudent/LeoWay/issues/64) |

---

## Epic 4: Operations

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Admin feed status page | David | An admin sees `linz-ag` OK on production; students see "not allowed" | S | [#65](https://github.com/LPuehringerStudent/LeoWay/issues/65) |
| ~~Discord notifications for GitHub~~ (moved to Sprint 3) | open | New issues and pull requests posted to the team Discord | S | [#26](https://github.com/LPuehringerStudent/LeoWay/issues/26) |
| VAO access | Laurenz | Key received, or "Linz AG only" written into the specification | S | [#13](https://github.com/LPuehringerStudent/LeoWay/issues/13) |

---

## Order of work

```text
#56 Linz AG trip spike ─┬─> #57 Route API ──> #60 Route editor
                        ├─> #58 Leave-by ─┬─> #61 Home with real data
#59 Timetable sync ─────┘                 ├─> #63 Delay warning (+ #62 Discord)
                                          └─> #64 Android alarm (+ #15)
```

#56 comes first: if Linz AG cannot plan journeys, #58 and #63 use the fallback (departure monitor plus a fixed travel
time per route).

---

## Summary

Status at the end of Sprint 2 (2026-11-26). "Done Done" = the whole Definition of Done is met, checked on production.

| Epic | Tasks | Done Done | Done, not yet checked on production | Moved to Sprint 3 / open | Focus Area |
|------|-------|-----------|-------------------------------------|--------------------------|------------|
| Sprint 1 carry-over | 7 (5 done in Sprint 1) | 1 (#69) | 1 (#55) | – | A09, S06, A13, Android spike, #69 |
| Leave-by time | 6 | 6 | – | – | F02, A02, U01, U03 |
| Delay warnings | 3 | 1 (#62) | 1 (#63) | 1 (#64) | F03, F04, A03, A04 |
| Operations | 3 | 2 (#65, #13) | – | 1 (#26) | Admin, team notifications, VAO |
| **TOTAL** | **19 tasks** | **10 + 5 in Sprint 1** | **2** | **2** | |

Unplanned work added during the sprint, all on the board:

| Item | Status | Issue / PR |
|------|--------|------------|
| Deploy `main` automatically (pull-based, backup first, rollback) | Done Done | [#83](https://github.com/LPuehringerStudent/LeoWay/issues/83) |
| Follow-ups from the reviews of #78 and #79 | Done Done | [#81](https://github.com/LPuehringerStudent/LeoWay/issues/81) |
| Spike: free data instead of VAO (MOTIS, MVO GTFS, ÖBB train runs) | Done Done | [#93](https://github.com/LPuehringerStudent/LeoWay/issues/93) |
| MOTIS on the server with a nightly GTFS import | Done Done, nightly runs since 2026-11-24 | [#104](https://github.com/LPuehringerStudent/LeoWay/issues/104) |
| nginx security headers on every response | Done Done | [#107](https://github.com/LPuehringerStudent/LeoWay/issues/107) |
| Linz AG live data licence | Done Done (accepted risk, spec 5.2) | [#88](https://github.com/LPuehringerStudent/LeoWay/issues/88) |
| Recorder: two vehicles share one fallback id | Done, not yet seen on production | [#77](https://github.com/LPuehringerStudent/LeoWay/issues/77) |
| Recorder: extra trips share fallback key 65534 | Closed, not planned (extra trips get their own keys) | [#91](https://github.com/LPuehringerStudent/LeoWay/issues/91) |
| VAO contract (counter, notice) | Blocked: VAO is no longer used after #93; cancellation pending | [#94](https://github.com/LPuehringerStudent/LeoWay/issues/94) |

31 pull requests merged after the Sprint 1 review (2026-11-13 to 2026-11-26), every one approved by a teammate other than the author, with green CI. Nine PRs merged between 2026-11-05 and 2026-11-12 still used the old single-owner bypass; they belong to the Sprint 1 follow-up.

| Who | Tasks |
|-----|-------|
| Laurenz | #58, #59, #61, #13 (#27/#3, #24, #50, #51 done in Sprint 1) |
| Ayan | #56, #57, #63 (#50 done in Sprint 1) |
| David | #60, #65 |
| Behrang | #64 moved to Sprint 3 (#15 done in Sprint 1) |
| open | #55, #62, #69 (#26 moved to Sprint 3) |

---

## Definition of Done (Sprint 2)

- [x] **Code merged** to `main` through a pull request approved by a team member other than the author, with green CI
  (backend, frontend, authors); no admin bypass
- [ ] **Time tracked:** hours logged in the teachers' time-tracking Excel (copy in `docs/time-tracking/Sprint2.xlsx`)
- [x] **Accepted** by the product owner, then moved to **Done Done** on the board (everything on `main` counts as accepted)
- [x] **Tests:** new logic has tests; time calculations include daylight-saving cases (CI on `main`: 205 backend, 70 frontend tests)
- [x] **API docs:** `docs/api/openapi.yaml` regenerated for new endpoints (checked in CI)
- [x] **Database:** changes only through new Flyway migrations (V5–V9)
- [x] **Privacy and security:** ownership checks on every private endpoint (S02); no keys, webhooks or school data in logs
  (S01; no webhook URL in the production log, checked 2026-11-19)
- [x] **Frontend:** 360 px without horizontal scrolling, keyboard only, Lighthouse accessibility ≥ 90
  ([route editor](../../test-results/issue-60-route-editor.md), [admin feeds](../../test-results/issue-65-admin-feeds.md))
- [x] **Deployed and checked on production:** https://leoway.lpuehringer.dev, auto-deploy from `main`
- [ ] **Critical user flows verified end-to-end:**
  - [x] Save route → Home shows the real first lesson and leave-by time (2026-11-23)
  - [ ] First lesson cancelled in WebUntis → leave-by time moves at the next sync: covered by tests (A02), no real
    cancellation on production yet
  - [ ] Bus delayed enough to arrive late → warning on Home and in Discord, with an alternative: covered by tests (A03);
    no delay large enough on a production route yet (0 warnings by 2026-11-26). Discord delivery itself is checked
    (test message, 2026-11-23)
  - [x] No live data for the line → "timetable only" (Home, 2026-11-23); "no warning" covered by tests

---

## Key Deliverables

1. **Leave-by time:** students save a route and see when to leave for their first real lesson.
2. **Home with real data:** leave-by time and live delay join the real first lesson (mock removed in Sprint 1, #72).
3. **Delay warnings:** a warning with an alternative connection when the bus makes the student late, sent to Discord.
4. **Recorder fixed:** one trip id per departure (#69). A09 check, restore test and stale alert were done in Sprint 1.

---

## Carried over to Sprint 3

| Item | Why | Status |
|------|-----|--------|
| [#64](https://github.com/LPuehringerStudent/LeoWay/issues/64) Android app with exact alarm | Stretch goal, not started. | Sprint 3 |
| [#26](https://github.com/LPuehringerStudent/LeoWay/issues/26) Discord notifications for GitHub | Low priority; needs a team webhook. | Sprint 3 |
| [#63](https://github.com/LPuehringerStudent/LeoWay/issues/63) Delay warning | Merged and deployed; waiting for a real late bus on production. | Done |
| [#77](https://github.com/LPuehringerStudent/LeoWay/issues/77) Recorder fallback ids | Merged and deployed; the case has not occurred on production since. | Done |
| [#55](https://github.com/LPuehringerStudent/LeoWay/issues/55) Deletion after restore | Passed with the real `backup.sh`/`restore.sh` on a local copy (2026-11-23); run on CT 104 still open. | Done |
| [#94](https://github.com/LPuehringerStudent/LeoWay/issues/94) VAO contract | Cancel the Start contract in writing, then remove VAO from the specification. | Blocked |

## Decisions in Sprint 2

- Trip planning moves from VAO to MOTIS with the free MVO GTFS timetables (#93); MOTIS runs on CT 104 and imports
  every night (#104). VAO is no longer needed and its Start contract is to be cancelled (#94).
- Live delays still come from the Linz AG departure board. Storing them long term is an accepted risk; if Linz AG
  objects, the recorder is switched off and `delay_record` deleted (#88, spec 5.2).
- Admins keep the student role and can use their own route and timetable (#103).
- `main` is deployed automatically by a pull-based timer on the server, with a backup before and a rollback if
  unhealthy; no quiet-window deploys on school mornings (#83).
- Referrer policy `strict-origin-when-cross-origin`, so OpenStreetMap tiles get the Referer their policy asks for (#107).

## Sprint review feedback

To be filled in after the review.
