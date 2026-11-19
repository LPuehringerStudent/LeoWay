import { HttpErrorResponse } from '@angular/common/http';
import { effect, inject, Injectable, signal } from '@angular/core';
import { catchError, defer, finalize, firstValueFrom, from, map, Observable, of, retry, shareReplay, tap, throwError, timer } from 'rxjs';
import { ApiClient } from './api-client';
import { Session } from './session';

export interface Stop {
  id: string;
  name: string;
  latitude?: number | null;
  longitude?: number | null;
}

@Injectable({ providedIn: 'root' })
export class StopsApi {
  private readonly api = inject(ApiClient);
  private readonly session = inject(Session);
  private readonly catalog = signal<Record<string, Stop>>({});
  private readonly queries = new Map<string, Stop[]>();
  private readonly resolving = new Map<string, Observable<Stop | null>>();
  private resolutionTail = Promise.resolve();
  private revision = 0;

  constructor() {
    effect(() => {
      if (this.session.me() === null) {
        this.revision++;
        this.catalog.set({});
        this.queries.clear();
      }
    });
  }

  get(id: string): Stop | undefined { return this.catalog()[id]; }

  search(query: string): Observable<Stop[]> {
    const key = query.trim().toLowerCase();
    const cached = this.queries.get(key);
    if (cached) return of(cached);
    return this.api.get<Stop[]>(`/api/stops?q=${encodeURIComponent(query.trim())}`, { timeout: 12_000 }).pipe(
      retry({ count: 2, delay: (error: unknown) => {
        if (!(error instanceof HttpErrorResponse) || error.status !== 429) return throwError(() => error);
        const seconds = Number(error.headers.get('Retry-After'));
        return timer(Number.isFinite(seconds) && seconds > 0 ? seconds * 1000 : 2000);
      } }),
      tap(stops => {
        if (this.session.me() === null) return;
        this.queries.set(key, stops);
        this.catalog.update(catalog => ({ ...catalog, ...Object.fromEntries(stops.map(stop => [stop.id, stop])) }));
      }),
    );
  }

  resolve(id: string): Observable<Stop | null> {
    const cached = this.get(id);
    if (cached) return of(cached);
    const pending = this.resolving.get(id);
    if (pending) return pending;
    const revision = this.revision;
    // Share one queue across cards and the editor. A slow/retried lookup finishes before the next starts.
    const result = defer(() => {
      const task = this.resolutionTail.then(async () => {
        if (revision !== this.revision) return null;
        const known = this.get(id);
        if (known) return known;
        const stop = await firstValueFrom(this.search(id).pipe(
          map(stops => stops.find(stop => stop.id === id) ?? null), catchError(() => of(null)),
        ));
        return revision === this.revision ? stop : null;
      });
      this.resolutionTail = task.then(() => undefined, () => undefined);
      return from(task);
    }).pipe(
      finalize(() => this.resolving.delete(id)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.resolving.set(id, result);
    return result;
  }
}
