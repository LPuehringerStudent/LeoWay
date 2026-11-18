import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { FeedHealth, FeedState } from '../core/feed-health';
import { LeaveByResult } from '../core/leave-by-api';
import { SavedRoute } from '../core/routes-api';
import { WebUntisStatus } from '../core/webuntis-api';
import { HomePage, REFRESH_MS } from './home-page';
import { dayLabel, HomeData, HomeDataService, keepLastLeaveBy } from './home-data';

const lesson = (over: Partial<WebUntisStatus> = {}): WebUntisStatus => ({
  connected: true,
  username: 'max.muster',
  available: true,
  firstLesson: { date: '2026-11-16', start: '08:00:00', subject: 'Mathematics' },
  needsReconnect: false,
  syncedAt: '2026-11-15T19:00:00Z',
  ...over,
});
const ROUTE: SavedRoute = { id: 1, homeStopId: 'a', schoolStopId: 'b', walkToStopMinutes: 7, walkToSchoolMinutes: 3, bufferMinutes: 5 };
const nothing = (status: LeaveByResult['status']): LeaveByResult => ({
  status, routeId: null, lessonStart: null, leaveAt: null, atSchool: null, connection: null, source: null, minutesLate: null,
});
/** The A02 standard case from LeaveByResourceTest: lesson 08:00, tram 3 > bus 11 at 07:24, leave 07:17. */
const leave = (over: Partial<LeaveByResult> = {}): LeaveByResult => ({
  status: 'IN_TIME',
  routeId: 1,
  lessonStart: '2026-11-16T08:00:00+01:00',
  leaveAt: '2026-11-16T07:17:00+01:00',
  atSchool: '2026-11-16T07:39:00+01:00',
  connection: {
    line: '3 > 11',
    plannedDeparture: '2026-11-16T07:24:00+01:00',
    departure: '2026-11-16T07:24:00+01:00',
    plannedArrival: '2026-11-16T07:36:00+01:00',
    arrival: '2026-11-16T07:36:00+01:00',
  },
  source: 'TIMETABLE',
  minutesLate: null,
  ...over,
});
const student = (
  webUntis: WebUntisStatus | null,
  routes: SavedRoute[] | null = [],
  leaveBy: LeaveByResult | null = nothing('NO_ROUTE'),
): HomeData => ({
  kind: 'student',
  username: 'max.muster',
  webUntis,
  routes,
  leaveBy,
  loadedAt: new Date(2026, 10, 15, 20, 5),
});

async function render(data: HomeData) {
  const feed = new Subject<FeedState>();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: HomeDataService, useValue: { load: () => Promise.resolve(data) } },
      { provide: FeedHealth, useValue: { linzAg: feed } },
    ],
  });
  const fixture = TestBed.createComponent(HomePage);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const page = fixture.nativeElement as HTMLElement;
  return { fixture, page, feed, text: () => page.textContent?.replace(/\s+/g, ' ') ?? '' };
}

