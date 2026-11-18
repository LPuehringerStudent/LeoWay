# LeoWay

Commute assistant for HTL Leonding: connects each student's WebUntis timetable with live public transport data,
tells them when to leave, and drafts delay appeals to the form teacher.

## Local development

Install JDK 25, Maven 3.9, Node.js 26 (see `.nvmrc`), and Docker with Compose.
Start Docker and run this from the repository root on Windows or Linux:

```sh
npm start
```

This creates `.env` if needed, starts PostgreSQL, installs frontend dependencies,
applies migrations, and launches the API and Angular. Open <http://localhost:4200>.
Press Ctrl+C to stop both apps. Run `docker compose down` to stop PostgreSQL and keep its data.
If PowerShell blocks `npm.ps1`, use `npm.cmd start`.

Wait for Angular to finish compiling, then open <http://localhost:4200> for the website.
Port 5080 serves the backend API; its root URL can show "Resource not found".
Use <http://localhost:5080/q/swagger-ui> to browse the API instead.
Angular analytics is disabled for this workspace so the first start does not wait for a consent prompt.

### Manual startup

Run these commands from the repository root in PowerShell. If `.env` already exists, use it.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Edit .env and choose a local database password (letters and numbers work here).
docker compose up -d --wait db
# Use the same password as LEOWAY_DB_PASSWORD in .env.
$env:QUARKUS_DATASOURCE_PASSWORD = 'YOUR_LOCAL_PASSWORD'
$env:QUARKUS_PROFILE = 'local'
mvn -f backend/pom.xml package
java -jar backend/target/quarkus-app/quarkus-run.jar
```

On macOS or Linux, use `cp` for the copy and `export NAME=value` for the two
environment variables. Flyway applies migrations when the API starts with the local profile.

In a second terminal, also from the repository root:

```sh
npm --prefix frontend ci
npm --prefix frontend start
```

Open <http://localhost:4200>. The page checks the API every five seconds.
The API runs on <http://localhost:5080> with `/health/live`, `/api/health/ready`, `/api/health/feeds`,
and the OpenAPI document at `/q/openapi`.
Stop and restart the database with `docker compose stop db` and
`docker compose up -d --wait db` to see the status change.

Build and test from the root:

```sh
mvn -f backend/pom.xml verify
npm --prefix frontend run build
npm --prefix frontend test -- --watch=false
```

Use `docker compose down` to stop PostgreSQL and keep its data.

## API documentation

### Local administrator access

The **Admin** navigation item appears between Home and Route for administrators and opens
`/admin/feeds`. The page shows feed health, last success and last error, with a 30-second refresh.
Students and logged-out visitors who open the URL directly see "Not allowed".

To enable your WebUntis username as an administrator locally, stop the app, then run in PowerShell:

```powershell
$env:LEOWAY_ADMINS = 'your.webuntis.username'
npm.cmd start
```

Alternatively, set `LEOWAY_ADMINS=username.one,username.two` in the root `.env` file;
`npm start` passes this local setting to the backend. An existing shell variable takes precedence.

Log out and back in through Connect WebUntis with your normal access key to receive the new role.
Admin accounts can use their own routes and timetable data as well as the admin panel.
The local setting does not change the production allowlist in `deploy/.env`.

### OpenAPI

- **Swagger UI:** <http://localhost:5080/q/swagger-ui> while the API runs (`npm start` or the local profile).
- **OpenAPI file:** [`docs/api/openapi.yaml`](docs/api/openapi.yaml). Every backend build regenerates it.
  Commit it together with endpoint changes; CI fails if it is outdated.
- Give new endpoints an `@Operation` summary, `@APIResponse` for each error status, and a `@Tag`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for branches, pull requests and commit rules.

## Repository layout

- `backend/` — Java Quarkus API and Flyway migrations
- `frontend/` — Angular app
- `docs/` — project proposal, system specification, VAO Start contract and `api/openapi.yaml`
- `deploy/` — interim server deployment and backups (see `deploy/README.md`)
- `webuntis-spike/` — feasibility test against HTL Leonding's WebUntis
- `linz-ag-spike/` — feasibility test against the Linz AG live departures and trip planner

## System specification

Read the [PDF](docs/LeoWay-System-Specification.pdf) or edit the
[Markdown source](docs/LeoWay-System-Specification.md).

To rebuild the PDF with Python 3.10 or later:

```sh
python -m pip install -r docs/tools/requirements.txt
python docs/tools/build_spec.py
```

The PDF uses Times New Roman throughout, with 12-point body text and bold headings.
This font is recommended in the [official BBS layout guidance](https://www.diplomarbeiten-bbs.at/durchfuehrung/gliederung-der-diplomarbeit-und-formale-vorgaben).
The builder reads the installed Windows fonts. On other systems, set `LEOWAY_FONT_DIR`
to a folder containing `times.ttf`, `timesbd.ttf`, `timesi.ttf` and `timesbi.ttf`. Without them it falls back to
Liberation Serif (package `ttf-liberation` / `fonts-liberation`), which has the same letter widths, so the layout is
identical. Font files are not included in the repository.
Use case, activity and architecture diagrams and the screen sketches are drawn from
the Markdown blocks (a small Mermaid subset and `screens` blocks). Only the domain model
is drawn by hand in `docs/tools/build_spec.py`; update it too when changing that diagram.

## Running the WebUntis spike

```sh
cd webuntis-spike
npm install
cp .env.example .env   # fill in UNTIS_USER and UNTIS_SECRET (WebUntis > Profil > Freigaben)
node probe.mjs 2026-10-05
node absences.mjs
```

## Running the Linz AG spike

```sh
cd linz-ag-spike
node probe.mjs                 # one snapshot of the stops near school
node probe.mjs poll 20         # poll for 20 minutes, writes to probe-output/
node analyse.mjs probe-output/poll-<timestamp>.jsonl
node trip.mjs stops Meixnerkr  # stop finder: name -> stop ids
node trip.mjs trips 20261119 0750   # home stops -> school stops, arriving by 07:50
```
