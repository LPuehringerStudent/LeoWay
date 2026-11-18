#!/usr/bin/env bash
# Imports the GTFS feeds into MOTIS and starts it on port 8080 (#93).
# Needs data/upper-austria.osm.pbf (see README) and the feeds from fetch.sh.
set -euo pipefail
cd "$(dirname "$0")/data"
MOTIS_VERSION="${MOTIS_VERSION:-v2.11.4}"
if [[ ! -x motis ]]; then
  curl -fsSL "https://github.com/motis-project/motis/releases/download/$MOTIS_VERSION/motis-linux-amd64.tar.bz2" | tar xj
fi
cat > config.yml <<YAML
server:
  port: 8080
  web_folder: ui
osm: upper-austria.osm.pbf
timetable:
  first_day: TODAY
  num_days: 30
  datasets:
    rail:
      path: rail.gtfs.zip
    linzag:
      path: linz-ag.gtfs.zip
    ooe:
      path: upper-austria.gtfs.zip
street_routing: true
geocoding: true
YAML
# Import time and peak memory (RSS sampled every second; /usr/bin/time is not installed everywhere).
start=$(date +%s)
./motis import > import.log 2>&1 &
pid=$! peak=0
while kill -0 "$pid" 2>/dev/null; do
  rss=$(ps -o rss= -p "$pid" 2>/dev/null || echo 0)
  (( ${rss:-0} > peak )) && peak=$rss
  sleep 1
done
wait "$pid" || { tail -20 import.log; exit 1; }
echo "import: $(( $(date +%s) - start )) s, peak $(( peak / 1024 )) MB, data $(du -sh data | cut -f1)"
exec ./motis server