describe('HomePage', () => {
  it('invites a guest to connect WebUntis and shows no times', async () => {
    const { page, text } = await render({ kind: 'guest' });
    expect(text()).toContain('Know when to leave for school');
    expect(page.querySelector('a.btn')?.getAttribute('href')).toBe('/connect');
    expect(page.querySelector('time')).toBeNull();
  });

  it('shows the real first lesson with its source (U03)', async () => {
    const { page, text } = await render(student(lesson()));
    expect(page.querySelector('time')?.textContent).toBe('08:00');
    expect(text()).toContain('Mathematics');
    expect(page.querySelector('app-time-source')?.textContent?.trim()).toBe('Timetable only');
  });

  it('asks for a route when there is none', async () => {
    const { text, page } = await render(student(lesson()));
    expect(page.querySelector('.board__pending')?.textContent?.trim()).toBe('Needs a saved route');
    expect(page.querySelector('.board__leave a')?.getAttribute('href')).toBe('/route');
    expect(page.querySelectorAll('time').length).toBe(1);
  });

  it('shows the leave-by time, the connection and the source (A02, U03)', async () => {
    const { text, page } = await render(student(lesson(), [ROUTE], leave()));
    expect(page.querySelector('.digits--leave')?.textContent).toBe('07:17');
    expect(page.querySelector('.board__ride')?.textContent?.trim()).toBe('Line 3 > 11 at 07:24, at school 07:39');
    expect(text()).not.toContain('planned');
    const labels = [...page.querySelectorAll('app-time-source')].map((e) => e.textContent?.trim());
    expect(labels).toEqual(['Timetable only', 'Timetable only']);
    expect(text()).toContain('No live data yet, updated 20:05');
    expect(text()).toContain('7 min to the stop, 3 min from the stop to school, 5 min buffer');
  });

  it('marks live times as prediction and shows the planned time', async () => {
    const live = leave({
      source: 'PREDICTION',
      leaveAt: '2026-11-16T07:20:00+01:00',
      connection: { ...leave().connection!, departure: '2026-11-16T07:27:00+01:00' },
    });
    const { text, page } = await render(student(lesson(), [ROUTE], live));
    expect(page.querySelector('.digits--leave')?.textContent).toBe('07:20');
    expect(page.querySelectorAll('app-time-source')[1]?.textContent?.trim()).toBe('Prediction');
    expect(page.querySelector('.board__ride')?.textContent?.trim()).toBe('Line 3 > 11 at 07:27 (planned 07:24), at school 07:39');
    expect(text()).not.toContain('No live data');
  });

  it('says when there is no buffer left or the student will be late', async () => {
    expect((await render(student(lesson(), [ROUTE], leave({ status: 'NO_BUFFER' })))).text())
      .toContain('No buffer left');
    TestBed.resetTestingModule();
    const { text, page } = await render(student(lesson(), [ROUTE], leave({ status: 'LATE', minutesLate: 4 })));
    expect(text()).toContain('4 min late');
    expect(page.querySelector('.board__note--late')).not.toBeNull();
  });

  it('shows a delay warning with the exam note and the alternative (F03, A03)', async () => {
    const warned = leave({
      warning: {
        line: '19',
        plannedDeparture: '2026-11-16T07:35:00+01:00',
        delayMinutes: 6,
        atSchool: '2026-11-16T08:03:00+01:00',
        minutesLate: 3,
        exam: true,
        alternative: {
          line: '3',
          departure: '2026-11-16T07:29:00+01:00',
          leaveAt: '2026-11-16T07:24:00+01:00',
          atSchool: '2026-11-16T07:49:00+01:00',
          inTime: true,
        },
      },
    });
    const { page } = await render(student(lesson(), [ROUTE], warned));
    const warning = page.querySelector('.board__warning')?.textContent?.replace(/\s+/g, ' ').trim();
    expect(warning).toContain('Line 19 at 07:35 is 6 min late: at school 08:03, 3 min late');
    expect(warning).toContain('Exam in your first lesson');
    expect(warning).toContain('Take line 3 at 07:29 instead, leave home at 07:24');
  });

  it('says when no connection helps and shows nothing without a warning', async () => {
    const base = leave().warning;
    expect(base).toBeUndefined();
    expect((await render(student(lesson(), [ROUTE], leave()))).page.querySelector('.board__warning')).toBeNull();
    TestBed.resetTestingModule();
    const warned = leave({
      warning: {
        line: '19', plannedDeparture: '2026-11-16T07:35:00+01:00', delayMinutes: 6,
        atSchool: '2026-11-16T08:03:00+01:00', minutesLate: 3, exam: false, alternative: null,
      },
    });
    const { page } = await render(student(lesson(), [ROUTE], warned));
    const warning = page.querySelector('.board__warning')?.textContent ?? '';
    expect(warning).toContain('No other connection gets you there earlier');
    expect(warning).not.toContain('Exam');
  });

  it('never shows a made-up leave-by time', async () => {
    expect((await render(student(lesson(), [ROUTE], null))).page.querySelector('.board__pending')?.textContent?.trim())
      .toBe('Could not be calculated right now');
    TestBed.resetTestingModule();
    const { text, page } = await render(student(lesson(), [ROUTE], nothing('NO_CONNECTION')));
    expect(page.querySelector('.board__pending')?.textContent?.trim()).toBe('No connection found');
    expect(page.querySelectorAll('time').length).toBe(1);
  });

  it('explains a missing first lesson', async () => {
    expect((await render(student(lesson({ connected: false, username: null, firstLesson: null })))).text())
      .toContain('WebUntis is not connected');
    TestBed.resetTestingModule();
    expect((await render(student(lesson({ available: false, firstLesson: null })))).text())
      .toContain("WebUntis can't be reached right now");
    TestBed.resetTestingModule();
    expect((await render(student(lesson({ needsReconnect: true, firstLesson: null })))).text())
      .toContain('WebUntis no longer accepts your access key');
    TestBed.resetTestingModule();
    expect((await render(student(lesson({ firstLesson: null })))).text()).toContain('No lessons found');
    TestBed.resetTestingModule();
    expect((await render(student(null))).text()).toContain('could not be loaded');
  });

  it('shows the live feed state', async () => {
    const { fixture, page, feed } = await render({ kind: 'guest' });
    const status = () => {
      fixture.detectChanges();
      return page.querySelector('.feed')?.textContent?.trim();
    };
    expect(status()).toBe('Checking');
    feed.next('OK');
    expect(status()).toBe('Working');
    feed.next('STALE');
    expect(status()).toBe('No update for over five minutes');
    feed.next('OFFLINE');
    expect(status()).toBe('LeoWay server not reachable');
  });
});

