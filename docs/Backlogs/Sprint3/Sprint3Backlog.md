# Sprint Backlog — Sprint 3

**Sprint Duration:** 3 weeks (2026-11-26 – 2026-12-17)
**Team Size:** 4 developers (Laurenz, Ayan, David, Behrang)
**Product Owner:** Behrang (@BehrangEl11)
**Board:** [Sprint3](https://github.com/users/LPuehringerStudent/projects/16)

**Sprint Goal:** LeoWay plans with our own MOTIS, shows the way home after school, alerts by email as well as Discord,
and shows each student their lateness record next to the recorded delays.

This finishes Block B of the specification (F04 second channel, F05; F06 stays in the backlog) and prepares Block C
(delay appeals, F07): the lateness record is what an appeal will point to, and the form teachers are asked what an
appeal must contain. Every task has an issue with tasks and acceptance criteria on GitHub. Sizes: S = up to a day,
M = several days. "Who" is the proposal for the planning meeting; assignees are set there.

---

## Epic 1: Trips on our own data (#93 follow-ups)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Leave-by and stop search on MOTIS | Laurenz | Leave-by and stop search use MOTIS, Linz AG as fallback; A02 passes; no 503 for a new route on production | M | [#117](https://github.com/LPuehringerStudent/LeoWay/issues/117) |
| Credit open data sources | David | MVO, Linz AG and OpenStreetMap credited in the app without login at 360 px; L05 matches | S | [#118](https://github.com/LPuehringerStudent/LeoWay/issues/118) |

## Epic 2: Alerts (Block B: F04, F05)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Way home after the last lesson | Ayan | A05 standard and non-standard pass; card on Home at 360 px, keyboard, Lighthouse ≥ 90 | M | [#119](https://github.com/LPuehringerStudent/LeoWay/issues/119) |
| Email alerts | Laurenz | Test email arrives in a real inbox; delay warnings reach every chosen channel within 60 s (A04); address encrypted, never logged | M | [#120](https://github.com/LPuehringerStudent/LeoWay/issues/120) |
| Android app with exact alarm (stretch, from Sprint 2) | Behrang | Alarm rings on a real phone and moves when the leave-by time changes (A04) | M | [#64](https://github.com/LPuehringerStudent/LeoWay/issues/64) |

## Epic 3: Towards delay appeals (Block C)

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| My lateness, matched with delay records | David (page), Ayan (matching) | Lateness entries of the last 30 days with the recorded delay of that morning's connection on production; another student's entry → 404 | M | [#121](https://github.com/LPuehringerStudent/LeoWay/issues/121) |
| Ask form teachers what an appeal must show | Behrang | Answers from at least one form teacher in #10: email content, verification link, teacher email source, portal yes/no | S | [#10](https://github.com/LPuehringerStudent/LeoWay/issues/10) |

## Epic 4: Operations

| Task | Who | Definition of Done | Size | Issue |
|------|-----|--------------------|------|-------|
| Cancel the VAO Start contract | Laurenz | Written notice sent; VAO removed from spec and README after it takes effect | S | [#94](https://github.com/LPuehringerStudent/LeoWay/issues/94) |
| Discord notifications for GitHub (from Sprint 2) | open | New issues and pull requests posted to the team Discord | S | [#26](https://github.com/LPuehringerStudent/LeoWay/issues/26) |

Checks left from Sprint 2, done when the case comes up (no new issues):

- Delay warning (#63) seen on production: a route with buffer 0 on a school morning, or the first real late bus
- Restore test on CT 104 (#55, A13), as run locally on 2026-11-23

---

## Order of work

```text
#117 MOTIS ──┬─> #118 credits (ship together)
             └─> #119 way home (works on Linz AG too, switches with #117)
#121 my lateness ──> F07 appeals in Sprint 4 (needs #10 answers)
#120 email alerts      #64 Android alarm      #26, #94 (independent)
```

#10 starts in week 1: the answers decide the appeal format for Sprint 4.

---

## Summary

| Epic | Tasks | Focus Area |
|------|-------|------------|
| Trips on our own data | 2 | #93 follow-ups, L05 |
| Alerts | 3 | F04, F05, A04, A05 |
| Towards delay appeals | 2 | F07 preparation, F12 data |
| Operations | 2 | VAO, team notifications |
| **TOTAL** | **9 tasks** | |

| Who | Tasks |
|-----|-------|
| Laurenz | #117, #120, #94 |
| Ayan | #119, #121 (matching) |
| David | #118, #121 (page) |
| Behrang | #64, #10 |
| open | #26 |

Not in this sprint (product backlog): F06 alert rules (PB-41), ÖBB train delays (#93 follow-up 4, base for F11),
A14 release checks (PB-48, planned for the sprint before release).

---

## Definition of Done (Sprint 3)

Same as Sprint 2:

- [ ] **Code merged** to `main` through a pull request approved by a team member other than the author, with green CI
  (backend, frontend, authors); no admin bypass
- [ ] **Time tracked:** hours logged in the teachers' time-tracking Excel
- [ ] **Accepted** by the product owner, then moved to **Done Done** on the board
- [ ] **Tests:** new logic has tests; time calculations include daylight-saving cases
- [ ] **API docs:** `docs/api/openapi.yaml` regenerated for new endpoints
- [ ] **Database:** changes only through new Flyway migrations
- [ ] **Privacy and security:** ownership checks on every private endpoint (S02); no keys, addresses or school data in logs (S01)
- [ ] **Frontend:** 360 px without horizontal scrolling, keyboard only, Lighthouse accessibility ≥ 90
- [ ] **Deployed and checked on production**
- [ ] **Critical user flows verified end-to-end:**
  - Save route → leave-by time from MOTIS on Home, same as before the switch
  - Last lesson ends → way home on Home; lessons end early → next connection after the new end
  - Delay warning → Discord and email, one message each
  - Lateness entry in WebUntis → appears on My lateness with the recorded delay

---

## Key Deliverables

1. **Own trip planning:** leave-by and stop search on MOTIS with the free MVO timetables, data sources credited.
2. **Way home:** connections home after the last lesson (F05).
3. **Email alerts:** a second alert channel next to Discord (F04).
4. **My lateness:** the student's lateness record next to the recorded delays, the base for appeals (F07).
