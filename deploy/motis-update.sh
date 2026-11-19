#!/usr/bin/env bash
# Nightly MOTIS data update (#104, from the #93 spike). Run by leoway-motis.timer.
#   1. MVO GTFS: rail (66), Linz AG (69), Upper Austria (77), for this timetable year and, once published, the next
#   2. OSM: Geofabrik Austria cropped to Upper Austria, refreshed when older than MOTIS_OSM_MAX_DAYS
#   3. motis import into a new folder MOTIS_DIR/data-<time>, in its own container (peak ~2.1 GB on CT 104)
#   4. point MOTIS_DIR/current at it, restart the motis container, check health and plan a test trip
#   5. if that fails, point current back at the old data and restart again; with no old data (first run), remove
#      current and stop motis instead of leaving it restarting on data that failed
# A failed download or import changes nothing: the running data stays.
#
#   ./motis-update.sh                  full update
#   ./motis-update.sh --no-download    import and switch with the files already in MOTIS_DIR/input
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a

dir=${MOTIS_DIR:-/opt/leoway/motis}
uid=${MOTIS_UID:-10093}
image=ghcr.io/motis-project/motis:${MOTIS_VERSION:-2.11.4}
osm_max_days=${MOTIS_OSM_MAX_DAYS:-7}
download=1
[ "${1:-}" = --no-download ] && download=0

exec 9>"${MOTIS_LOCK:-/run/lock/leoway-motis.lock}"
flock -n 9 || { echo "another MOTIS update is running"; exit 0; }

notify() {
  echo "$1"
  if [ -n "${AUTO_DEPLOY_NOTIFY_URL:-}" ]; then
    curl -fsS -m 10 -H "Title: LeoWay MOTIS" -d "$1" "$AUTO_DEPLOY_NOTIFY_URL" >/dev/null || echo "warning: notify failed" >&2
  fi
}
fail() {
  if [ -e "$dir/current" ]; then
    notify "MOTIS update FAILED: $1. The old data keeps running."
  else
    notify "MOTIS update FAILED: $1. MOTIS has no data and stays down."
  fi
  exit 1
}

mkdir -p "$dir/input"
input=$dir/input

mvo_token() {
  # Password via stdin (@-), so it never shows up in the process list.
  printf '%s' "$MVO_PASSWORD" | curl -fsS -m 30 \
    https://user.mobilitaetsverbuende.at/auth/realms/dbp-public/protocol/openid-connect/token \
    --data-urlencode client_id=dbp-public-ui --data-urlencode grant_type=password --data-urlencode scope=openid \
    --data-urlencode "username=$MVO_USER" --data-urlencode password@- \
    | sed -n 's/.*"access_token" *: *"\([^"]*\)".*/\1/p'
}

# Downloads dataset $1 for year $2 to $3. Returns 2 if the year isn't published yet (404), 3 if its terms aren't
# accepted on data.mobilitaetsverbuende.at (451), 1 on any other error.
mvo_get() {
  local status
  status=$(curl -sS -m 600 -H "Authorization: Bearer $token" -o "$3.part" -w '%{http_code}' \
    "https://data.mobilitaetsverbuende.at/api/public/v1/data-sets/$1/$2/file") || { rm -f "$3.part"; return 1; }
  case $status in
    200) mv "$3.part" "$3" ;;
    404) rm -f "$3.part"; return 2 ;;
    451) rm -f "$3.part"; return 3 ;;
    *) rm -f "$3.part"; echo "MVO data set $1/$2: HTTP $status" >&2; return 1 ;;
  esac
}

