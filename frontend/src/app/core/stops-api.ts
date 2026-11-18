import { HttpErrorResponse } from '@angular/common/http';
import { effect, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of, retry, tap, throwError, timer } from 'rxjs';
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

  constructor() {
    effect(() => {
      if (this.session.me() === null) {
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
        this.queries.set(key, stops);
        this.catalog.update(catalog => ({ ...catalog, ...Object.fromEntries(stops.map(stop => [stop.id, stop])) }));
      }),
    );
  }

  resolve(id: string): Observable<Stop | null> {
    const cached = this.get(id);
    return cached ? of(cached) : this.search(id).pipe(map(stops => stops.find(stop => stop.id === id) ?? null), catchError(() => of(null)));
  }
}
