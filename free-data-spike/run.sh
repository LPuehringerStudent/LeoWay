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
/usr/bin/time -v ./motis import 2> import-time.txt
grep -E "Elapsed|Maximum resident" import-time.txt
exec ./motis server
