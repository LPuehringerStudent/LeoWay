# Deployment

LeoWay runs on an **interim host** until the school decides on LeoCloud (#8).
Everything needed to run it is in this folder; nothing is installed by hand except Docker.

## Interim host: R740, container `leoway`

| | |
| --- | --- |
| Host | Proxmox on Laurenz's Dell R740, LXC **CT 104 `leoway`** (Debian 13, unprivileged, Docker) |
| Resources | 4 cores, 8 GB RAM, 40 GB disk (shared NVMe thin pool) |
| Address | `192.168.178.11` on the home LAN, reached from outside through Tailscale via the Proxmox host |
| Responsible | @LPuehringerStudent |
| App folder | `/opt/leoway/deploy` (this folder) with `.env` (secrets, `chmod 600`, never committed) |
| Backups | `/var/backups/leoway`, daily at 02:30 (`leoway-backup.timer`), kept 7 days |
| Public URL | **https://leoway.lpuehringer.dev** via Cloudflare Tunnel `r740` (cloudflared on the Proxmox host), no Cloudflare Access: open to everyone |
| Monitoring | Uptime Kuma on CT 103 (`192.168.178.30:3001`) |

Availability depends on home power and internet. Outages show up as gaps in the recorded data and are not hidden.

## First setup on a new host

```sh
apt-get install -y docker.io docker-compose age rsync git curl   # curl: backup heartbeat
mkdir -p /opt/leoway && cd /opt/leoway
git clone https://github.com/LPuehringerStudent/LeoWay.git repo && cp -r repo/deploy . && cd deploy
cp .env.example .env && chmod 600 .env   # fill in passwords and keys (see comments in the file)
docker compose up -d --wait
cp systemd/leoway-backup.* /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now leoway-backup.timer
```

Secrets:
- `POSTGRES_PASSWORD`: `openssl rand -hex 24`
- `LEOWAY_KEY_ENCRYPTION_KEY`: `openssl rand -base64 32`. Encrypts the WebUntis access keys. It lives only in `.env`, never in the database or the repo. If it's lost, every student must reconnect WebUntis.
- `LEOWAY_SESSION_KEY`: `openssl rand -base64 32`. Encrypts login cookies; if it changes, everyone has to log in again. No need to back it up.
- `LEOWAY_ADMINS`: WebUntis usernames with the admin role (feed status only), comma-separated.
- `BACKUP_AGE_RECIPIENT`: the **public** key from `age-keygen`. Keep the private key **off the server** (password manager); without it backups can't be read.

## Deploying a new version

The images are built on a developer machine and copied over SSH, so the server needs no GitHub access:

```sh
deploy/ship.sh                       # builds leoway-backend and leoway-web, tags them with the commit
ssh leoway 'cd /opt/leoway/deploy && docker compose up -d --wait'
```

Changes to files in `deploy/` itself (compose, scripts) are copied with
`rsync -a --exclude .env deploy/ leoway:/opt/leoway/deploy/`.

Flyway applies new migrations when the backend starts. Roll back by setting `LEOWAY_VERSION=<older commit>` in
`.env` and running `docker compose up -d` (only if no migration ran in between; otherwise restore a backup).

| Service | What | Reachable |
| --- | --- | --- |
| `db` | PostgreSQL 17 | `127.0.0.1:5432` in the container only |
| `backend` | Quarkus API, migrations at start | compose network only |
| `web` | Angular app in nginx, proxies `/api` and `/health/live` | `http://192.168.178.11:8080` on the LAN |

Uptime Kuma checks `http://192.168.178.11:8080/api/health/ready` (database included).

A second Kuma HTTP monitor on `http://192.168.178.11:8080/api/health/feeds` alerts the team when a feed stops working
(F09). It answers 503 when a feed is `STALE` or `DOWN`, or has no status yet. Give the monitor at least one retry, because
a feed has no status for a moment on a brand-new database. `/api/admin/feeds` has the error details.

## Backup and restore

```sh
./backup.sh                                    # manual backup
./restore.sh /var/backups/leoway/<file>.age <private-key-file>   # overwrites the database
```

Copy the private key to the server only for the restore (e.g. to `/dev/shm`) and delete it afterwards (`shred -u`).

`restore.sh` first checks that the file decrypts and decompresses completely, stops the backend, and restores into a
fresh scratch database in one transaction. Only if that works does it swap the databases. The old database stays
as `leoway_before_restore` until you drop it. So a broken backup never touches the live data, and tables that are
newer than the backup don't stay behind.

**Deleted accounts (A13, #55):** deleting an account writes its id (no username) to `deleted_account`. Before the
swap, `restore.sh` reads that list from the live database, deletes those students in the restored copy again
(their keys, routes and lateness entries go with them) and copies the list over. Student ids continue after the
highest id the live database used, so a deleted id is never given to a new account. If the backup is older than
`deleted_account` (before V6), the deletions are still applied, but the list is not carried over; keep
`leoway_before_restore` until the next backup has run. If the live database is lost completely, deletions made after
the newest backup cannot be recovered; repeat them by hand from the support requests.

Restore tests on 2026-11-04: normal restore, a truncated backup (rejected, live DB untouched) and a backup older
than the schema (newer table gone after the restore).

## Public access

`leoway.lpuehringer.dev` is a route of the existing Cloudflare Tunnel `r740` on the Proxmox host to
`http://192.168.178.11:8080`, plus a proxied CNAME to the tunnel. Cloudflare ends TLS and redirects HTTP to HTTPS.
There is deliberately **no** Cloudflare Access app in front: students and teachers must reach it without a login
from Cloudflare. Private data is protected by the API itself (S02).

## Secrets to keep outside the server

Keep both in the team password manager. If CT 104 is lost without them, the backups and stored WebUntis keys are lost too:
- the age **private** key for backups
- `LEOWAY_KEY_ENCRYPTION_KEY` from `.env` (not included in the backups; also needed for the LeoCloud move)

## Backup monitoring

Create a **Push** monitor in Uptime Kuma (CT 103, heartbeat interval about 25 h) and put its URL into
`BACKUP_HEARTBEAT_URL` in `.env`. Every successful backup calls it; if backups stop, Kuma alerts.

## Moving to LeoCloud

1. Set up the same compose stack (or translate it if LeoCloud needs Kubernetes) with the same `.env` values,
   especially `LEOWAY_KEY_ENCRYPTION_KEY`, otherwise stored WebUntis keys become unreadable.
2. Stop the backend on the R740, run `./backup.sh`, restore the file on LeoCloud.
3. Switch the public hostname (`LEOWAY_PUBLIC_BASE_URL`, DNS) and start the backend there.
4. Note the recorder gap in the feed status / data log.
