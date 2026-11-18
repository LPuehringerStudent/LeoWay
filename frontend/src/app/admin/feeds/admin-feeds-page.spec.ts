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
    expect(text()).toContain('All feeds operational');
    expect(text()).toContain('Linz AG');
    expect(text()).toContain('09:00:00');
    expect(text()).toContain('None recorded');
    expect(page.querySelector('caption')?.textContent).toContain('Europe/Vienna');
    expect(page.querySelector('.badge')?.textContent).toContain('OK');
    expect(page.querySelector('time[datetime="2026-11-18T08:00:00Z"]')).not.toBeNull();
  });

  it('refreshes every 30 seconds, manually, and stops when destroyed', async () => {
    const { fixture, page } = await render();
    expect(list).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(29_999);
    expect(list).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(list).toHaveBeenCalledTimes(2);
    (page.querySelector('button.refresh') as HTMLButtonElement).click();
    expect(list).toHaveBeenCalledTimes(3);
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(30_000);
    expect(list).toHaveBeenCalledTimes(3);
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

  it('keeps the last report on a network failure, then recovers', async () => {
    const { fixture, text } = await render();
    list.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    await vi.advanceTimersByTimeAsync(30_000);
    fixture.detectChanges();
    expect(text()).toContain('Showing previously loaded data');
    expect(text()).toContain('Linz AG');
    expect(text()).toContain('Status may be out of date');
    await vi.advanceTimersByTimeAsync(30_000);
    fixture.detectChanges();
    expect(text()).toContain('All feeds operational');
    expect(text()).not.toContain('Showing previously loaded data');
  });

  it('clears private feed data if access is revoked', async () => {
    const { fixture, text } = await render();
    list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await vi.advanceTimersByTimeAsync(30_000);
    fixture.detectChanges();
    expect(text()).toContain('Not allowed');
    expect(text()).not.toContain('Linz AG');
  });

  it('shows STALE and DOWN with text and historical error details', async () => {
    list.mockReturnValue(of([
      { ...feed, state: 'STALE', lastErrorAt: feed.lastSuccessAt, lastError: '<script>bad()</script>' },
      { ...feed, feedId: 'other', state: 'DOWN', lastSuccessAt: null },
    ]));
    const { page, text } = await render();
    expect(text()).toContain('2 feeds need attention');
    expect(text()).toContain('STALE');
    expect(text()).toContain('DOWN');
    expect(text()).toContain('Never received');
    expect(page.querySelector('details summary')?.textContent).toContain('Error details');
    expect(page.querySelector('script')).toBeNull();
    expect(text()).toContain('<script>bad()</script>');
  });

  it('distinguishes an empty report from a failed initial request', async () => {
    list.mockReturnValue(of([]));
    const { fixture, text } = await render();
    expect(text()).toContain('No feeds reporting yet');
    list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await vi.advanceTimersByTimeAsync(30_000);
    fixture.detectChanges();
    expect(text()).toContain('Couldn’t refresh');
  });

  it('offers retry after an initial failure', async () => {
    list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { page, text } = await render();
    expect(text()).toContain('Couldn’t load feed status');
    expect((page.querySelector('button.refresh') as HTMLButtonElement).disabled).toBe(false);
  });

  it('prevents overlapping requests', async () => {
    list.mockReturnValue(new Subject<AdminFeed[]>());
    const { page } = await render();
    expect((page.querySelector('button.refresh') as HTMLButtonElement).disabled).toBe(true);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(list).toHaveBeenCalledTimes(1);
  });
});
