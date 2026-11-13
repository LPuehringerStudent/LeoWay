#!/usr/bin/env bash
# Restore a backup made by backup.sh. Run where the age private key is available.
#   ./restore.sh <backup.sql.gz.age> <age-identity-file>
#
# Safe order, so a bad backup never damages the live database:
#   1. check the file decrypts and decompresses completely (catches truncated files)
#   2. stop the backend (if running) so nothing writes during the restore
#   3. restore into a fresh scratch database in one transaction
#   4. delete again the accounts deleted in the live DB (deleted_account, #55, A13), so an older backup
#      does not bring them back; student ids continue after the live DB's, so no deleted id is reused
#   5. only then swap: live DB -> <db>_before_restore, scratch DB -> live
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
psql_on() { local d=$1; shift; docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "$user" -d "$d" "$@"; }
has_table() { [ "$(psql_on "$1" -tAc "SELECT to_regclass('public.$2') IS NOT NULL")" = t ]; }

if [ "$(psql_admin -tAc "SELECT 1 FROM pg_database WHERE datname = '$old'")" = "1" ]; then
  echo "error: $old still exists from an earlier restore." >&2
  echo "Check it, then drop it: docker compose exec db dropdb -U $user $old" >&2
  exit 1
fi

echo "1/5 checking backup file"
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

echo "2/5 stopping backend"
if [ "$backend_was_running" = yes ]; then docker compose stop backend; fi

echo "3/5 restoring into $scratch"
psql_admin -c "DROP DATABASE IF EXISTS \"$scratch\" WITH (FORCE);" -c "CREATE DATABASE \"$scratch\" OWNER \"$user\";"
age -d -i "$identity" "$file" | gunzip \
  | docker compose exec -T db psql -v ON_ERROR_STOP=1 -q --single-transaction -U "$user" -d "$scratch"

echo "4/5 deleting accounts that were deleted after the backup"
if has_table "$db" deleted_account; then
  deleted=$(psql_on "$db" -tAc "SELECT string_agg(student_id::text, ',') FROM deleted_account")
  last_id=$(psql_on "$db" -tAc "SELECT coalesce(max(id), 0) FROM student")
  last_deleted=$(psql_on "$db" -tAc "SELECT coalesce(max(student_id), 0) FROM deleted_account")
  [ "$last_deleted" -gt "$last_id" ] && last_id=$last_deleted
  # One transaction: foreign keys cascade to keys, routes and lateness entries (L02).
  {
    echo "BEGIN;"
    if [ -n "$deleted" ]; then
      echo "DELETE FROM student WHERE id IN ($deleted);"
      if has_table "$scratch" deleted_account; then
        echo "INSERT INTO deleted_account (student_id) SELECT unnest(ARRAY[$deleted]::bigint[]) ON CONFLICT DO NOTHING;"
      fi
    fi
    echo "SELECT setval(pg_get_serial_sequence('student', 'id'), greatest($last_id, (SELECT coalesce(max(id), 0) FROM student), 1));"
    echo "COMMIT;"
  } | psql_on "$scratch" >/dev/null
  echo "re-applied $( [ -n "$deleted" ] && echo "$deleted" | tr ',' '\n' | wc -l || echo 0) account deletion(s)"
  if [ -n "$deleted" ] && ! has_table "$scratch" deleted_account; then
    echo "WARNING: the backup predates deleted_account; the list is not carried over. Keep $old until the next backup." >&2
  fi
else
  echo "live database has no deleted_account table, nothing to re-apply"
fi

echo "5/5 swapping databases"
psql_admin -c "ALTER DATABASE \"$db\" RENAME TO \"$old\";" \
  -c "ALTER DATABASE \"$scratch\" RENAME TO \"$db\";"

echo "restore ok: $file"
echo "previous database kept as $old. Check the data, then: docker compose exec db dropdb -U $user $old"
