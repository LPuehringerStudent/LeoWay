import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Session } from '../core/session';
import { HomeDataService } from './home-data';

describe('HomeDataService', () => {
  it('asks the user to reconnect when student endpoints return 403', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(HomeDataService).load();
    http.expectOne('/api/session').flush({ username: 'student', admin: false });
    await vi.waitFor(() => {
      http.expectOne('/api/me/webuntis').flush(null, { status: 403, statusText: 'Forbidden' });
      http.expectOne('/api/me/routes').flush(null, { status: 403, statusText: 'Forbidden' });
      http.expectOne('/api/me/leave-by').flush(null, { status: 403, statusText: 'Forbidden' });
    });
    expect(await result).toEqual({
      kind: 'student',
      username: 'student',
      webUntis: null,
      routes: null,
      leaveBy: null,
      loadedAt: expect.any(Date),
      reconnectRequired: true,
    });
    http.verify();
  });

  it('loads own timetable, routes and leave-by time for an admin', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(HomeDataService).load();
    http.expectOne('/api/session').flush({ username: 'test.admin', admin: true });
    await vi.waitFor(() => {
      http.expectOne('/api/me/webuntis').flush({ connected: true });
      http.expectOne('/api/me/routes').flush([]);
      http.expectOne('/api/me/leave-by').flush(null, { status: 404, statusText: 'Not Found' });
    });
    expect(await result).toEqual({
      kind: 'student',
      username: 'test.admin',
      webUntis: { connected: true },
      routes: [],
      leaveBy: null,
      loadedAt: expect.any(Date),
    });
    expect(TestBed.inject(Session).me()?.admin).toBe(true);
    http.verify();
  });

  it('loads the leave-by time and keeps the rest if it fails', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(HomeDataService).load();
    http.expectOne('/api/session').flush({ username: 'max.muster', admin: false });
    await new Promise((r) => setTimeout(r));
    http.expectOne('/api/me/webuntis').flush({ connected: false });
    http.expectOne('/api/me/routes').flush([]);
    http
      .expectOne('/api/me/leave-by')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });

    const data = await result;
    expect(data.kind === 'student' && data.leaveBy).toBeNull();
    expect(data.kind === 'student' && data.routes).toEqual([]);
    http.verify();
  });
});
