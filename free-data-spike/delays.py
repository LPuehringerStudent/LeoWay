"""Typical arrival delay per train from ÖBB "Zugfahrten" (#93).

Usage: python3 delays.py 3829 3003 6374   (train numbers, e.g. tripShortName from MOTIS)
Needs data/oebb/zugfahrten.zip unpacked into data/oebb (see README). Weekdays only.
Caveat: the CSV has one row per train run with start and end on the ÖBB-Infra network only, so the
delay is the one at the end of the run, not at a given station.
"""
import csv
import glob
import statistics
import sys
from datetime import datetime


def parse(s):
    return datetime.strptime(s, '%Y-%m-%dT%H:%M:%S%z')


wanted = set(sys.argv[1:])
runs = {k: [] for k in wanted}
for f in sorted(glob.glob('data/oebb/20*/*_mmtis_zugfahrten.csv')):
    for r in csv.DictReader(open(f, encoding='utf-8')):
        if r['zugnummer'] in wanted and r['ankunftzeit_soll'] and parse(r['ankunftzeit_soll']).weekday() < 5:
            runs[r['zugnummer']].append(r)

for train, rows in sorted(runs.items()):
    d = sorted((parse(r['ankunftzeit_ist']) - parse(r['ankunftzeit_soll'])).total_seconds() / 60
               for r in rows if r['ankunftzeit_ist'])
    if not d:
        print(f'train {train}: no data')
        continue
    p85 = d[min(len(d) - 1, int(0.85 * len(d)))]
    print(f'train {train}: {len(rows)} weekdays, median {statistics.median(d):.1f} min, '
          f'85 % within {p85:.1f} min, over 5 min on {sum(x > 5 for x in d) / len(d):.0%}, '
          f'no actual time {len(rows) - len(d)}')
