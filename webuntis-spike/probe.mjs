// Feasibility probe: can LeoWay get what it needs from HTL Leonding's WebUntis?
// Usage: node probe.mjs [YYYY-MM-DD]   (credentials from .env: UNTIS_USER, UNTIS_PASS)
import 'dotenv/config';
import { WebUntis, WebUntisSecretAuth } from 'webuntis';
import { authenticator } from 'otplib';
import { writeFileSync } from 'node:fs';

const SCHOOL = 'htl-leonding';
const SERVER = 'htl-leonding.webuntis.com';
const day = process.argv[2] ? new Date(process.argv[2]) : new Date();

const monday = new Date(day);
monday.setDate(day.getDate() - ((day.getDay() + 6) % 7));
const friday = new Date(monday);
friday.setDate(monday.getDate() + 4);

// The access key ("Schlüssel") generates one-time codes, so the password is never needed
const untis = process.env.UNTIS_SECRET
    ? new WebUntisSecretAuth(SCHOOL, process.env.UNTIS_USER, process.env.UNTIS_SECRET, SERVER, 'LeoWay', authenticator)
    : new WebUntis(SCHOOL, process.env.UNTIS_USER, process.env.UNTIS_PASS, SERVER);
const results = [];
const dump = {};

async function check(name, fn) {
    const t0 = Date.now();
    try {
        const value = await fn();
        dump[name] = value;
        const summary = Array.isArray(value) ? `${value.length} items` : typeof value === 'object' ? 'ok' : String(value);
        results.push({ name, ok: true, ms: Date.now() - t0, summary });
        return value;
    } catch (e) {
        results.push({ name, ok: false, ms: Date.now() - t0, summary: e.message.slice(0, 120) });
        return null;
    }
}

const hhmm = (n) => `${String(Math.floor(n / 100)).padStart(2, '0')}:${String(n % 100).padStart(2, '0')}`;

// Reuses a browser session: the JSESSIONID gives a JWT, and the REST app data tells us who we are
async function adoptBrowserSession(sessionId) {
    untis.sessionInformation = { sessionId };
    const jwt = await untis._getJWT(false);
    const res = await fetch(`https://${SERVER}/WebUntis/api/rest/view/v1/app/data`, {
        headers: { Authorization: `Bearer ${jwt}`, Cookie: untis._buildCookies() },
    });
    if (!res.ok) throw new Error(`app/data returned ${res.status}`);
    const { user } = await res.json();
    untis.sessionInformation = {
        sessionId,
        jwt_token: jwt,
        personId: user.person.id,
        personType: 5, // student
        klasseId: user.students?.[0]?.klasseId,
    };
    return `session ok, person ${user.person.id}`;
}

if (process.env.UNTIS_SESSION_ID) {
    await check('login (browser session)', () => adoptBrowserSession(process.env.UNTIS_SESSION_ID));
} else if (process.env.UNTIS_SECRET) {
    await check('login (access key)', () => untis.login().then(() => 'session ok'));
} else {
    await check('login (JSON-RPC)', () => untis.login().then(() => 'session ok'));
}
await check('current school year', () => untis.getCurrentSchoolyear().then((y) => y.name));
await check('timegrid', () => untis.getTimegrid());
await check('holidays', () => untis.getHolidays());
const week = await check('own timetable (week, JSON-RPC)', () => untis.getOwnTimetableForRange(monday, friday));
const webWeek = await check('own timetable (week, web API)', () => untis.getOwnTimetableForWeek(monday));
await check('own class timetable (week)', () => untis.getOwnClassTimetableForRange(monday, friday));
await check('exams (week)', () => untis.getExamsForRange(monday, friday));
await check('JWT for REST API', () => untis._getJWT().then((t) => `${t.slice(0, 12)}...`));
await check('session still valid', () => untis.validateSession());

console.table(results);

// What LeoWay actually needs: the first real lesson per day, with cancellations
if (week) {
    console.log(`\nFirst lesson per day, ${monday.toDateString()} - ${friday.toDateString()}:`);
    const byDate = Object.groupBy(week, (l) => l.date);
    for (const date of Object.keys(byDate).sort()) {
        const lessons = byDate[date].sort((a, b) => a.startTime - b.startTime);
        const first = lessons.find((l) => l.code !== 'cancelled');
        const cancelled = lessons.filter((l) => l.code === 'cancelled').length;
        const substituted = lessons.filter((l) => l.substText || l.te?.some((t) => t.orgid)).length;
        console.log(
            `  ${date}: first ${first ? hhmm(first.startTime) + ' ' + (first.su?.[0]?.name ?? '?') : 'none (all cancelled)'}` +
                `  | ${lessons.length} lessons, ${cancelled} cancelled, ${substituted} substituted`,
        );
    }
    const codes = Object.groupBy(week, (l) => l.code ?? '(regular)');
    console.log('\nLesson codes seen:', Object.fromEntries(Object.entries(codes).map(([k, v]) => [k, v.length])));
}

// Logging out a borrowed browser session would also log the browser out
if (!process.env.UNTIS_SESSION_ID) await untis.logout().catch(() => {});
writeFileSync('probe-output.json', JSON.stringify(dump, null, 2));
console.log('\nRaw responses written to probe-output.json');
