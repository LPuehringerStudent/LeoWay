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

Production, CT 104, 08:21–08:59 Vienna time (`a09-report.sql`). Version `1e8a71f` since 08:50 (adds #47 and #49).

| Check | Result |
| --- | --- |
| Records | 194 rows for 108 departures (one row per change) |
| With a live delay value | 167 of 194; the rest are departures without real-time data (`NULL`) |
| Every row has `source` and `collected_at` | **yes**, 0 missing |
| Duplicate observation keys (source, trip, stop, bay, service date, collection time) | **0** |
| `EXTRA_TRIP` rows | 0 in this window |
| Feed state | `linz-ag` **OK** |

| Line | Records | Trips | Avg. delay (min) | Max. delay (min) |
| --- | ---: | ---: | ---: | ---: |
| 3 | 21 | 17 | 0.5 | 4 |
| 4 | 22 | 16 | 0.1 | 1 |
| 19 | 77 | 26 | 2.2 | 7 |
| 191 | 16 | 5 | 1.2 | 5 |
| 194 | 58 | 20 | 0.7 | 3 |

Stops: Meixnerkreuzung 119 rows, Limesstraße 75 rows.

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

## Not done on 2026-11-05

These four points were finished in the follow-up below.

- Three days of data and the volume check against spec section 4.
- Team alert when the feed goes stale: no alert channel exists yet. Uptime Kuma checks the API's readiness, not
  the feed state (#50).
- The night-time power schedule versus S06 (#51).
- Backup restore test (S06): the restore script exists and was tested when the backups were set up (#25, #29), but
  not repeated with recorder data.

## Follow-up 2026-11-12: one week of data

Production, CT 104, 2026-11-05 08:21 – 2026-11-12 09:34 Vienna time. Same version as above (`1e8a71f`: Flyway
`V1`–`V3`, so #47 and #49 were live the whole week). Report: [`a09-report.sql`](a09-report.sql).

### Volume

| Day | Records | Departures | |
| --- | ---: | ---: | --- |
| Thu 2026-11-05 | 2,414 | 563 | from 08:21 |
| Fri 2026-11-06 | 2,573 | 677 | |
| Sat 2026-11-07 | 1,756 | 607 | |
| Sun 2026-11-08 | 907 | 361 | server restarted 18:37–18:53 |
| Mon 2026-11-09 | 2,709 | 681 | |
| Tue 2026-11-10 | 2,840 | 696 | |
| Wed 2026-11-11 | 2,534 | 677 | |
| Thu 2026-11-12 | 884 | 254 | until 09:34 |
| **Total** | **16,617** | **4,479** | 14,382 with a live delay value |

- A full school day gives **about 2,700 records for about 680 departures** at the two stops: about 340 departures per
  stop, close to the estimated 300, and **about 4 records per departure** (one per change).
- The estimate in spec section 4 counted one record per departure. With about 4 changes each it is **about 60,000
  records per day** at 50 stops, not 15,000. Measured size is 231 bytes per row including indexes, so about 5 GB per
  year instead of 1.1 GB. Spec section 4 is updated. This is still small for PostgreSQL.
- About 15 % of the departures are counted twice because of a trip id bug (see findings, #69), so the real number of
  departures is a little lower.

| Line | Records | Trips | Avg. delay (min) | Max. delay (min) |
| --- | ---: | ---: | ---: | ---: |
| 3 | 1,894 | 544 | 1.3 | 41 |
| 4 | 2,857 | 711 | 1.1 | 50 |
| 11 | 465 | 24 | 6.0 | 28 |
| 19 | 6,572 | 349 | 3.7 | 40 |
| 191 | 1,256 | 131 | 1.5 | 10 |
| 194 | 3,454 | 139 | 0.6 | 10 |
| N84 | 119 | 53 | 0.1 | 3 |

Stops: Meixnerkreuzung 11,579 rows, Limesstraße 5,038 rows. Line 11 shows up now (it was missing on 2026-11-05).
Night bus N84 is recorded too.

### Integrity

| Check | Result |
| --- | --- |
| Every row has `source` and `collected_at` | **yes**, 0 missing |
| Duplicate observation keys | **0** |
| `EXTRA_TRIP` rows | 94, stored and flagged |
| Feed state | `linz-ag` **OK** |

### Availability (S06: 99 % on school days, 05:00–10:00)

The backend logged 121 failed polls in the week: 49 `ConnectException`, 37 `HTTP 302`, 27 timeouts, 7 `HTTP 503`
and 1 connect timeout. Each failure is a gap from the last stored poll before it to the first one after it:

| School day | Outages 05:00–10:00 | Lost | Uptime | Longest |
| --- | ---: | ---: | ---: | ---: |
| Fri 2026-11-06 | 1 | 4.0 min | 98.7 % | 4.5 min |
| Mon 2026-11-09 | 2 | 2.1 min | **99.3 %** | 1.5 min |
| Tue 2026-11-10 | 4 | 4.8 min | 98.4 % | 2.7 min |
| Wed 2026-11-11 | 5 | 6.4 min | 97.9 % | 3.0 min |
| Thu 2026-11-12 | 0 | 0 | **100 %** | – |

- **The server itself was up the whole time.** Since 2026-11-08 it runs around the clock (#51). Every outage was
  the Linz AG server not answering or answering with an error, never our host.
- 99 % was missed on three of five school days, by a few minutes each. A failed poll waits 30 s and then backs off,
  so one bad answer costs about a minute. A quick retry after a single failure would bring most days over 99 %.
- The longest outage was at night: `ConnectException` from 2026-11-11 22:32 to 2026-11-12 04:08 (37 failed polls), with almost no buses running.
  The feed went STALE and back to OK by itself.

### Team alert on STALE (#50)

`/api/health/feeds` (#54) answers 503 when a feed is `STALE` or `DOWN`. It is merged but not deployed yet: on
2026-11-12 production still answered 404. The Uptime Kuma monitor is added after the deploy (`deploy/README.md`).

### Restore with recorder data (S06)

The backup from 2026-11-12 02:32 (`leoway-2026-11-12T013218Z.sql.gz.age`, 170 KB) was decrypted with the age key and
restored into a **throwaway PostgreSQL 17 container** on a developer machine. The live database was not touched.

| Check | Backup | Production |
| --- | --- | --- |
| `delay_record` rows before the backup time | 15,733 | 15,733 |
| Last record | 2026-11-11 22:29:14 | 2026-11-11 22:29:14 |
| Checksum over id, trip and collection time | `5b6018a2…` | `5b6018a2…` |
| Flyway migrations | V1, V2, V3 | V1, V2, V3 |
| `TRUNCATE delay_record` | rejected by trigger | rejected by trigger |

The restore is complete and the append-only rule (S04) is still in place afterwards. The container and the decrypted
file were deleted.

### Findings

- **One departure can get two trip ids (#69).** Linz AG leaves `AVMSTripID` out on some sightings, mostly more than
  two hours ahead and sometimes after the planned time. The adapter then uses a fallback id, so 659 departures are
  stored twice with split delay histories. The duplicate check above still passes, because the key is different.
- **Linz AG answers with HTTP 302 at times**, the same answer as a wrong URL. It cleared on the next poll.
- **38 tram departures (lines 3 and 4) have two `AVMSTripID`s** that differ only in the suffix (`-1`/`-2`) for the
  same planned time and bay. Noted in #69.

### Result

A09 is **passed**: records keep source and time, nothing is duplicated within the key, a broken feed turns STALE
after five minutes and recovers by itself, and the backup restores completely. Open: S06's 99 % depends on Linz AG,
the trip id bug (#69), and the Kuma monitor after the deploy (#50).
