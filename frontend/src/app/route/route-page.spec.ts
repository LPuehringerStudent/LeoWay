import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Session } from '../core/session';
import { RoutePage } from './route-page';

describe('RoutePage', () => {
  it('shows an admin’s own WebUntis connection like a student', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RoutePage);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ username: 'test.admin', admin: true });
    await vi.waitFor(() => {
      http.expectOne('/api/me/webuntis').flush({ connected: true, username: 'test.admin', available: true });
    });
    const page = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.textContent).toContain('Connected as test.admin');
    });
    expect(page.querySelector('a[href="/connect"]')).toBeNull();
    expect(page.textContent).not.toContain('Loading…');
    expect(TestBed.inject(Session).me()?.admin).toBe(true);
    http.verify();
  });
});
