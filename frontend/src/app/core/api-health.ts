import { inject, Injectable } from '@angular/core';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { ApiClient } from './api-client';

@Injectable({ providedIn: 'root' })
export class ApiHealth {
  private readonly api = inject(ApiClient);

  readonly status = timer(0, 5000).pipe(
    switchMap(() =>
      this.api.getText('/api/health/ready', { timeout: 3000 }).pipe(
        map(() => 'Ready' as const),
        catchError(() => of('Unavailable' as const)),
      ),
    ),
  );
}
