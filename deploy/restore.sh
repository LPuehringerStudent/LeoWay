#!/usr/bin/env bash
# Restore a backup made by backup.sh. Run where the age private key is available.
#   ./restore.sh <backup.sql.gz.age> <age-identity-file>
#
# Safe order, so a bad backup never damages the live database:
#   1. check the file decrypts and decompresses completely (catches truncated files)
#   2. stop the backend (if running) so nothing writes during the restore
#   3. restore into a fresh scratch database in one transaction
#   4. only then swap: live DB -> <db>_before_restore, scratch DB -> live
# The old database is kept as <db>_before_restore; drop it once the restore is checked.
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
file=${1:?backup file}; identity=${2:?age identity (private key) file}
user=${POSTGRES_USER:-leoway}; db=${POSTGRES_DB:-leoway}
scratch="${db}_restore"; old="${db}_before_restore"
psql_admin() { docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "$user" -d postgres "$@"; }

echo "1/4 checking backup file"
age -d -i "$identity" "$file" | gunzip -t

echo "2/4 stopping backend"
docker compose stop backend 2>/dev/null || true

echo "3/4 restoring into $scratch"
psql_admin -c "DROP DATABASE IF EXISTS \"$scratch\" WITH (FORCE);" -c "CREATE DATABASE \"$scratch\" OWNER \"$user\";"
age -d -i "$identity" "$file" | gunzip \
  | docker compose exec -T db psql -v ON_ERROR_STOP=1 -q --single-transaction -U "$user" -d "$scratch"

echo "4/4 swapping databases"
psql_admin -c "DROP DATABASE IF EXISTS \"$old\" WITH (FORCE);" \
  -c "ALTER DATABASE \"$db\" RENAME TO \"$old\";" \
  -c "ALTER DATABASE \"$scratch\" RENAME TO \"$db\";"

echo "restore ok: $file"
echo "previous database kept as $old. Check the data, then: docker compose exec db dropdb -U $user $old"
echo "start the backend again: docker compose up -d"
