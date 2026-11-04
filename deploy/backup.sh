#!/usr/bin/env bash
# Encrypted daily PostgreSQL backup (spec 3.4 availability).
# Writes $BACKUP_DIR/leoway-YYYY-MM-DDTHHMMSSZ.sql.gz.age and deletes files older than $BACKUP_KEEP_DAYS.
# Only the age public key is on the server, so a stolen backup file cannot be read.
set -euo pipefail
umask 077
cd "$(dirname "$0")"
set -a; . ./.env; set +a

: "${BACKUP_AGE_RECIPIENT:?set BACKUP_AGE_RECIPIENT in .env}"
BACKUP_DIR=${BACKUP_DIR:-/var/backups/leoway}
BACKUP_KEEP_DAYS=${BACKUP_KEEP_DAYS:-7}
mkdir -p "$BACKUP_DIR"; chmod 700 "$BACKUP_DIR"

out="$BACKUP_DIR/leoway-$(date -u +%Y-%m-%dT%H%M%SZ).sql.gz.age"
trap 'rm -f -- "$out.tmp"' EXIT   # no half-written files left behind after a failed run
docker compose exec -T db pg_dump -U "${POSTGRES_USER:-leoway}" -d "${POSTGRES_DB:-leoway}" --clean --if-exists \
  | gzip -9 | age -r "$BACKUP_AGE_RECIPIENT" > "$out.tmp"
mv "$out.tmp" "$out"

find "$BACKUP_DIR" -name 'leoway-*.sql.gz.age' -mtime +"$((BACKUP_KEEP_DAYS - 1))" -delete

# Tell Uptime Kuma the backup ran; its push monitor alerts when this stops arriving.
if [ -n "${BACKUP_HEARTBEAT_URL:-}" ]; then
  curl -fsS -m 10 --retry 3 "$BACKUP_HEARTBEAT_URL" >/dev/null || echo "warning: heartbeat failed" >&2
fi
echo "backup ok: $out ($(du -h "$out" | cut -f1))"
