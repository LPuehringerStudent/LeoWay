# Deployment

LeoWay runs on an **interim host** until the school decides on LeoCloud (#8).
Everything needed to run it is in this folder; nothing is installed by hand except Docker.

## Interim host: R740, container `leoway`

| | |
| --- | --- |
| Host | Proxmox on Laurenz's Dell R740, LXC **CT 104 `leoway`** (Debian 13, unprivileged, Docker) |
| Resources | 4 cores, 8 GB RAM, 40 GB disk (shared NVMe thin pool) |
| Address | `192.168.178.31` on the home LAN, reached from outside through Tailscale via the Proxmox host |
| Responsible | @LPuehringerStudent |
| App folder | `/opt/leoway/deploy` (this folder) with `.env` (secrets, `chmod 600`, never committed) |
| Backups | `/var/backups/leoway`, daily at 02:30 (`leoway-backup.timer`), kept 7 days |
| Monitoring | Uptime Kuma on CT 103 (`192.168.178.30:3001`) |

Availability depends on home power and internet. Outages show up as gaps in the recorded data and are not hidden.

## First setup on a new host

```sh
apt-get install -y docker.io docker-compose age rsync git
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
- `BACKUP_AGE_RECIPIENT`: the **public** key from `age-keygen`. Keep the private key **off the server** (password manager); without it backups can't be read.

## Updating

```sh
cd /opt/leoway/repo && git pull && rsync -a --exclude .env ../repo/deploy/ ../deploy/
cd ../deploy && docker compose pull && docker compose up -d --wait
```

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

**Deleted accounts:** a restore brings back accounts deleted after the backup was made. Until #20 handles this,
re-apply account deletions after every restore (spec A13).

Restore tests on 2026-11-04: normal restore, a truncated backup (rejected, live DB untouched) and a backup older
than the schema (newer table gone after the restore).

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
