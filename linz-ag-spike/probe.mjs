// Probe: does the Linz AG EFA departure monitor give the delay recorder what it needs? (issue #3)
//
// Usage:
//   node probe.mjs                     one snapshot of every stop
//   node probe.mjs poll [minutes]      poll for a while, then report (default 20 minutes)
//
// Needs Node 18 or newer, no packages. The feed is open, no key required.
// Output goes to probe-output/ (gitignored).
import { appendFileSync, mkdirSync } from 'node:fs';

const BASE = 'https://www.linzag.at/static';
const USER_AGENT = 'LeoWay-school-project-spike';
const OUTPUT_DIR = new URL('./probe-output/', import.meta.url);

// IDs from XML_STOPFINDER_REQUEST
const STOPS = [
    { id: '60501780', name: 'Limesstraße', lines: ['19'] },
    { id: '60500120', name: 'Meixnerkreuzung', lines: ['3', '4', '11', '19', '191'] },
];

// At most one request every 15 s across all stops. Linz AG publishes no limit; this is the floor used by
// rolandzeiner/linz-linien-austria, and we want to stay well clear of anything that could look like abuse.
const REQUEST_GAP_MS = 15_000;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function fetchDepartures(stopId) {
    const params = new URLSearchParams({
        outputFormat: 'JSON',
        locationServerActive: '1',
        stateless: '1', // required since December 2015 (EFA_XML_Schnittstelle_20151217.pdf)
        type_dm: 'any',
        name_dm: stopId,
        limit: '40',
        mode: 'direct',
        useRealtime: '1',
        coordOutputFormat: 'WGS84[dd.ddddd]',
    });
    const res = await fetch(`${BASE}/XML_DM_REQUEST?${params}`, {
        headers: { 'User-Agent': USER_AGENT },
        signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const body = await res.json();
    const serverTime = body.parameters?.find((p) => p.name === 'serverTime')?.value;
    return { serverTime, departures: body.departureList ?? [] };
}

const hhmm = (t) => (t ? `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}` : null);
const isoDate = (t) => `${t.year}-${String(t.month).padStart(2, '0')}-${String(t.day).padStart(2, '0')}`;

// One row of the feed, reduced to what the recorder would store
function normalise(raw, stop) {
    const line = raw.servingLine;
    const delay = line.delay === undefined ? null : Number(line.delay);
    return {
        stopId: stop.id,
        bay: raw.platform,
        bayName: raw.stopName,
        line: line.number,
        direction: line.liErgRiProj?.direction,
        destination: line.direction,
        lineKey: line.stateless, // e.g. esg:02019:E:R:f26, "f26" is the timetable period
        tripKey: line.key, // only unique within a line
        avmsTripId: raw.attrs?.find((a) => a.name === 'AVMSTripID')?.value ?? null,
        serviceDate: isoDate(raw.dateTime),
        planned: hhmm(raw.dateTime),
        realtimeTime: hhmm(raw.realDateTime),
        delay: delay === -9999 ? null : delay,
        hasRealtime: line.realtime === '1',
        status: raw.realtimeStatus ?? null,
        tripStatus: raw.realtimeTripStatus ?? null,
        countdown: Number(raw.countdown),
    };
}

function printSnapshot(stop, serverTime, rows) {
    console.log(`\n${stop.name} (${stop.id}), server time ${serverTime}, ${rows.length} departures`);
    const seen = new Set(rows.map((r) => r.line));
    console.log(`  lines in window: ${[...seen].sort().join(', ')}`);
    console.log(`  expected but not in window: ${stop.lines.filter((l) => !seen.has(l)).join(', ') || '-'}`);
    for (const r of rows) {
        console.log(
            `  ${r.planned} -> ${(r.realtimeTime ?? '-').padEnd(5)} delay ${String(r.delay ?? '-').padStart(4)}` +
                `  ${r.line.padEnd(4)} ${r.destination.padEnd(24)} bay ${r.bay.padEnd(2)}` +
                `  in ${String(r.countdown).padStart(4)} min  trip ${r.avmsTripId}  ${r.status ?? r.tripStatus ?? ''}`,
        );
    }
}

async function snapshot() {
    for (const [i, stop] of STOPS.entries()) {
        if (i > 0) await sleep(REQUEST_GAP_MS);
        const { serverTime, departures } = await fetchDepartures(stop.id);
        printSnapshot(stop, serverTime, departures.map((d) => normalise(d, stop)));
    }
}

async function poll(minutes) {
    mkdirSync(OUTPUT_DIR, { recursive: true });
    const file = new URL(`poll-${new Date().toISOString().replace(/[:.]/g, '-')}.jsonl`, OUTPUT_DIR);
    const end = Date.now() + minutes * 60_000;
    let requests = 0;
    let failures = 0;
    const statuses = new Set();

    while (Date.now() < end) {
        for (const stop of STOPS) {
            const collectedAt = new Date().toISOString();
            const started = Date.now();
            requests++;
            try {
                const { serverTime, departures } = await fetchDepartures(stop.id);
                const rows = departures.map((d) => normalise(d, stop));
                appendFileSync(file, `${JSON.stringify({ collectedAt, serverTime, stopId: stop.id, rows })}\n`);
            } catch (e) {
                failures++;
                statuses.add(e.message);
                appendFileSync(file, `${JSON.stringify({ collectedAt, stopId: stop.id, error: e.message })}\n`);
            }
            await sleep(Math.max(0, REQUEST_GAP_MS - (Date.now() - started)));
        }
    }
    console.log(`${requests} requests, ${failures} failed ${[...statuses].join('; ')}`);
    console.log(`Wrote ${file.pathname}`);
    console.log(`Run: node analyse.mjs ${file.pathname}`);
}

if (process.argv[2] === 'poll') await poll(Number(process.argv[3] ?? 20));
else await snapshot();
