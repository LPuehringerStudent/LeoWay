import { inject, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, switchMap, timer } from 'rxjs';
import { ApiClient } from './api-client';

/** OK, STALE (no update for five minutes), DOWN, UNKNOWN (no status yet) or OFFLINE (API not reachable). */
export type FeedState = 'OK' | 'STALE' | 'DOWN' | 'UNKNOWN' | 'OFFLINE';

interface Report {
  status: 'UP' | 'DOWN';
  feeds: { feedId: string; state: Exclude<FeedState, 'OFFLINE'> }[];
}

/** State of the Linz AG live feed from the public `/api/health/feeds` (503 still carries the report). */
@Injectable({ providedIn: 'root' })
export class FeedHealth {
  private readonly api = inject(ApiClient);

  readonly linzAg: Observable<FeedState> = timer(0, 60_000).pipe(
    switchMap(() =>
      this.api.get<Report>('/api/health/feeds', { timeout: 5000 }).pipe(
        catchError((e: unknown) =>
          e instanceof HttpErrorResponse && e.status === 503 && e.error?.feeds ? of(e.error as Report) : of(null),
        ),
        map((r): FeedState => (r ? (r.feeds.find((f) => f.feedId === 'linz-ag')?.state ?? 'UNKNOWN') : 'OFFLINE')),
      ),
    ),
  );
}
