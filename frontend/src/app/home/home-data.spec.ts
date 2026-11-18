import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Session } from '../core/session';
import { HomeDataService } from './home-data';

describe('HomeDataService', () => {
  it('shows the guest view for an admin without requesting student data', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(HomeDataService).load();
    http.expectOne('/api/session').flush({ username: 'test.admin', admin: true });

    expect(await result).toEqual({ kind: 'guest' });
    http.expectNone('/api/me/webuntis');
    http.expectNone('/api/me/routes');
    http.expectNone('/api/me/leave-by');
    // Keep the admin session so the Admin navigation remains available.
    expect(TestBed.inject(Session).me()?.admin).toBe(true);
    http.verify();
  });

  it('loads the leave-by time and keeps the rest if it fails', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(HomeDataService).load();
    http.expectOne('/api/session').flush({ username: 'max.muster', admin: false });
    await new Promise((r) => setTimeout(r));
    http.expectOne('/api/me/webuntis').flush({ connected: false });
    http.expectOne('/api/me/routes').flush([]);
    http.expectOne('/api/me/leave-by').flush(null, { status: 503, statusText: 'Service Unavailable' });

    const data = await result;
    expect(data.kind === 'student' && data.leaveBy).toBeNull();
    expect(data.kind === 'student' && data.routes).toEqual([]);
    http.verify();
  });
});
