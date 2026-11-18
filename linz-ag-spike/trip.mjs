// Probe: can the Linz AG EFA trip request plan home stop -> school stop for leave-by and alternatives? (issue #56)
//
// Usage:
//   node trip.mjs stops <name> [<name> ...]      stop finder: name -> stop ids
//   node trip.mjs trips [YYYYMMDD] [HHMM]         every home stop -> both school stops, arriving by HHMM
//                                                 (default: today, 07:50)
//
// Needs Node 18 or newer, no packages. Raw responses go to probe-output/ (gitignored, licence question #88).
import { mkdirSync, writeFileSync } from 'node:fs';

const BASE = 'https://www.linzag.at/static';
const USER_AGENT = 'LeoWay-school-project-spike';
const OUTPUT_DIR = new URL('./probe-output/', import.meta.url);

// Same budget as probe.mjs and the recorder: at most one request every 15 s.
const REQUEST_GAP_MS = 15_000;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let lastRequest = 0;

const HOME_STOPS = [
    { id: '60501720', name: 'Linz Hauptbahnhof' },
    { id: '60501810', name: 'Linz Auwiesen' },
    { id: '60501660', name: 'Leonding Doblerholz' },
];
const SCHOOL_STOPS = [
    { id: '60500120', name: 'Meixnerkreuzung' },
    { id: '60501780', name: 'Limesstraße' },
];

