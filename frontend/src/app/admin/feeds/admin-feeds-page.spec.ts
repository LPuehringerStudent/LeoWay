import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AdminFeed, AdminFeedsApi } from '../../core/admin-feeds-api';
import { AdminFeedsPage } from './admin-feeds-page';

const feed: AdminFeed = { feedId: 'linz-ag', state: 'OK', lastSuccessAt: '2026-11-18T08:00:00Z', lastErrorAt: null, lastError: null };

describe('AdminFeedsPage', () => {
  const list = vi.fn();
  beforeEach(() => {
    vi.useFakeTimers();
    list.mockReset().mockReturnValue(of([feed]));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AdminFeedsApi, useValue: { list } }] });
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  async function render() {
    const fixture = TestBed.createComponent(AdminFeedsPage);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    return { fixture, page, text: () => page.textContent?.replace(/\s+/g, ' ') ?? '' };
  }

  it('shows a semantic feed table, explicit status text and Vienna timestamps', async () => {
    const { page, text } = await render();
    expect(text()).toContain('Linz AG');
    expect(text()).toContain('09:00:00');
    expect(text()).toContain('None recorded');
    expect(page.querySelector('caption')?.textContent).toContain('Europe/Vienna');
    expect(page.querySelector('.badge')?.textContent).toContain('OK');
    expect(page.querySelector('time[datetime="2026-11-18T08:00:00Z"]')).not.toBeNull();
  });

  it.each([401, 403])('shows Not allowed for HTTP %s and stops polling', async status => {
    list.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
    const { page, text } = await render();
    expect(text()).toContain('Not allowed');
    expect(page.querySelector('table')).toBeNull();
    expect(!!page.querySelector('a[href="/connect"]')).toBe(status === 401);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(list).toHaveBeenCalledTimes(1);
  });

});
