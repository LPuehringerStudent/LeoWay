# Free data instead of VAO (#93)

Can LeoWay plan trips and find stops with free timetable data and a self-hosted planner, without VAO's 100 queries per day?

## Data
- **GTFS** from Mobilitätsverbünde Österreich (MVO): rail (66), Linz AG (69), Upper Austria incl. flex (77). Free account on [data.mobilitaetsverbuende.at](https://data.mobilitaetsverbuende.at); `fetch.sh` gets a token and downloads them through the MVO API.
- **Street map**: Geofabrik Austria, cropped to Upper Austria:
  ```bash
  curl -L -o data/austria.osm.pbf https://download.geofabrik.de/europe/austria-latest.osm.pbf
  docker run --rm -u "$(id -u):$(id -g)" -v "$PWD/data":/d -w /d iboates/osmium:latest \
    extract -b 12.7,47.4,15.1,48.8 austria.osm.pbf -o upper-austria.osm.pbf
  ```
- **ÖBB train runs** (planned and actual times, weekly, CC BY): `curl -o data/oebb/zugfahrten.zip https://static.web.oebb.at/open-data/infra/delVO_mmtis/mmtis_zugfahrten.zip` and unzip in `data/oebb/` (terms: data.oebb.at/de/nutzungsbedingungen). `python3 delays.py <train numbers>` prints the typical delay.
- Everything goes into `data/` (gitignored, licence terms in #93).

## Run
```bash
MVO_USER=... MVO_PASSWORD=... ./fetch.sh   # read -s the password, don't type it into the command line
./run.sh                                     # imports into MOTIS, prints import time and peak memory, starts on :8080
node plan.mjs                                # next weekday, arrive by 07:50
node plan.mjs 2026-11-19 07:50
```

## Result (2026-11-18, details in #93)
- **Yes, VAO is not needed for trip planning and stop search.** MOTIS with the MVO GTFS plans all test routes, including Wels, Enns and Freistadt, with the same times as Linz AG's planner inside Linz.
- **Live times for Linz** come from the Linz AG departure board (no open GTFS-RT); it accepts MOTIS' stop ids (`at:44:41084`), and legs are matched by line, stop, platform and planned time.
- **Trains**: no open live data in Austria. `delays.py` gives the typical delay per train from a year of ÖBB train runs.
- **On CT 104**: import 75 s, peak 2.1 GB; running 0.2–0.3 GB; plan 48–62 ms, stop search 7 ms.
