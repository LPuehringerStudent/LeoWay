import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { Stop, StopsApi } from '../core/stops-api';
import { StopPicker } from './stop-picker';

describe('StopPicker', () => {
  const search = vi.fn();
  beforeEach(() => {
    vi.useFakeTimers();
    search.mockReset().mockReturnValue(of([{ id: '1', name: 'Auwiesen' }]));
    TestBed.configureTestingModule({ providers: [{ provide: StopsApi, useValue: { search } }] });
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  function render() {
    const fixture = TestBed.createComponent(StopPicker);
    fixture.componentRef.setInput('fieldId', 'home');
    fixture.componentRef.setInput('label', 'Home stop');
    const selected = vi.fn();
    fixture.componentInstance.selectedChange.subscribe(selected);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const input = page.querySelector('input')!;
    const type = (value: string) => {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    return { fixture, page, input, type, selected };
  }

  it('debounces stop search and supports selection entirely by keyboard', async () => {
    const { fixture, input, type, selected } = render();
    type('Au');
    await vi.advanceTimersByTimeAsync(200);
    type('Auw');
    await vi.advanceTimersByTimeAsync(349);
    expect(search).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    fixture.detectChanges();
    expect(search).toHaveBeenCalledExactlyOnceWith('Auw');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
    fixture.detectChanges();
    expect(selected).toHaveBeenLastCalledWith({ id: '1', name: 'Auwiesen' });
    expect(input.value).toBe('Auwiesen');
    expect(input.getAttribute('aria-expanded')).toBe('false');
  });

  it('ignores a late result for an older query', async () => {
    const old = new Subject<Stop[]>();
    search.mockReturnValueOnce(old);
    const { fixture, page, type } = render();
    type('Old');
    await vi.advanceTimersByTimeAsync(350);
    type('New');
    old.next([{ id: 'old', name: 'Old result' }]);
    await vi.advanceTimersByTimeAsync(350);
    fixture.detectChanges();
    expect(page.textContent).not.toContain('Old result');
  });

  it('explains unavailable search and no results', async () => {
    search
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
      .mockReturnValueOnce(of([]));
    const { fixture, page, type } = render();
    type('Stop');
    await vi.advanceTimersByTimeAsync(350);
    fixture.detectChanges();
    expect(page.textContent).toContain('Stop search unavailable');
    type('Unknown');
    await vi.advanceTimersByTimeAsync(350);
    fixture.detectChanges();
    expect(page.textContent).toContain('No stops found');
  });
});
