import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ApiHealth } from '../core/api-health';
import { HomePage } from './home-page';
import { HomeSummary, HomeSummaryService, MOCK_HOME_SUMMARY } from './home-summary';

function render(summary: HomeSummary, status = new Subject<'Ready' | 'Unavailable'>()) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ApiHealth, useValue: { status } },
      { provide: HomeSummaryService, useValue: { today: of(summary) } },
    ],
  });
  const fixture = TestBed.createComponent(HomePage);
  fixture.detectChanges();
  return { fixture, page: fixture.nativeElement as HTMLElement, status };
}

describe('HomePage', () => {
  it('shows checking, unavailability, and recovery', () => {
    const { fixture, page, status } = render(MOCK_HOME_SUMMARY);
    const statusText = () => {
      fixture.detectChanges();
      return page.querySelector('[role="status"]')?.textContent;
    };

    expect(statusText()).toBe('API status: Checking');
    status.next('Unavailable');
    expect(statusText()).toBe('API status: Unavailable');
    status.next('Ready');
    expect(statusText()).toBe('API status: Ready');
  });

  it('shows first lesson, leave-by time and the delay', () => {
    const { page } = render(MOCK_HOME_SUMMARY);
    const text = page.textContent ?? '';
    expect(text).toContain('08:55');
    expect(text).toContain('07:42');
    expect(text).toContain('Bus 19');
    expect(text).toContain('+6 min');
  });

  it('labels every time with its source (U03)', () => {
    const { page } = render(MOCK_HOME_SUMMARY);
    const labels = [...page.querySelectorAll('app-time-source')].map((l) => l.textContent?.trim());
    expect(labels).toEqual(['Timetable only', 'Prediction', 'Prediction']);
  });

  it('says so when the line has no live data', () => {
    const summary = { ...MOCK_HOME_SUMMARY, line: { ...MOCK_HOME_SUMMARY.line, delayMinutes: null } };
    expect(render(summary).page.textContent).toContain('no live data');
  });

  it('offers the four actions as links', () => {
    const links = [...render(MOCK_HOME_SUMMARY).page.querySelectorAll('nav a')];
    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/connections',
      '/disruptions',
      '/lateness',
      '/route',
    ]);
  });
});
