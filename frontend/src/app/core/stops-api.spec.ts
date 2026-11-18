import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Session } from './session';
import { Stop, StopsApi } from './stops-api';

describe('StopsApi', () => {
  it('resolves multiple routes one stop at a time and shares duplicate lookups', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: Session, useValue: { me: signal({ username: 'test' }) } }] });
    const api = TestBed.inject(StopsApi);
    const http = TestBed.inject(HttpTestingController);
    const results: (Stop | null)[] = [];
    for (const id of ['111', '222', '333', '444', '111']) api.resolve(id).subscribe(stop => results.push(stop));
    for (const id of ['111', '222', '333', '444']) {
      await vi.waitFor(() => {
        const requests = http.match(r => r.url.startsWith('/api/stops'));
        expect(requests).toHaveLength(1);
        expect(requests[0].request.url).toBe(`/api/stops?q=${id}`);
        requests[0].flush([{ id, name: `Resolved ${id}` }]);
      });
    }
    await vi.waitFor(() => expect(results).toHaveLength(5));
    expect(api.get('444')?.name).toBe('Resolved 444');
    http.verify();
  });

  it('continues the queue after a failed lookup', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: Session, useValue: { me: signal({ username: 'test' }) } }] });
    const api = TestBed.inject(StopsApi);
    const http = TestBed.inject(HttpTestingController);
    let next: Stop | null = null;
    api.resolve('111').subscribe();
    api.resolve('222').subscribe(stop => next = stop);
    await vi.waitFor(() => http.expectOne('/api/stops?q=111').flush(null, { status: 503, statusText: 'Unavailable' }));
    await vi.waitFor(() => http.expectOne('/api/stops?q=222').flush([{ id: '222', name: 'Next stop' }]));
    await vi.waitFor(() => expect(next).toEqual({ id: '222', name: 'Next stop' }));
    http.verify();
  });

  it('honours Retry-After and caches names without storing them in browser storage', async () => {
    vi.useFakeTimers();
    try {
      TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: Session, useValue: { me: signal({ username: 'test' }) } }] });
      const api = TestBed.inject(StopsApi);
      const http = TestBed.inject(HttpTestingController);
      let result: Stop[] = [];
      api.search('Auwiesen').subscribe(stops => result = stops);
      http.expectOne('/api/stops?q=Auwiesen').flush(null, { status: 429, statusText: 'Busy', headers: { 'Retry-After': '2' } });
      await vi.advanceTimersByTimeAsync(1999);
      http.expectNone('/api/stops?q=Auwiesen');
      await vi.advanceTimersByTimeAsync(1);
      http.expectOne('/api/stops?q=Auwiesen').flush([{ id: '1', name: 'Auwiesen' }]);
      expect(result[0].name).toBe('Auwiesen');
      api.search(' auwiesen ').subscribe();
      expect(api.get('1')?.name).toBe('Auwiesen');
      http.verify();
    } finally { vi.useRealTimers(); }
  });
});
