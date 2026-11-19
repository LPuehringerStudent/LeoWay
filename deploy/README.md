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
apt-get install -y docker.io docker-compose age rsync git curl osmium-tool   # curl: heartbeat, osmium: MOTIS map
mkdir -p /opt/leoway && cd /opt/leoway
git clone https://github.com/LPuehringerStudent/LeoWay.git repo && cp -r repo/deploy . && cd deploy
cp .env.example .env && chmod 600 .env   # fill in passwords and keys (see comments in the file)
docker compose up -d --wait db backend web   # motis has no data yet, see "MOTIS trip planner"
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

### Automatically (#83)

Every push to `main` that passes CI is built by `.github/workflows/publish.yml` and pushed to GHCR as
`ghcr.io/lpuehringerstudent/leoway-{backend,web}:<commit>` and `:main`. On the server, `leoway-deploy.timer` runs
`auto-deploy.sh` every 5 minutes. The server only pulls: GitHub has no SSH key, no port is open and no self-hosted
runner runs here (the repository is public, so pull requests from forks could run code on such a runner).

`auto-deploy.sh`, when `:main` has a commit that is not deployed yet:

1. waits during the quiet window, Mon–Fri 06:00–08:30 Vienna time (no holiday calendar) (`AUTO_DEPLOY_QUIET=1-5,06:00-08:30`, `off`
   disables it)
2. runs `backup.sh`; if the backup fails, nothing is deployed
3. tags the new images as `leoway-*:latest` and runs `docker compose up -d --wait`
4. if the containers don't become healthy within 3 minutes, puts the previous images back and does not try that
   commit again; the next merge deploys again
5. sends the result to `AUTO_DEPLOY_NOTIFY_URL` (an ntfy topic URL), if set

A rollback does not undo database migrations. Flyway ignores migrations newer than the code (its default
`ignoreMigrationPatterns=*:future`; don't set `quarkus.flyway.ignore-migration-patterns` without keeping it), so old
code still starts; if a migration itself broke data, use `restore.sh` with the backup from step 2.

Setup, once:

```sh
# GitHub: Packages leoway-backend and leoway-web -> Package settings -> Change visibility -> Public
# (the images hold no secrets; .env stays on the server). Then on the server:
rsync -a --exclude .env deploy/ leoway:/opt/leoway/deploy/
ssh leoway 'cp /opt/leoway/deploy/systemd/leoway-deploy.* /etc/systemd/system/ \
  && systemctl daemon-reload && systemctl enable --now leoway-deploy.timer'
ssh leoway 'systemctl start leoway-deploy.service; journalctl -u leoway-deploy -n 20'
```

Pause it with `systemctl stop leoway-deploy.timer`, e.g. to pin an older version or to test with `ship.sh`. While the
timer runs, a newer `main` replaces anything shipped by hand, and its rollback goes to the image it replaced, which
may be the hand-shipped one. The last 5 deployed commits stay as `leoway-*:<commit>` (`AUTO_DEPLOY_KEEP`) for
`LEOWAY_VERSION=<commit>`; older ones are removed. Changes in `deploy/` itself (compose file, scripts) are
not deployed by it; copy those with the `rsync` line above.

### By hand

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
| `motis` | MOTIS trip planner on the MVO timetables (#104), not used by the backend yet | compose network only |

Uptime Kuma checks `http://192.168.178.11:8080/api/health/ready` (database included).

A second Kuma HTTP monitor on `http://192.168.178.11:8080/api/health/feeds` alerts the team when a feed stops working
(F09). It answers 503 when a feed is `STALE` or `DOWN`, or has no status yet. Give the monitor at least one retry, because
a feed has no status for a moment on a brand-new database. `/api/admin/feeds` has the error details.

## MOTIS trip planner (#104)

MOTIS plans trips on the free MVO GTFS timetables instead of VAO (#93). It runs as the `motis` service from the
official image `ghcr.io/motis-project/motis` (version pinned in `MOTIS_VERSION`) and reads its data from
`MOTIS_DIR` (default `/opt/leoway/motis`), read-only.

`motis-update.sh` keeps the data fresh. `leoway-motis.timer` runs it every night at 03:15 Vienna time, after the
backup. Each run:
1. downloads the MVO GTFS data sets rail (66), Linz AG (69) and Upper Austria (77) for this timetable year, and
   for the next one once MVO publishes it (timetable change in mid-December)
2. refreshes the street map once a week: Geofabrik Austria, cropped to Upper Austria with `osmium`
3. imports into a new folder `data-<time>` in its own container (about 1 minute; at most 3 cores and 4 GB, peak
   about 2.1 GB)
4. points `current` at it, restarts `motis` (a few seconds without answers), and checks the health and a test trip
   Linz Hauptbahnhof → Limesstraße, which must contain a bus, tram or train leg (a walking-only answer means the
   timetables are unusable)
5. if the check fails, points `current` back at the old data and restarts again. On the very first run there is no
   old data: it removes `current` and stops `motis`, and the next run starts clean

If next year's data is published but its terms aren't accepted (HTTP 451), the update still runs with this year's data
and sends a warning every night until you accept them; otherwise trips would quietly stop about 30 days after the
timetable change. A 451 for the current year fails the update.

A failed download or import changes nothing. Results go to `AUTO_DEPLOY_NOTIFY_URL` if set, otherwise to the journal
(`journalctl -u leoway-motis`). The data before the current one stays for a manual rollback
(`ln -sfn data-<older> current && docker compose up -d --force-recreate motis`). Disk use: about 2 GB
(two data folders of ~0.8 GB, the GTFS zips and the map).

App deploys (`auto-deploy.sh`) only start `db`, `backend` and `web`, so a MOTIS problem never rolls back an app
deploy.

Setup, once (the script creates `MOTIS_DIR` and gives it to the MOTIS user, uid `MOTIS_UID`; don't start `motis`
before the first import, it only restarts without data):
1. Create a free account on [data.mobilitaetsverbuende.at](https://data.mobilitaetsverbuende.at) and accept the
   terms of data sets 66, 69 and 77 (otherwise downloads answer HTTP 451). Accept them again for each new
   timetable year.
2. Put `MVO_USER` and `MVO_PASSWORD` into `.env`. The password goes to MVO through stdin, never on a command line.
3. Then:

```sh
rsync -a --exclude .env deploy/ leoway:/opt/leoway/deploy/
ssh leoway 'apt-get install -y osmium-tool && cd /opt/leoway/deploy && ./motis-update.sh'
ssh leoway 'cp /opt/leoway/deploy/systemd/leoway-motis.* /etc/systemd/system/ \
  && systemctl daemon-reload && systemctl enable --now leoway-motis.timer'
```

Check it from inside the compose network:

```sh
docker compose exec motis wget -qO- 'http://127.0.0.1:8080/api/v1/geocode?text=Limesstrasse' | head -c 300
```

Licence: the MVO data must be credited ("Data source: Mobilitätsverbünde Österreich OG"). The credit in the app
comes with the backend switch to MOTIS (#93 follow-ups).

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
