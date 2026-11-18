#!/usr/bin/env bash
# Downloads the MVO GTFS feeds for the #93 spike: rail (66), Linz AG (69), Upper Austria incl. flex (77).
# Needs a free account on data.mobilitaetsverbuende.at: MVO_USER and MVO_PASSWORD in the environment.
set -euo pipefail
: "${MVO_USER:?set MVO_USER}" "${MVO_PASSWORD:?set MVO_PASSWORD}"
YEAR="${YEAR:-2026}"
cd "$(dirname "$0")"
mkdir -p data

token=$(curl -fsS https://user.mobilitaetsverbuende.at/auth/realms/dbp-public/protocol/openid-connect/token \
  --data-urlencode client_id=dbp-public-ui --data-urlencode grant_type=password --data-urlencode scope=openid \
  --data-urlencode "username=$MVO_USER" --data-urlencode "password=$MVO_PASSWORD" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')

for feed in 66:rail 69:linz-ag 77:upper-austria; do
  id=${feed%%:*} name=${feed#*:}
  echo "downloading $name ($id/$YEAR)"
  curl -fsS -H "Authorization: Bearer $token" -o "data/$name.gtfs.zip" \
    "https://data.mobilitaetsverbuende.at/api/public/v1/data-sets/$id/$YEAR/file"
done
ls -la data/*.gtfs.zip
