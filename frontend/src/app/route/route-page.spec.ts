import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Component, input } from '@angular/core';
import { provideRouter } from '@angular/router';
import { Session } from '../core/session';
import { RoutePage } from './route-page';
import { RouteMap } from './route-map';
import { Stop } from '../core/stops-api';

@Component({ selector: 'app-route-map', template: '' })
class MapStub {
  readonly home = input<Stop | null>(null);
  readonly school = input<Stop | null>(null);
}

describe('RoutePage', () => {
  it('shows a reconnect message after a 403 from private endpoints', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.overrideComponent(RoutePage, {
      remove: { imports: [RouteMap] },
      add: { imports: [MapStub] },
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RoutePage);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ username: 'student', admin: false });
    await vi.waitFor(() => {
      http.expectOne('/api/me/webuntis').flush(null, { status: 403, statusText: 'Forbidden' });
      http.expectOne('/api/me/routes').flush(null, { status: 403, statusText: 'Forbidden' });
    });
    const page = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.textContent).toContain('Please log out and reconnect WebUntis');
    });
    expect(page.querySelector('a[href="/connect"]')).not.toBeNull();
    expect(page.textContent).not.toContain('could not be loaded');
    http.verify();
  });

  it('shows the admin link without requesting student data', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.overrideComponent(RoutePage, {
      remove: { imports: [RouteMap] },
      add: { imports: [MapStub] },
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RoutePage);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ username: 'test.admin', admin: true });
    const page = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.textContent).toContain('signed in as an administrator');
    });
    expect(page.querySelector('a[href="/admin/feeds"]')).not.toBeNull();
    http.expectNone('/api/me/webuntis');
    http.expectNone('/api/me/routes');
    expect(page.textContent).not.toContain('Loading…');
    expect(TestBed.inject(Session).me()?.admin).toBe(true);
    http.verify();
  });

  it('confirms deletion, keeps a route after failure, then removes it on success', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.overrideComponent(RoutePage, {
      remove: { imports: [RouteMap] },
      add: { imports: [MapStub] },
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RoutePage);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ username: 'student', admin: false });
    await vi.waitFor(() => {
      http
        .expectOne('/api/me/webuntis')
        .flush({ connected: true, username: 'student', available: true });
      http
        .expectOne('/api/me/routes')
        .flush([
          {
            id: 1,
            homeStopId: '111',
            schoolStopId: '222',
            walkToStopMinutes: 4,
            walkToSchoolMinutes: 6,
            bufferMinutes: 10,
          },
        ]);
    });
    await vi.waitFor(() => {
      http.expectOne('/api/stops?q=111').flush([{ id: '111', name: 'Home stop' }]);
    });
    await vi.waitFor(() => {
      http.expectOne('/api/stops?q=222').flush([{ id: '222', name: 'School stop' }]);
    });
    const page = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.querySelector('.delete-route')).not.toBeNull();
    });
    (page.querySelector('.delete-route') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectNone('/api/me/routes/1');
    (page.querySelector('.confirm-delete') as HTMLButtonElement).click();
    http.expectOne('/api/me/routes/1').flush(null, { status: 503, statusText: 'Unavailable' });
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.textContent).toContain('could not be deleted');
    });
    expect(page.querySelectorAll('.saved-route').length).toBe(1);
    (page.querySelector('.confirm-delete') as HTMLButtonElement).click();
    const deletion = http.expectOne('/api/me/routes/1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.textContent).toContain('Route deleted');
    });
    expect(page.querySelectorAll('.saved-route').length).toBe(0);
    http.verify();
  });
});
