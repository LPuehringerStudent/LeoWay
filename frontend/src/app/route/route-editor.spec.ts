import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RoutesApi, SavedRoute } from '../core/routes-api';
import { StopsApi } from '../core/stops-api';
import { RouteEditor } from './route-editor';
import { StopPicker } from './stop-picker';

const home = { id: '60501810', name: 'Linz, Auwiesen' };
const school = { id: '60500120', name: 'Leonding, Meixnerkreuzung' };
const saved: SavedRoute = {
  id: 7,
  homeStopId: home.id,
  schoolStopId: school.id,
  walkToStopMinutes: 5,
  walkToSchoolMinutes: 5,
  bufferMinutes: 10,
};

describe('RouteEditor', () => {
  const create = vi.fn();
  const update = vi.fn();
  beforeEach(() => {
    create.mockReset().mockReturnValue(of(saved));
    update.mockReset().mockReturnValue(of(saved));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RoutesApi, useValue: { create, update } },
        {
          provide: StopsApi,
          useValue: {
            get: (id: string) => (id === home.id ? home : school),
            resolve: (id: string) => of(id === home.id ? home : school),
          },
        },
      ],
    });
  });

  function render(route: SavedRoute | null = null) {
    const fixture = TestBed.createComponent(RouteEditor);
    fixture.componentRef.setInput('route', route);
    const output = vi.fn();
    fixture.componentInstance.saved.subscribe(output);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const choose = () => {
      const pickers = fixture.debugElement.queryAll(By.directive(StopPicker));
      (pickers[0].componentInstance as StopPicker).selectedChange.emit(home);
      (pickers[1].componentInstance as StopPicker).selectedChange.emit(school);
      fixture.detectChanges();
    };
    const submit = async () => {
      page
        .querySelector('form')!
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      await fixture.whenStable();
      fixture.detectChanges();
    };
    return { fixture, page, choose, submit, output };
  }

  it('creates a route with selected ids and the suggested ten-minute buffer', async () => {
    const { choose, submit, output } = render();
    choose();
    await submit();
    expect(create).toHaveBeenCalledWith({
      homeStopId: home.id,
      schoolStopId: school.id,
      walkToStopMinutes: 5,
      walkToSchoolMinutes: 5,
      bufferMinutes: 10,
    });
    expect(output).toHaveBeenCalledWith(saved);
  });

  it('requires selected stops rather than typed names', async () => {
    const { submit, page } = render();
    await submit();
    expect(create).not.toHaveBeenCalled();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Choose your home stop');
    expect(page.querySelector('#home-stop')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('rejects the same stop and out-of-range or fractional minutes', async () => {
    const { fixture, page, choose, submit } = render();
    choose();
    (
      fixture.debugElement.queryAll(By.directive(StopPicker))[1].componentInstance as StopPicker
    ).selectedChange.emit(home);
    const input = page.querySelector('#walk-stop') as HTMLInputElement;
    input.value = '60.5';
    input.dispatchEvent(new Event('input'));
    await submit();
    expect(create).not.toHaveBeenCalled();
    expect(page.textContent).toContain('Choose two different stops');
    expect(page.textContent).toContain('whole number from 0 to 60');
  });

  it('loads existing choices and updates the same route', async () => {
    const { submit, page } = render(saved);
    expect((page.querySelector('#home-stop') as HTMLInputElement).value).toBe(home.name);
    await submit();
    expect(update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ homeStopId: home.id, bufferMinutes: 10 }),
    );
    expect(create).not.toHaveBeenCalled();
  });

  it('keeps choices and shows all server validation errors', async () => {
    create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { errors: ['bufferMinutes must be 0 to 30 minutes', 'homeStopId is invalid'] },
          }),
      ),
    );
    const { choose, submit, page } = render();
    choose();
    await submit();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'Extra buffer must be 0 to 30',
    );
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Home stop is invalid');
    expect((page.querySelector('#home-stop') as HTMLInputElement).value).toBe(home.name);
  });

  it('lets the user retry after a save failure', async () => {
    create.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    const { choose, submit, page, output } = render();
    choose();
    await submit();
    expect(page.textContent).toContain('Your choices are still here');
    await submit();
    expect(output).toHaveBeenCalledWith(saved);
  });
});
