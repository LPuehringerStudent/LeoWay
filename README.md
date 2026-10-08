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

The PDF includes the use cases, domain model and home screen sketch. Windows uses
Calibri and Georgia when available; other systems use standard PDF fonts.
The PDF drawings live in `docs/tools/build_spec.py`; update them too when changing
the diagrams or screen sketch in Markdown.

## Running the WebUntis spike

```sh
cd webuntis-spike
npm install
cp .env.example .env   # fill in UNTIS_USER and UNTIS_SECRET (WebUntis > Profil > Freigaben)
node probe.mjs 2026-10-05
node absences.mjs
```
