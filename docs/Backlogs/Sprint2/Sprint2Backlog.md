# Sprint Backlog — Sprint 2

**Sprint Duration:** 3 weeks (2026-11-05 – 2026-11-26)
**Team Size:** 4 developers (Laurenz, Ayan, David, Behrang)
**Product Owner:** Behrang (@BehrangEl11)
**Board:** [Sprint2](https://github.com/users/LPuehringerStudent/projects/15)

**Sprint Goal:** A student sees a real leave-by time on Home and gets a warning when their bus is late.

This finishes Block A of the specification (F02) and starts Block B (F03, first channel of F04). Every task has an
issue with tasks and acceptance criteria on GitHub. Sizes: S = up to a day, M = several days.

---

## Epic 1: Sprint 1 carry-over

#27/#3, #24, #50 and #51 were finished before Sprint 1 closed and are counted there
([Sprint 1 backlog](../Sprint1/Sprint1Backlog.md#finished-after-the-review)).

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| ~~Linz AG morning poll~~ (done in Sprint 1) | Laurenz | Full 06:00–08:30 run on 2026-11-06; results posted in #3; #3 and #27 closed | S | [#27](https://github.com/LPuehringerStudent/LeoWay/issues/27), [#3](https://github.com/LPuehringerStudent/LeoWay/issues/3) |
| ~~Recorder test on the server (A09)~~ (done in Sprint 1) | Laurenz | At least three days of data checked against the ~15,000 records/day estimate; restore tested with recorder data; results file updated | S | [#24](https://github.com/LPuehringerStudent/LeoWay/issues/24) |
| ~~Stale feed alert~~ (done in Sprint 1) | Ayan, Laurenz | `/api/health/feeds` deployed; Kuma monitor alerts the team when a feed is stale | S | [#50](https://github.com/LPuehringerStudent/LeoWay/issues/50) |
| ~~Server power schedule vs. S06~~ (done in Sprint 1) | Laurenz | Power-on moved to before 05:00, or S06 changed in the specification | S | [#51](https://github.com/LPuehringerStudent/LeoWay/issues/51) |
| Recorder: one trip id per departure | open | A departure losing and regaining `AVMSTripID` keeps one trip id (recorded feed test); 0 split departures in new production data | S | [#69](https://github.com/LPuehringerStudent/LeoWay/issues/69) |
| Deleted accounts stay deleted after restore | – | After restoring an older backup, deleted accounts are gone again (A13) | S | [#55](https://github.com/LPuehringerStudent/LeoWay/issues/55) |
| Android alarm spike: phone test | Behrang | Spike app tested on a real Android 10+ phone: permission denied, Doze, reboot, push moves the alarm; results in #15 | S | [#15](https://github.com/LPuehringerStudent/LeoWay/issues/15) |

---

## Epic 2: Leave-by time (F02)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Spike: journey planning with Linz AG | Ayan | Clear yes/no whether Linz AG trips can be used for leave-by and alternatives; if no, fallback written down | S | [#56](https://github.com/LPuehringerStudent/LeoWay/issues/56) |
| Stop search and saved route API | Ayan | Students create, change and delete their route; another student gets 404; no address or coordinates stored | S | [#57](https://github.com/LPuehringerStudent/LeoWay/issues/57) |
| Leave-by calculation | Laurenz | Latest journey that arrives in time (spec 5.4); live delay when available, otherwise "timetable only"; tests for cancelled first lesson, nothing fits and daylight-saving changes (A02) | M | [#58](https://github.com/LPuehringerStudent/LeoWay/issues/58) |
| Background timetable sync | Laurenz | First lesson synced evening, morning and noon; a lesson cancelled overnight moves the stored first lesson; no keys or school data in logs | M | [#59](https://github.com/LPuehringerStudent/LeoWay/issues/59) |
| Route editor | David | Route can be saved and edited in Route and alerts with keyboard only at 360 px; Lighthouse accessibility ≥ 90 | M | [#60](https://github.com/LPuehringerStudent/LeoWay/issues/60) |
| Home with real data | Laurenz | Home shows the real first lesson, leave-by time and bus delay on production; every time labelled (U03); fits 360 px (U01) | M | [#61](https://github.com/LPuehringerStudent/LeoWay/issues/61) |

---

## Epic 3: Delay warnings (F03, F04)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Delay warning with alternative | Ayan | A delay that makes the student late creates one warning and an alternative connection; no warning without live data (A03) | M | [#63](https://github.com/LPuehringerStudent/LeoWay/issues/63) |
| Discord alerts | open | Test message arrives in a real Discord channel; webhook URL stored encrypted and never logged | S | [#62](https://github.com/LPuehringerStudent/LeoWay/issues/62) |
| Android app with exact alarm (stretch) | Behrang | Alarm rings on a real phone and moves when the leave-by time changes (A04) | M | [#64](https://github.com/LPuehringerStudent/LeoWay/issues/64) |

---

## Epic 4: Operations

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Admin feed status page | David | An admin sees `linz-ag` OK on production; students see "not allowed" | S | [#65](https://github.com/LPuehringerStudent/LeoWay/issues/65) |
| Discord notifications for GitHub | open | New issues and pull requests posted to the team Discord | S | [#26](https://github.com/LPuehringerStudent/LeoWay/issues/26) |
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

| Epic | Tasks | Focus Area |
|------|-------|------------|
| Sprint 1 carry-over | 7 (4 done in Sprint 1) | A09, S06, A13, Android spike, #69 |
| Leave-by time | 6 | F02, A02, U01, U03 |
| Delay warnings | 3 | F03, F04, A03, A04 |
| Operations | 3 | Admin, team notifications, VAO |
| **TOTAL** | **19 tasks** | |

| Who | Tasks |
|-----|-------|
| Laurenz | #58, #59, #61, #13 (#27/#3, #24, #50, #51 done in Sprint 1) |
| Ayan | #56, #57, #63 (#50 done in Sprint 1) |
| David | #60, #65 |
| Behrang | #15, #64 |
| open | #55, #62, #26, #69 |

---

## Definition of Done (Sprint 2)

- [ ] **Code merged** to `main` through a reviewed pull request with green CI (backend, frontend, authors)
- [ ] **Tests:** new logic has tests; time calculations include daylight-saving cases
- [ ] **API docs:** `docs/api/openapi.yaml` regenerated for new endpoints
- [ ] **Database:** changes only through new Flyway migrations
- [ ] **Privacy and security:** ownership checks on every private endpoint (S02); no keys, webhooks or school data in logs (S01)
- [ ] **Frontend:** 360 px without horizontal scrolling, keyboard only, Lighthouse accessibility ≥ 90
- [ ] **Deployed and checked on production**
- [ ] **Critical user flows verified end-to-end:**
  - Save route → Home shows the real first lesson and leave-by time
  - First lesson cancelled in WebUntis → leave-by time moves after the next sync
  - Bus delayed enough to arrive late → warning on Home and in Discord, with an alternative
  - No live data for the line → "timetable only", no warning

---

## Key Deliverables

1. **Leave-by time:** students save a route and see when to leave for their first real lesson.
2. **Home with real data:** first lesson, leave-by time and live delay replace the mock.
3. **Delay warnings:** a warning with an alternative connection when the bus makes the student late, sent to Discord.
4. **Recorder fixed:** one trip id per departure (#69). A09 check, restore test and stale alert were done in Sprint 1.
