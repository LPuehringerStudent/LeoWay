#!/usr/bin/env bash
# Deploys the newest main from GHCR (#83). Run by leoway-deploy.timer every 5 minutes on the server.
#   1. pull the :main images; stop if their commit is already deployed
#   2. skip school-day mornings (AUTO_DEPLOY_QUIET, default 1-5,06:00-08:30 = Mon-Fri, Europe/Vienna)
#   3. backup.sh, so a bad migration can be undone with restore.sh; no backup, no deploy
#   4. tag the images as leoway-*:latest (the names docker-compose.yml uses) and start them
#   5. if the containers don't get healthy, put the previous images back
# Rolling back the code does not undo database migrations; Flyway ignores migrations newer than the code.
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
exec 9>"${AUTO_DEPLOY_LOCK:-/run/lock/leoway-deploy.lock}"
flock -n 9 || { echo "another deploy is running"; exit 0; }

registry=${AUTO_DEPLOY_REGISTRY:-ghcr.io/lpuehringerstudent}
quiet=${AUTO_DEPLOY_QUIET:-1-5,06:00-08:30}
state=.deployed
notify() {
  echo "$1"
  if [ -n "${AUTO_DEPLOY_NOTIFY_URL:-}" ]; then
    curl -fsS -m 10 -H "Title: LeoWay deploy" -d "$1" "$AUTO_DEPLOY_NOTIFY_URL" >/dev/null || echo "warning: notify failed" >&2
  fi
}
revision() { docker image inspect -f '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$1" 2>/dev/null || true; }

# "1-5,06:00-08:30": weekdays (1 = Monday) and a time window, both inclusive. "off" disables it.
in_quiet_window() {
  [ "$quiet" != off ] || return 1
  local days=${quiet%%,*} window=${quiet#*,} dow now
  dow=$(TZ=Europe/Vienna date +%u); now=$(TZ=Europe/Vienna date +%H:%M)
  [ "$dow" -ge "${days%-*}" ] && [ "$dow" -le "${days#*-}" ] \
    && [[ ! "$now" < "${window%-*}" ]] && [[ ! "$now" > "${window#*-}" ]]
}

docker pull -q "$registry/leoway-backend:main" >/dev/null
docker pull -q "$registry/leoway-web:main" >/dev/null
new=$(revision "$registry/leoway-backend:main")
[ -n "$new" ] || { notify "deploy stopped: image has no revision label"; exit 1; }
[ "$new" = "$(revision "$registry/leoway-web:main")" ] || { echo "backend and web images differ, waiting for both"; exit 0; }
old=$(cat "$state" 2>/dev/null || true)
[ "$new" != "$old" ] || exit 0

if in_quiet_window; then echo "new version $new waits: quiet window ($quiet)"; exit 0; fi

echo "deploying $new (was ${old:-unknown})"
./backup.sh || { notify "deploy of $new stopped: backup failed, production unchanged"; exit 1; }

# Keep what runs now, so it can be put back.
for app in backend web; do
  docker image inspect "leoway-$app:latest" >/dev/null 2>&1 && docker tag "leoway-$app:latest" "leoway-$app:previous"
  docker tag "$registry/leoway-$app:main" "leoway-$app:latest"
  docker tag "$registry/leoway-$app:main" "leoway-$app:$new"
done

if LEOWAY_VERSION=latest docker compose up -d --wait --wait-timeout 180; then
  echo "$new" > "$state"
  notify "deployed $new"
  docker image prune -f >/dev/null
  exit 0
fi

if docker image inspect leoway-backend:previous >/dev/null 2>&1; then
  for app in backend web; do docker tag "leoway-$app:previous" "leoway-$app:latest"; done
  if LEOWAY_VERSION=latest docker compose up -d --wait --wait-timeout 180; then
    # Remember the failed version, so the timer does not try it again every 5 minutes.
    echo "$new" > "$state"
    notify "deploy of $new FAILED, rolled back to ${old:-the previous images}. Fix on main, the next merge deploys again."
    exit 1
  fi
fi
notify "deploy of $new FAILED and rollback did not get healthy. LeoWay may be down: ssh leoway, cd /opt/leoway/deploy, docker compose ps"
exit 1
