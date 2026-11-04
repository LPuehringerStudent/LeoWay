// Answers the recorder questions from a file written by `node probe.mjs poll`.
// Usage: node analyse.mjs probe-output/poll-....jsonl
import { readFileSync } from 'node:fs';

const polls = readFileSync(process.argv[2], 'utf8').trim().split('\n').map((l) => JSON.parse(l));
const good = polls.filter((p) => p.rows);
console.log(`${polls.length} polls, ${polls.length - good.length} failed`);
console.log(`From ${polls[0].collectedAt} to ${polls.at(-1).collectedAt}`);

const groupBy = (items, keyOf) => {
    const groups = new Map();
    for (const item of items) {
        const key = keyOf(item);
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key).push(item);
    }
    return groups;
};
const rows = good.flatMap((p) => p.rows.map((r) => ({ ...r, collectedAt: p.collectedAt })));

// Does AVMSTripID identify a trip?
const withId = rows.filter((r) => r.avmsTripId).length;
console.log(`\nAVMSTripID present on ${withId} of ${rows.length} rows`);
const idsPerScheduled = [...groupBy(rows, (r) => `${r.lineKey}|${r.tripKey}|${r.stopId}|${r.bay}|${r.serviceDate}|${r.planned}`).values()].map(
    (g) => new Set(g.map((r) => r.avmsTripId)).size,
);
console.log(`Scheduled departures whose AVMSTripID changed between polls: ${idsPerScheduled.filter((n) => n > 1).length} of ${idsPerScheduled.length}`);
const scheduledPerKey = [...groupBy(rows, (r) => `${r.avmsTripId}|${r.stopId}|${r.bay}|${r.serviceDate}`).values()].map(
    (g) => new Set(g.map((r) => r.planned)).size,
);
console.log(`AVMSTripID + stop + bay + date matching more than one planned time: ${scheduledPerKey.filter((n) => n > 1).length} of ${scheduledPerKey.length}`);

// Which unique key holds within one poll? Two rows of one poll with the same key would be lost by the database.
const candidates = {
    'tripKey, stop, date': (r) => `${r.tripKey}|${r.stopId}|${r.serviceDate}`,
    'line, tripKey, stop, date (planned in #18)': (r) => `${r.lineKey}|${r.tripKey}|${r.stopId}|${r.serviceDate}`,
    'AVMSTripID, stop, date': (r) => `${r.avmsTripId}|${r.stopId}|${r.serviceDate}`,
    'AVMSTripID, stop, bay, date': (r) => `${r.avmsTripId}|${r.stopId}|${r.bay}|${r.serviceDate}`,
    'line, tripKey, stop, bay, date': (r) => `${r.lineKey}|${r.tripKey}|${r.stopId}|${r.bay}|${r.serviceDate}`,
};
console.log('\nUnique key candidates: departures that collide');
for (const [name, keyOf] of Object.entries(candidates)) {
    const collisions = new Set();
    for (const p of good) {
        const seen = new Map();
        for (const r of p.rows) {
            const key = keyOf(r);
            const planned = `${r.bay}|${r.planned}`;
            if (seen.has(key) && seen.get(key) !== planned) collisions.add(key);
            seen.set(key, planned);
        }
    }
    console.log(`  ${String(collisions.size).padStart(4)}  ${name}${collisions.size ? `, e.g. ${[...collisions][0]}` : ''}`);
}

// How often does the data change?
const departures = groupBy(rows, (r) => `${r.lineKey}|${r.avmsTripId}|${r.stopId}|${r.bay}|${r.serviceDate}|${r.planned}`);
const gaps = [];
for (const obs of departures.values()) {
    let lastChange = null;
    for (let i = 1; i < obs.length; i++) {
        if (obs[i].delay !== obs[i - 1].delay && obs[i].delay !== null && obs[i - 1].delay !== null) {
            const t = Date.parse(obs[i].collectedAt);
            if (lastChange !== null) gaps.push((t - lastChange) / 1000);
            lastChange = t;
        }
    }
}
gaps.sort((a, b) => a - b);
console.log(`\nDelay changes within one departure: ${gaps.length} measurable gaps`);
if (gaps.length) {
    const pct = (p) => Math.round(gaps[Math.floor((gaps.length - 1) * p)]);
    console.log(`  seconds between changes: min ${pct(0)}, median ${pct(0.5)}, p90 ${pct(0.9)}, max ${pct(1)}`);
}

// At what distance does a real-time value appear, and how far is a far-out value from the last one?
const buckets = [[0, 5], [5, 15], [15, 30], [30, 60], [60, 120], [120, Infinity]];
console.log('\nBy minutes until departure: rows, with real time, delay not 0');
for (const [lo, hi] of buckets) {
    const b = rows.filter((r) => r.countdown >= lo && r.countdown < hi && !r.status);
    const rt = b.filter((r) => r.hasRealtime);
    console.log(`  ${String(lo).padStart(3)}-${String(hi).padEnd(8)} ${String(b.length).padStart(6)} ${String(rt.length).padStart(6)} ${String(rt.filter((r) => r.delay).length).padStart(6)}`);
}

console.log('\nReal-time coverage per line, departures seen within 15 min of leaving');
for (const [line, group] of [...groupBy(rows.filter((r) => r.countdown <= 15 && !r.status), (r) => r.line)].sort()) {
    const deps = [...groupBy(group, (r) => `${r.tripKey}|${r.stopId}|${r.bay}|${r.planned}`).values()];
    const live = deps.filter((obs) => obs.some((r) => r.hasRealtime)).length;
    console.log(`  ${line.padEnd(4)} ${live} of ${deps.length}`);
}

// What happens at the end of a departure's life?
const lastPollOfStop = new Map(good.map((p) => [p.stopId, p.collectedAt])); // stops are polled in turn
const ended = [...departures.values()].filter((obs) => obs.at(-1).collectedAt < lastPollOfStop.get(obs[0].stopId) && obs.at(-1).countdown < 60);
const lastCountdowns = ended.map((obs) => obs.at(-1).countdown).sort((a, b) => a - b);
console.log(`\nDepartures that left the feed during the run: ${ended.length}`);
if (lastCountdowns.length) {
    console.log(`  countdown at last sighting: min ${lastCountdowns[0]}, median ${lastCountdowns[Math.floor(lastCountdowns.length / 2)]}, max ${lastCountdowns.at(-1)}`);
    const changes = ended.filter((obs) => new Set(obs.map((r) => r.delay)).size > 1).length;
    console.log(`  with a delay that changed while watched: ${changes}`);
}

const strange = rows.filter((r) => r.status || (r.delay !== null && Math.abs(r.delay) > 60) || r.delay < 0);
console.log('\nUnusual rows (status set, early, or more than 60 min late)');
for (const [status, group] of groupBy(strange, (r) => `${r.status ?? 'no status'} / ${r.tripStatus ?? '-'}`)) {
    const distinct = groupBy(group, (r) => `${r.line}|${r.avmsTripId}|${r.stopId}|${r.bay}|${r.planned}`);
    const delays = group.map((r) => r.delay).filter((d) => d !== null);
    console.log(`  ${status}: ${distinct.size} departures, delay ${Math.min(...delays)} to ${Math.max(...delays)}`);
    for (const obs of [...distinct.values()].slice(0, 4)) {
        const r = obs.at(-1);
        console.log(`    ${r.line} ${r.destination}, planned ${r.planned}, real ${r.realtimeTime}, delay ${r.delay}, trip ${r.avmsTripId}, tripKey ${r.tripKey}`);
    }
}
