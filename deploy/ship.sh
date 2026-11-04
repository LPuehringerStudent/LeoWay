#!/usr/bin/env bash
# Build the backend and web images on this machine and load them onto the server.
# The repo and images are private, so the server never needs GitHub credentials.
#   deploy/ship.sh [ssh-host]      (default: leoway)
# Then on the server: cd /opt/leoway/deploy && docker compose up -d --wait
set -euo pipefail
cd "$(dirname "$0")/.."
host=${1:-leoway}
tag=$(git rev-parse --short HEAD)
[ -z "$(git status --porcelain -- backend frontend)" ] || echo "warning: backend/ or frontend/ has uncommitted changes" >&2

docker build -f backend/Dockerfile -t "leoway-backend:$tag" -t leoway-backend:latest .
docker build -t "leoway-web:$tag" -t leoway-web:latest frontend
docker save "leoway-backend:$tag" leoway-backend:latest "leoway-web:$tag" leoway-web:latest \
  | gzip | ssh "$host" 'gunzip | docker load'
echo "loaded $tag on $host. Next: ssh $host 'cd /opt/leoway/deploy && docker compose up -d --wait'"
