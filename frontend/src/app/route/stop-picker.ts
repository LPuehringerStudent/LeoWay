import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, switchMap, timer } from 'rxjs';
import { Stop, StopsApi } from '../core/stops-api';

@Component({
  selector: 'app-stop-picker',
  templateUrl: './stop-picker.html',
  styleUrl: './stop-picker.css',
})
export class StopPicker implements OnInit {
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  readonly selected = input<Stop | null>(null);
  readonly invalid = input(false);
  readonly selectedChange = output<Stop | null>();
  private readonly stops = inject(StopsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queries = new Subject<string>();
  protected readonly query = signal('');
  protected readonly results = signal<Stop[]>([]);
  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly open = signal(false);
  protected readonly active = signal(-1);

  constructor() {
    effect(() => { const stop = this.selected(); if (stop) this.query.set(stop.name); });
  }

  ngOnInit() {
    this.queries.pipe(
      switchMap(query => query.trim().length < 2 ? of(null) : timer(350).pipe(
        switchMap(() => this.stops.search(query)),
        map(stops => ({ stops, message: stops.length ? '' : 'No stops found. Try a different name.' })),
        catchError((e: unknown) => of({ stops: [], message: e instanceof HttpErrorResponse && e.status === 429
          ? 'Search is busy. Please try again shortly.' : 'Stop search unavailable. Please try again later.' })),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => {
      this.busy.set(false);
      this.results.set(result?.stops ?? []);
      this.message.set(result?.message ?? '');
      this.active.set(result?.stops.length ? 0 : -1);
    });
  }

  protected search(value: string) {
    this.query.set(value);
    this.selectedChange.emit(null);
    this.open.set(true);
    this.busy.set(value.trim().length >= 2);
    this.message.set('');
    this.queries.next(value);
  }

  protected choose(stop: Stop) {
    this.queries.next('');
    this.query.set(stop.name);
    this.open.set(false);
    this.selectedChange.emit(stop);
  }

  protected key(event: KeyboardEvent) {
    if (event.key === 'Escape') { this.open.set(false); return; }
    const count = this.results().length;
    if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && count) {
      event.preventDefault();
      this.open.set(true);
      this.active.update(i => (i + (event.key === 'ArrowDown' ? 1 : -1) + count) % count);
    } else if (event.key === 'Enter' && this.open() && this.active() >= 0 && count) {
      event.preventDefault();
      this.choose(this.results()[this.active()]);
    }
  }
}
