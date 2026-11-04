#!/usr/bin/env bash
# Restore a backup made by backup.sh. Run where the age private key is available.
#   ./restore.sh <backup.sql.gz.age> <age-identity-file>
# Overwrites the current database contents.
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
file=${1:?backup file}; identity=${2:?age identity (private key) file}

age -d -i "$identity" "$file" | gunzip \
  | docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "${POSTGRES_USER:-leoway}" -d "${POSTGRES_DB:-leoway}"
echo "restore ok: $file"