describe('HomePage refresh', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('reloads every minute while open and stops when closed', async () => {
    const load = vi.fn(() => Promise.resolve<HomeData>({ kind: 'guest' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: HomeDataService, useValue: { load } },
        { provide: FeedHealth, useValue: { linzAg: new Subject<FeedState>() } },
      ],
    });
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    expect(load).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(REFRESH_MS);
    expect(load).toHaveBeenCalledTimes(2);
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(3 * REFRESH_MS);
    expect(load).toHaveBeenCalledTimes(2);
  });
});

describe('keepLastLeaveBy', () => {
  const before = new Date('2026-11-16T07:00:00+01:00');
  const good = student(lesson(), [ROUTE], leave());
  const failed = { ...student(lesson(), [ROUTE], null), loadedAt: new Date(2026, 10, 16, 7, 1) };

  it('keeps the last result while its lesson is ahead, marked as outdated', () => {
    const kept = keepLastLeaveBy(good, failed, before);
    expect(kept.kind === 'student' && kept.leaveBy).toEqual(leave());
    expect(kept.kind === 'student' && kept.outdated).toBe(true);
    expect(kept.kind === 'student' && kept.loadedAt).toEqual(new Date(2026, 10, 15, 20, 5));
  });

  it('lets a new answer win and drops a result whose lesson has started', () => {
    const noRoute = student(lesson(), [], nothing('NO_ROUTE'));
    expect(keepLastLeaveBy(good, noRoute, before)).toBe(noRoute);
    expect(keepLastLeaveBy(good, failed, new Date('2026-11-16T08:00:00+01:00'))).toBe(failed);
    expect(keepLastLeaveBy(null, failed, before)).toBe(failed);
  });

  it('says on Home that the time could not be updated', async () => {
    const { text, page } = await render(keepLastLeaveBy(good, failed, before));
    expect(page.querySelector('.digits--leave')?.textContent).toBe('07:17');
    expect(text()).toContain("Can't update right now, last update 20:05");
    expect(text()).not.toContain('No live data yet');
  });
});

describe('dayLabel', () => {
  const today = new Date(2026, 10, 16, 9);
  it('names today, tomorrow and other days', () => {
    expect(dayLabel('2026-11-16', today)).toEqual({ day: 'Today', date: 'Mon 16 Nov' });
    expect(dayLabel('2026-11-17', today).day).toBe('Tomorrow');
    expect(dayLabel('2026-11-19', today).day).toBe('Thursday');
  });
});
