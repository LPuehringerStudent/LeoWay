import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { App } from './app';
import { ApiHealth } from './core/api-health';
import { Session } from './core/session';
import { ConnectPage } from './connect/connect-page';

@Component({ template: '<h1>Admin panel</h1>' })
class AdminStub {}

describe('Admin navigation', () => {
  it.each([true, false, null])('shows Admin between Home and Route only for an admin (%s)', admin => {
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: ApiHealth, useValue: { status: of('Ready') } },
      { provide: Session, useValue: { me: signal(admin === null ? null : { username: 'test', admin }), refresh: vi.fn() } },
    ] });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map(a => a.textContent?.trim());
    expect(links).toEqual(admin ? ['Home', 'Admin', 'Route', 'Privacy'] : ['Home', 'Route', 'Privacy']);
    expect(!!(fixture.nativeElement as HTMLElement).querySelector('button.logout')).toBe(admin !== null);
  });

  it('lets an administrator log out from the header and returns to WebUntis login', async () => {
    const me = signal<{ username: string; admin: boolean } | null>({ username: 'admin', admin: true });
    let finish!: () => void;
    const logout = vi.fn(() => new Promise<void>(resolve => { finish = () => { me.set(null); resolve(); }; }));
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: ApiHealth, useValue: { status: of('Ready') } },
      { provide: Session, useValue: { me, refresh: vi.fn(), logout } },
    ] });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const button = page.querySelector('button.logout') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Logging out');
    expect(logout).toHaveBeenCalledTimes(1);
    finish();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith('/connect', { replaceUrl: true, state: { returningUser: true } });
    expect(page.querySelector('button.logout')).toBeNull();
    expect(page.querySelector('a[href="/admin/feeds"]')).toBeNull();
  });

  it('renders the login form immediately after logout using actual router navigation', async () => {
    const me = signal<{ username: string; admin: boolean } | null>({ username: 'admin', admin: true });
    TestBed.configureTestingModule({ providers: [
      provideRouter([{ path: 'admin/feeds', component: AdminStub }, { path: 'connect', component: ConnectPage }]),
      { provide: ApiHealth, useValue: { status: of('Ready') } },
      { provide: Session, useValue: { me, refresh: vi.fn(), logout: async () => { me.set(null); } } },
    ] });
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await router.navigateByUrl('/admin/feeds');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    (page.querySelector('button.logout') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(router.url).toBe('/connect');
    expect(page.querySelector('input#username')).not.toBeNull();
    expect(page.querySelector('input#key')).not.toBeNull();
    expect(page.querySelector('app-admin-feeds-page')).toBeNull();
    expect(page.querySelector('button.logout')).toBeNull();
  });
});
