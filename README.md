# LeoWay

Commute assistant for HTL Leonding: connects each student's WebUntis timetable with live public transport data,
tells them when to leave, and drafts delay appeals to the form teacher.

- `docs/` — project proposal (PDF) and the VAO Start contract
- `webuntis-spike/` — feasibility test against HTL Leonding's WebUntis

## Running the WebUntis spike

```sh
cd webuntis-spike
npm install
cp .env.example .env   # fill in UNTIS_USER and UNTIS_SECRET (WebUntis > Profil > Freigaben)
node probe.mjs 2026-10-05
node absences.mjs
```