async function efa(endpoint, params) {
    await sleep(Math.max(0, lastRequest + REQUEST_GAP_MS - Date.now()));
    lastRequest = Date.now();
    const query = new URLSearchParams({
        outputFormat: 'JSON',
        locationServerActive: '1',
        stateless: '1', // required since December 2015 (EFA_XML_Schnittstelle_20151217.pdf)
        coordOutputFormat: 'WGS84[dd.ddddd]',
        ...params,
    });
    const res = await fetch(`${BASE}/${endpoint}?${query}`, {
        headers: { 'User-Agent': USER_AGENT },
        signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) throw new Error(`${endpoint}: HTTP ${res.status}`);
    return res.json();
}

function save(name, body) {
    mkdirSync(OUTPUT_DIR, { recursive: true });
    writeFileSync(new URL(`${name}.json`, OUTPUT_DIR), JSON.stringify(body));
}

// EFA returns one match as an object and several as an array
const asList = (x) => (x == null ? [] : Array.isArray(x) ? x : x.point ? [x.point] : [x]);

async function stops(names) {
    for (const name of names) {
        const body = await efa('XML_STOPFINDER_REQUEST', { type_sf: 'any', name_sf: name, anyObjFilter_sf: '2' });
        const points = asList(body.stopFinder?.points);
        console.log(`\n"${name}": ${points.length} stop(s)`);
        for (const p of points) console.log(`  ${p.ref?.id}  ${p.name}  quality ${p.quality}  ${p.ref?.coords}`);
    }
}

const attr = (attrs, name) => attrs?.find((a) => a.name === name)?.value ?? null;
const minutesOf = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

// A leg reduced to what leave-by (C) and alternatives (G) would need
function normaliseLeg(leg) {
    const from = leg.points[0];
    const to = leg.points.at(-1);
    const mode = leg.mode ?? {};
    const diva = mode.diva ?? {};
    const walk = !diva.stateless || diva.stateless.startsWith(':');
    return {
        walk,
        line: walk ? 'walk' : mode.number,
        from: from.nameWO,
        to: to.nameWO,
        toBay: to.ref?.platform ?? null,
        plannedDep: from.dateTime.time,
        realDep: from.dateTime.rtTime ?? null,
        plannedArr: to.dateTime.time,
        realArr: to.dateTime.rtTime ?? null,
        realtime: mode.realtime === '1',
        status: leg.realtimeStatus ?? null,
        avmsTripId: attr(diva.attrs, 'AVMSTripID'),
        // The recorder's fallback id when AVMSTripID is missing (LinzAgAdapter.tripId): stateless + "#" + key.
        // The trip request's diva.tripCode is the departure monitor's servingLine.key.
        fallbackId: walk ? null : `${diva.stateless}#${diva.tripCode}`,
    };
}

function printTrip(trip, arriveBy) {
    const legs = trip.legs.map(normaliseLeg);
    const rides = legs.filter((l) => !l.walk);
    const arrival = legs.at(-1).realArr ?? legs.at(-1).plannedArr;
    const late = minutesOf(arrival) > minutesOf(arriveBy);
    console.log(
        `  ${legs[0].plannedDep} -> ${legs.at(-1).plannedArr}  ${trip.duration}  ${trip.interchange} change(s)` +
            `  ${rides.map((l) => l.line).join(' > ')}${late ? `  ARRIVES AFTER ${arriveBy}` : ''}`,
    );
    for (const l of legs) {
        if (l.walk) {
            console.log(`      walk ${l.from} -> ${l.to}, ${l.plannedDep}-${l.plannedArr}`);
            continue;
        }
        const dep = l.realDep && l.realDep !== l.plannedDep ? `${l.plannedDep} (now ${l.realDep})` : l.plannedDep;
        const arr = l.realArr && l.realArr !== l.plannedArr ? `${l.plannedArr} (now ${l.realArr})` : l.plannedArr;
        console.log(
            `      ${l.line.padEnd(4)} ${l.from} ${dep} -> ${l.to} ${arr} bay ${l.toBay ?? '-'}` +
                `  realtime ${l.realtime ? l.status ?? 'yes' : 'no'}  AVMSTripID ${l.avmsTripId ?? '-'}  fallback ${l.fallbackId}`,
        );
    }
    return { late, rides };
}

async function trips(date, arriveBy) {
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const totals = { requests: 0, trips: 0, late: 0, rides: 0, withAvms: 0, withRealtime: 0 };
    for (const home of HOME_STOPS) {
        for (const school of SCHOOL_STOPS) {
            const body = await efa('XML_TRIP_REQUEST2', {
                type_origin: 'any',
                name_origin: home.id,
                type_destination: 'any',
                name_destination: school.id,
                itdDate: date,
                itdTime: arriveBy.replace(':', ''),
                itdTripDateTimeDepArr: 'arr',
                calcNumberOfTrips: '4',
                useRealtime: '1',
            });
            totals.requests++;
            save(`trip-${stamp}-${home.id}-${school.id}`, body);
            const list = body.trips ?? [];
            console.log(`\n${home.name} -> ${school.name}, arrive by ${arriveBy} on ${date}: ${list.length} trip(s)`);
            for (const message of asList(body.itdMessageList?.itdMessage ?? body.itdMessageList)) {
                if (message?.value) console.log(`  message: ${message.value}`);
            }
            for (const trip of list) {
                const { late, rides } = printTrip(trip, arriveBy);
                totals.trips++;
                if (late) totals.late++;
                totals.rides += rides.length;
                totals.withAvms += rides.filter((l) => l.avmsTripId).length;
                totals.withRealtime += rides.filter((l) => l.realtime).length;
            }
        }
    }
    console.log(
        `\n${totals.requests} requests, ${totals.trips} trips, ${totals.late} arriving after ${arriveBy}, ` +
            `${totals.rides} rides: ${totals.withAvms} with AVMSTripID, ${totals.withRealtime} with real-time`,
    );
}

const todayVienna = () =>
    new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Vienna' }).format(new Date()).replaceAll('-', '');

const [command, ...args] = process.argv.slice(2);
if (command === 'stops') await stops(args);
else if (command === 'trips') {
    // || not ??: an empty date argument must fall back too (EFA answers an empty itdDate with 0 trips, no error)
    const time = args[1] || '0750';
    await trips(args[0] || todayVienna(), `${time.slice(0, 2)}:${time.slice(2, 4)}`);
} else {
    console.log('Usage: node trip.mjs stops <name> ... | node trip.mjs trips [YYYYMMDD] [HHMM]');
    process.exitCode = 1;
}
