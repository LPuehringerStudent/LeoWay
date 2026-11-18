import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Session } from '../core/session';
import { RoutePage } from './route-page';

describe('RoutePage', () => {
  it('shows an admin link without requesting student WebUntis data', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RoutePage);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ username: 'test.admin', admin: true });
    const page = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(page.textContent).toContain('signed in as an administrator');
    });
    expect(page.querySelector('a[href="/admin/feeds"]')?.textContent).toContain('Open admin panel');
    expect(page.querySelector('a[href="/connect"]')).toBeNull();
    expect(page.textContent).not.toContain('Connected as');
    expect(page.textContent).not.toContain('Loading…');
    http.expectNone('/api/me/webuntis');
    http.expectNone('/api/me/alerts');
    expect(TestBed.inject(Session).me()?.admin).toBe(true);
    http.verify();
  });
});
