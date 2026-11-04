# LeoWay

Commute assistant for HTL Leonding: connects each student's WebUntis timetable with live public transport data,
tells them when to leave, and drafts delay appeals to the form teacher.

## Local development

Install the .NET 10 SDK (see `global.json`), Node.js 26 (see `.nvmrc`), and Docker with Compose.
Run these commands from the repository root in PowerShell.

```powershell
Copy-Item .env.example .env
# Edit .env and choose a local database password (letters and numbers work here).
docker compose up -d --wait db
dotnet restore backend
dotnet tool restore
# Use the same password as LEOWAY_DB_PASSWORD in .env.
$env:ConnectionStrings__LeoWay = 'Host=localhost;Database=leoway;Username=leoway;Password=YOUR_LOCAL_PASSWORD'
dotnet ef database update --project backend
dotnet run --project backend
```

On macOS or Linux, use `cp` for the copy and
`export ConnectionStrings__LeoWay='...'` to set the connection string.

In a second terminal, also from the repository root:

```sh
npm --prefix frontend ci
npm --prefix frontend start
```

Open <http://localhost:4200>. The page checks the API every five seconds.
The API runs on <http://localhost:5080> with `/health/live`, `/api/health/ready`,
and the development OpenAPI document at `/openapi/v1.json`.
Stop and restart the database with `docker compose stop db` and
`docker compose up -d --wait db` to see the status change.

Build and test from the root:

```sh
dotnet build backend
npm --prefix frontend run build
npm --prefix frontend test -- --watch=false
```

Use `docker compose down` to stop PostgreSQL and keep its data.

## Repository layout

- `backend/` — ASP.NET Core API and EF Core migrations
- `frontend/` — Angular app
- `docs/` — project proposal, system specification and VAO Start contract
- `webuntis-spike/` — feasibility test against HTL Leonding's WebUntis

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
to a folder containing `times.ttf`, `timesbd.ttf`, `timesi.ttf` and `timesbi.ttf`.
Font files are not included in the repository.
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
