# LeoWay

Commute assistant for HTL Leonding: connects each student's WebUntis timetable with live public transport data,
tells them when to leave, and drafts delay appeals to the form teacher.

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
