import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ApiHealth } from '../core/api-health';
import { HomePage } from './home-page';

describe('HomePage', () => {
  it('shows checking, unavailability, and recovery', () => {
    const status = new Subject<'Ready' | 'Unavailable'>();
    TestBed.configureTestingModule({
      providers: [{ provide: ApiHealth, useValue: { status } }],
    });

    const fixture = TestBed.createComponent(HomePage);
    const page: HTMLElement = fixture.nativeElement;
    const statusText = () => {
      fixture.detectChanges();
      return page.querySelector('[role="status"]')?.textContent;
    };

    expect(statusText()).toBe('API status: Checking');
    status.next('Unavailable');
    expect(statusText()).toBe('API status: Unavailable');
    status.next('Ready');
    expect(statusText()).toBe('API status: Ready');
  });
});
