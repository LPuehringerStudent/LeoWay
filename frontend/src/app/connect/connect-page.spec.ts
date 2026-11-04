import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Session } from '../core/session';
import { ConnectPage } from './connect-page';

describe('ConnectPage', () => {
  const login = vi.fn();

  function render() {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: Session, useValue: { login } }],
    });
    const fixture = TestBed.createComponent(ConnectPage);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  async function fillAndSubmit(fixture: ReturnType<typeof render>['fixture'], page: HTMLElement) {
    (page.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const user = page.querySelector('#username') as HTMLInputElement;
    const key = page.querySelector('#key') as HTMLInputElement;
    user.value = 'max.muster';
    user.dispatchEvent(new Event('input'));
    key.value = 'KEY123';
    key.dispatchEvent(new Event('input'));
    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  beforeEach(() => login.mockReset());

  it('shows the privacy information before the form (L01)', () => {
    const { page } = render();
    expect(page.textContent).toContain('What LeoWay never stores');
    expect(page.querySelector('form')).toBeNull();
  });

  it('masks the access key', async () => {
    const { fixture, page } = render();
    (page.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((page.querySelector('#key') as HTMLInputElement).type).toBe('password');
  });

  it('shows "access key not accepted" with help on a wrong key', async () => {
    login.mockResolvedValue('rejected');
    const { fixture, page } = render();
    await fillAndSubmit(fixture, page);
    expect(login).toHaveBeenCalledWith('max.muster', 'KEY123');
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('access key not accepted');
    expect(page.textContent).toContain('How to create a key');
  });

  it('says try again later when WebUntis is down', async () => {
    login.mockResolvedValue('unavailable');
    const { fixture, page } = render();
    await fillAndSubmit(fixture, page);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('try again later');
  });

  it('goes to Route and alerts after connecting', async () => {
    login.mockResolvedValue('ok');
    const { fixture, page } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fillAndSubmit(fixture, page);
    expect(navigate).toHaveBeenCalledWith('/route');
  });
});