if [ "$download" = 1 ]; then
  : "${MVO_USER:?set MVO_USER in .env}" "${MVO_PASSWORD:?set MVO_PASSWORD in .env}"
  token=$(mvo_token) && [ -n "$token" ] || fail "no MVO token (check MVO_USER and MVO_PASSWORD)"
  year=$(TZ=Europe/Vienna date +%Y)
  # Timetable years change in mid-December. The next year's data is fetched as soon as MVO publishes it; until
  # then the import has this year's only. Old years are removed.
  # A next year that is published but whose terms aren't accepted (451) doesn't stop the update, but must not stay
  # silent: this year's data runs out about 30 days after the timetable change.
  rm -f "$input"/*.gtfs.zip.part
  terms_missing=
  for feed in 66:rail 69:linzag 77:ooe; do
    id=${feed%%:*} name=${feed#*:}
    rc=0; mvo_get "$id" "$year" "$input/$name-$year.gtfs.zip" || rc=$?
    if [ "$rc" = 3 ]; then fail "MVO data set $id/$year: terms not accepted (HTTP 451)"; fi
    if [ "$rc" != 0 ]; then fail "download of MVO data set $id/$year"; fi
    rc=0; mvo_get "$id" "$((year + 1))" "$input/$name-$((year + 1)).gtfs.zip" || rc=$?
    if [ "$rc" = 1 ]; then fail "download of MVO data set $id/$((year + 1))"; fi
    if [ "$rc" = 3 ]; then terms_missing="$terms_missing $id/$((year + 1))"; fi
  done
  if [ -n "$terms_missing" ]; then
    notify "MOTIS warning: MVO data sets$terms_missing are published but their terms are not accepted (HTTP 451). \
Accept them on data.mobilitaetsverbuende.at, or trips stop working after this timetable year ends."
  fi
  find "$input" -name '*.gtfs.zip' ! -name "*-$year.gtfs.zip" ! -name "*-$((year + 1)).gtfs.zip" -delete

  osm=$input/upper-austria.osm.pbf
  if [ ! -s "$osm" ] || [ -n "$(find "$osm" -mtime +"$osm_max_days")" ]; then
    command -v osmium >/dev/null || fail "osmium is missing (apt-get install osmium-tool)"
    curl -fsSL -m 1800 -o "$input/austria.osm.pbf.part" https://download.geofabrik.de/europe/austria-latest.osm.pbf \
      || fail "OSM download from Geofabrik"
    # Upper Austria and a margin (same box as in the #93 spike).
    osmium extract --overwrite -b 12.7,47.4,15.1,48.8 "$input/austria.osm.pbf.part" -o "$osm.part.pbf" \
      || fail "OSM extract"
    mv "$osm.part.pbf" "$osm"
    rm -f "$input/austria.osm.pbf.part"
  fi
fi

[ -s "$input/upper-austria.osm.pbf" ] || fail "no OSM file in $input"
ls "$input"/*.gtfs.zip >/dev/null 2>&1 || fail "no GTFS files in $input"

# Dataset names become the prefix of MOTIS stop ids (e.g. linzag-2026_at:44:41084:0:0); after the prefix come the
# Austrian global stop ids that Linz AG's EFA also accepts (#93).
{
  echo "osm: upper-austria.osm.pbf"
  echo "timetable:"
  echo "  first_day: TODAY"
  echo "  num_days: 30"
  echo "  datasets:"
  for f in "$input"/*.gtfs.zip; do
    name=$(basename "$f" .gtfs.zip)
    echo "    $name:"
    echo "      path: $name.gtfs.zip"
  done
  echo "street_routing: true"
  echo "geocoding: true"
} > "$input/config.yml"

new=data-$(date -u +%Y%m%d-%H%M%S)
chown -R "$uid:$uid" "$dir"
echo "importing into $dir/$new"
start=$(date +%s)
# Lower priority and at most 3 of the 4 cores, so the API stays responsive during the import.
docker run --rm --user "$uid:$uid" --cpus 3 --memory 4g --cpu-shares 256 \
  -v "$dir:/data" -w /data/input "$image" /motis import -c /data/input/config.yml -d "/data/$new" \
  > "$dir/import.log" 2>&1 || { tail -20 "$dir/import.log"; rm -rf "${dir:?}/$new"; fail "import (see $dir/import.log)"; }
echo "import took $(( $(date +%s) - start )) s, $(du -sh "$dir/$new" | cut -f1)"

old=$(readlink "$dir/current" 2>/dev/null || true)

switch_to() {
  ln -sfn "$1" "$dir/current.tmp" && mv -Tf "$dir/current.tmp" "$dir/current"
  docker compose up -d --force-recreate --wait --wait-timeout 300 motis
}

# Next weekday (Mon-Fri) 07:50 in Vienna, Linz Hauptbahnhof -> Leonding Limesstraße (by coordinates, so the check
# doesn't depend on stop ids). The answer must contain a public transport leg: with street routing on, MOTIS also
# returns walking-only itineraries, so "some itinerary" would pass an import whose timetables are unusable.
plan_works() {
  local day time answer
  day=$(TZ=Europe/Vienna date -d tomorrow +%F)
  while [ "$(TZ=Europe/Vienna date -d "$day" +%u)" -gt 5 ]; do day=$(date -d "$day + 1 day" +%F); done
  time=$(TZ=Europe/Vienna date -d "$day 07:50" --iso-8601=seconds)
  answer=$(docker compose exec -T motis wget -q -O - \
    "http://127.0.0.1:8080/api/v6/plan?fromPlace=48.29113,14.29114&toPlace=48.26567,14.25723&arriveBy=true&time=${time/+/%2B}") \
    || return 1
  has_transit_leg <<<"$answer"
}

# Succeeds if the plan answer on stdin has a leg whose mode is not a street mode. grep -c (not -q) reads all input,
# so no stage of the pipe exits early and trips pipefail with SIGPIPE.
has_transit_leg() {
  [ "$(grep -oE '"mode":"[A-Z_]+"' | grep -cvE '"(WALK|BIKE|RENTAL|CAR|CAR_PARKING|CAR_DROPOFF|ODM|FLEX)"')" -gt 0 ]
}

if switch_to "$new" && plan_works; then
  # Keep the data before this one for a manual rollback (ln -sfn <folder> current), remove older ones.
  find "$dir" -maxdepth 1 -name 'data-*' ! -name "$new" ! -name "${old:-none}" -exec rm -rf {} +
  notify "MOTIS updated: $new ($(cd "$input" && echo *.gtfs.zip))"
  exit 0
fi

if [ -n "$old" ] && [ -d "$dir/$old" ]; then
  switch_to "$old" || true
  rm -rf "${dir:?}/$new"
  fail "new data $new did not pass the health check or the test trip; back on $old"
fi
# First run: nothing to go back to. Don't leave motis restarting on data that failed; the next run starts clean.
rm -f "$dir/current"
docker compose stop motis || true
rm -rf "${dir:?}/$new"
fail "new data $new did not pass the health check or the test trip, and there is no older data"
