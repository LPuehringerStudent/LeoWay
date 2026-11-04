#!/usr/bin/env bash
# Restore a backup made by backup.sh. Run where the age private key is available.
#   ./restore.sh <backup.sql.gz.age> <age-identity-file>
#
# Safe order, so a bad backup never damages the live database:
#   1. check the file decrypts and decompresses completely (catches truncated files)
#   2. stop the backend (if running) so nothing writes during the restore
#   3. restore into a fresh scratch database in one transaction
#   4. only then swap: live DB -> <db>_before_restore, scratch DB -> live
# The old database is kept as <db>_before_restore. The script refuses to run while that copy
# still exists, so a second restore can't silently delete the original data.
# The backend is started again at the end, also when the restore fails.
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
file=${1:?backup file}; identity=${2:?age identity (private key) file}
user=${POSTGRES_USER:-leoway}; db=${POSTGRES_DB:-leoway}
scratch="${db}_restore"; old="${db}_before_restore"
psql_admin() { docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "$user" -d postgres "$@"; }

if [ "$(psql_admin -tAc "SELECT 1 FROM pg_database WHERE datname = '$old'")" = "1" ]; then
  echo "error: $old still exists from an earlier restore." >&2
  echo "Check it, then drop it: docker compose exec db dropdb -U $user $old" >&2
  exit 1
fi

echo "1/4 checking backup file"
age -d -i "$identity" "$file" | gunzip -t

backend_was_running=$(docker compose ps --status running --services 2>/dev/null | grep -qx backend && echo yes || echo no)
restart_backend() {
  status=$?
  if [ "$backend_was_running" = yes ]; then
    echo "starting backend again"
    docker compose start backend || echo "WARNING: backend did not start, recorder is NOT running: docker compose up -d" >&2
  fi
  [ "$status" -ne 0 ] && echo "RESTORE FAILED (exit $status). Live database unchanged." >&2
  exit "$status"
}
trap restart_backend EXIT

echo "2/4 stopping backend"
if [ "$backend_was_running" = yes ]; then docker compose stop backend; fi

echo "3/4 restoring into $scratch"
psql_admin -c "DROP DATABASE IF EXISTS \"$scratch\" WITH (FORCE);" -c "CREATE DATABASE \"$scratch\" OWNER \"$user\";"
age -d -i "$identity" "$file" | gunzip \
  | docker compose exec -T db psql -v ON_ERROR_STOP=1 -q --single-transaction -U "$user" -d "$scratch"

echo "4/4 swapping databases"
psql_admin -c "ALTER DATABASE \"$db\" RENAME TO \"$old\";" \
  -c "ALTER DATABASE \"$scratch\" RENAME TO \"$db\";"

echo "restore ok: $file"
echo "previous database kept as $old. Check the data, then: docker compose exec db dropdb -U $user $old"
