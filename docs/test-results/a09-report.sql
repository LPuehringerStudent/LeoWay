-- A09 recorder report (#24). Run on the server:
--   ssh leoway 'cd /opt/leoway/deploy && docker compose exec -T db psql -U leoway -d leoway' < docs/test-results/a09-report.sql
\pset pager off
\echo '== Totals'
select count(*) records,
       count(distinct (trip_id, stop_id, bay, service_date)) departures,
       count(reported_delay) with_live_delay,
       min(collected_at) at time zone 'Europe/Vienna' first_collected,
       max(collected_at) at time zone 'Europe/Vienna' last_collected
from delay_record;
\echo '== Records per day'
select (collected_at at time zone 'Europe/Vienna')::date as service_day, count(*) records,
       count(distinct (trip_id, stop_id, bay, service_date)) departures
from delay_record group by 1 order by 1;
\echo '== Per line'
select line, count(*) records, count(distinct trip_id) trips,
       round(avg(reported_delay), 1) avg_delay_min, max(reported_delay) max_delay_min
from delay_record group by 1 order by 1;
\echo '== Per stop'
select stop_id, count(*) records from delay_record group by 1 order by 1;
\echo '== Integrity: every row has source and time; no duplicate observation keys'
select count(*) filter (where source is null or collected_at is null) missing_source_or_time,
       count(*) - count(distinct (source, trip_id, stop_id, bay, service_date, collected_at)) duplicate_keys,
       count(*) filter (where trip_status like '%EXTRA_TRIP%') extra_trips
from delay_record;
\echo '== Feed status'
select feed_id, state, last_success_at at time zone 'Europe/Vienna' last_success,
       last_error_at at time zone 'Europe/Vienna' last_error_at, left(last_error, 100) last_error
from feed_status;
