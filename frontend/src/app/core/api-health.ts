import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, of, switchMap, timer } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiHealth {
  private readonly http = inject(HttpClient);

  readonly status = timer(0, 5000).pipe(
    switchMap(() =>
      this.http.get('/api/health/ready', { responseType: 'text', timeout: 3000 }).pipe(
        map(() => 'Ready' as const),
        catchError(() => of('Unavailable' as const)),
      ),
    ),
  );
}
