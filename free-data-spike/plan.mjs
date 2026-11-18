// Plans the #89 routes plus routes from outside Linz with a local MOTIS (#93).
// Usage: node plan.mjs [YYYY-MM-DD] [HH:MM]   (default: next weekday, arrive by 07:50 Vienna time)
// Needs Node 18+, MOTIS on http://localhost:8080 (run.sh). Stop search uses MOTIS' own geocoder (no VAO, no Linz AG).
const MOTIS = process.env.MOTIS ?? 'http://localhost:8080';

const ORIGINS = ['Linz Hauptbahnhof', 'Linz Auwiesen', 'Leonding Doblerholz', 'Wels Hauptbahnhof', 'Enns Bahnhof', 'Freistadt Bahnhof'];
const SCHOOL = ['Leonding Limesstraße', 'Leonding Meixnerkreuzung'];

async function get(path, params) {
  const started = performance.now();
  const res = await fetch(`${MOTIS}${path}?${new URLSearchParams(params)}`);
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status} ${await res.text()}`);
  return { body: await res.json(), ms: Math.round(performance.now() - started) };
}

async function stop(name) {
  const { body, ms } = await get('/api/v1/geocode', { text: name, type: 'STOP' });
  const s = body[0];
  if (!s) throw new Error(`no stop for "${name}"`);
  console.log(`  ${name.padEnd(26)} -> ${s.name} (${s.id}) ${ms} ms`);
  return s;
}

function nextWeekday() {
  const d = new Date(Date.now() + 86_400_000);
  while ([0, 6].includes(d.getDay())) d.setDate(d.getDate() + 1);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Vienna' }).format(d);
}

const hhmm = (iso) => new Date(iso).toLocaleTimeString('de-AT', { timeZone: 'Europe/Vienna', hour: '2-digit', minute: '2-digit' });

const [date = nextWeekday(), time = '07:50'] = process.argv.slice(2);
// Vienna is UTC+1 in winter; good enough for a spike run between November and March.
const arriveBy = new Date(`${date}T${time}:00+01:00`).toISOString();

console.log('Stop search');
const origins = [];
for (const name of ORIGINS) origins.push(await stop(name));
const schools = [];
for (const name of SCHOOL) schools.push(await stop(name));

for (const from of origins) for (const to of schools) {
  const { body, ms } = await get('/api/v6/plan', {
    fromPlace: from.id, toPlace: to.id, time: arriveBy, arriveBy: 'true', numItineraries: '3',
  });
  console.log(`\n${from.name} -> ${to.name}, arrive by ${time} on ${date}: ${body.itineraries.length} trip(s), ${ms} ms`);
  for (const it of body.itineraries) {
    const rides = it.legs.filter((l) => l.mode !== 'WALK');
    console.log(`  ${hhmm(it.startTime)} -> ${hhmm(it.endTime)}  ${Math.round(it.duration / 60)} min  ${it.transfers} change(s)  `
      + rides.map((l) => `${l.routeShortName ?? l.mode} (${l.agencyName ?? '?'})`).join(' > '));
  }
}
