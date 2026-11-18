import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { App } from './app';
import { ApiHealth } from './core/api-health';
import { Session } from './core/session';

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
  });

});
