import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Session } from './session';
import { Stop, StopsApi } from './stops-api';

describe('StopsApi', () => {
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
