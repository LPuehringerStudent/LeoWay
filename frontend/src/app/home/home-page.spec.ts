import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { FeedHealth, FeedState } from '../core/feed-health';
import { SavedRoute } from '../core/routes-api';
import { WebUntisStatus } from '../core/webuntis-api';
import { HomePage } from './home-page';
import { dayLabel, HomeData, HomeDataService } from './home-data';

const lesson = (over: Partial<WebUntisStatus> = {}): WebUntisStatus => ({
  connected: true,
  username: 'max.muster',
  available: true,
  firstLesson: { date: '2026-11-16', start: '08:00:00', subject: 'Mathematics' },
  needsReconnect: false,
  syncedAt: '2026-11-15T19:00:00Z',
  ...over,
});
const student = (webUntis: WebUntisStatus | null, routes: SavedRoute[] | null = []): HomeData => ({
  kind: 'student',
  username: 'max.muster',
  webUntis,
  routes,
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

  it('never shows a made-up leave-by time', async () => {
    expect((await render(student(lesson()))).text()).toContain('Leave by Needs a saved route');
    TestBed.resetTestingModule();
    const route = { id: 1, homeStopId: 'a', schoolStopId: 'b', walkToStopMinutes: 4, walkToSchoolMinutes: 3, bufferMinutes: 5 };
    const { text, page } = await render(student(lesson(), [route]));
    expect(text()).toContain('Leave by Not calculated yet');
    expect(text()).toContain('4 min to the stop, 3 min from the stop to school, 5 min buffer');
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

describe('dayLabel', () => {
  const today = new Date(2026, 10, 16, 9);
  it('names today, tomorrow and other days', () => {
    expect(dayLabel('2026-11-16', today)).toEqual({ day: 'Today', date: 'Mon 16 Nov' });
    expect(dayLabel('2026-11-17', today).day).toBe('Tomorrow');
    expect(dayLabel('2026-11-19', today).day).toBe('Thursday');
  });
});
