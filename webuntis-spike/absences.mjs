// Probe: can we read the student's absences and lateness via WebUntis?
// Usage: node absences.mjs [from YYYY-MM-DD] [to YYYY-MM-DD]   (defaults to the current school year)
import 'dotenv/config';
import { WebUntisSecretAuth } from 'webuntis';
import { authenticator } from 'otplib';

const untis = new WebUntisSecretAuth(
    'htl-leonding', process.env.UNTIS_USER, process.env.UNTIS_SECRET, 'htl-leonding.webuntis.com', 'LeoWay', authenticator,
);

await untis.login();
const year = await untis.getCurrentSchoolyear();
const from = process.argv[2] ? new Date(process.argv[2]) : year.startDate;
const to = process.argv[3] ? new Date(process.argv[3]) : new Date();
console.log(`Absences ${from.toDateString()} - ${to.toDateString()}`);

const { absences } = await untis.getAbsentLesson(from, to);
console.log(`${absences.length} entries`);
if (absences.length) console.log('Fields:', Object.keys(absences[0]).join(', '));

const hhmm = (n) => `${String(Math.floor(n / 100)).padStart(2, '0')}:${String(n % 100).padStart(2, '0')}`;
for (const a of absences) {
    console.log(
        `  ${a.startDate} ${hhmm(a.startTime)}-${hhmm(a.endTime)}  ${a.reason || '(no reason)'}` +
            `  excused: ${a.isExcused}  excuse: ${a.excuse?.excuseStatus || '-'}  text: ${a.text || '-'}`,
    );
}

const pdf = await untis.getPdfOfAbsentLesson(from, to).catch((e) => `failed: ${e.message}`);
console.log('PDF export:', typeof pdf === 'string' ? pdf.slice(0, 120) : pdf);

await untis.logout().catch(() => {});
