# Sprint 1 · A09 delay recorder (#24)

Acceptance test A09 (spec section 6): *records keep source and time; a disconnected feed is marked stale and other
feeds keep working.* Date: 2026-11-05.

## Setup

| | |
| --- | --- |
| Server | R740, CT 104 (`leoway`), Docker compose in `/opt/leoway/deploy`, public at https://leoway.lpuehringer.dev |
| Version | `main` at `21f968d` (#45), deployed 2026-11-05 08:20 Vienna time |
| Feed | Linz AG EFA departure monitor (`linz-ag`), stops Limesstraße `60501780` and Meixnerkreuzung `60500120`, polled every 30 s |
| Storage rule | One row per departure **change** (new departure, new delay or new trip status), never per poll; rows are never updated (S04) |
| Report | [`a09-report.sql`](a09-report.sql), re-runnable on the server |

## Deviation from the plan

#24 planned **three days** of recording before the review. The recorder went live on the morning of the review day,
so the numbers below cover **hours, not days**, and the daily volume estimate from spec section 4 (~15,000 records per
day) cannot be confirmed yet. The recorder keeps running; the check is repeated in Sprint 2.

## Standard case: records keep source and time

Production, CT 104, 08:21–08:37 Vienna time (`a09-report.sql`). Refresh before the review.

| Check | Result |
| --- | --- |
| Records | 132 rows for 95 departures (one row per change) |
| With a live delay value | 107 of 132; the rest are departures without real-time data (`NULL`) |
| Every row has `source` and `collected_at` | **yes**, 0 missing |
| Duplicate observation keys (source, trip, stop, bay, service date, collection time) | **0** |
| `EXTRA_TRIP` rows | 0 in this window |
| Feed state | `linz-ag` **OK** |

| Line | Records | Trips | Avg. delay (min) | Max. delay (min) |
| --- | ---: | ---: | ---: | ---: |
| 3 | 17 | 13 | 0.6 | 4 |
| 4 | 15 | 13 | 0.1 | 1 |
| 19 | 54 | 24 | 2.1 | 6 |
| 191 | 8 | 5 | 0.6 | 2 |
| 194 | 38 | 20 | 0.3 | 2 |

Stops: Meixnerkreuzung 77 rows, Limesstraße 55 rows.

Line 11 did not appear in this window. The spike (#3) also never saw it within 15 minutes of departure.

## Non-standard case: disconnected feed is marked stale

Run on a **local copy** of the production stack (same images and `deploy/docker-compose.yml`, real Linz AG feed).
Breaking the feed on the live server was not done: it would have stopped the real recording before the review.
The feed URL was switched to a wrong path (`LEOWAY_FEEDS_LINZ_AG_BASE_URL`) and back with a compose override.
Times are Vienna time.

| Time | Step | Feed state | Records |
| --- | --- | --- | ---: |
| 08:29:38 | Last successful poll | OK | 83 |
| 08:29:56 | Feed URL broken, backend restarted | OK | 83 |
| 08:30:25 | First failed poll: `IOException: Linz AG returned HTTP 302` | OK | 83 |
| 08:31:26, 08:33:27 | Further failures, wait between retries grows (backoff) | OK | 83 |
| **08:34:57** | Five minutes after the last success | **STALE** | 83 |
| 08:37:09 | Feed URL restored, backend restarted | STALE | 83 |
| 08:38:08 | First successful poll | **OK** | 100 |

Results:
- The failure is recorded with time and reason in `feed_status`; the backend keeps running (Q03).
- The feed turns **STALE exactly five minutes** after the last success (E03).
- After a restart the last success is read back from the database, so the five minutes are not reset.
- After the fix the feed is OK again within a minute, and recording continues.
- "Other feeds keep working": only one feed exists so far. `FeedPollerTest` covers it with a hanging and a
  healthy test feed (#19).
- **No team alert** was sent, because none exists yet (#50).

## Findings

- **The server is switched off at night.** On 2026-11-04 and 2026-11-05 the R740 powered off cleanly at about
  23:56 and came back at about 05:33. S06 asks for 99 % uptime **from 05:00** on school days, so 05:00–05:33 is lost
  every day. To decide (#51): move the power-on time to 04:45, or change S06 to "from 05:45".
- **Backups survive the power schedule.** The backup timer is persistent: the 02:33 run was missed while the server
  was off and ran at 05:39 after boot (`backup ok`, 2026-11-05).
- **Timers created with `systemd-run` don't survive the shutdown.** The Linz AG morning poll (#27) was lost on
  2026-11-05 for that reason; it is now a real timer unit for 2026-11-06.

## Not done in Sprint 1

- Three days of data and the volume check against spec section 4.
- Team alert when the feed goes stale: no alert channel exists yet. Uptime Kuma checks the API's readiness, not
  the feed state (#50).
- The night-time power schedule versus S06 (#51).
- Backup restore test (S06): the restore script exists and was tested when the backups were set up (#25, #29), but
  not repeated with recorder data.
