import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api-client';

export interface WebUntisStatus {
  connected: boolean;
  username: string | null;
  /** False if WebUntis could not be reached; firstLesson is then unknown. */
  available: boolean;
  firstLesson: { date: string; start: string; subject: string | null } | null;
}

@Injectable({ providedIn: 'root' })
export class WebUntisApi {
  private readonly api = inject(ApiClient);

  status(): Observable<WebUntisStatus> {
    return this.api.get<WebUntisStatus>('/api/me/webuntis');
  }

  disconnect(): Observable<void> {
    return this.api.delete('/api/me/webuntis');
  }
}
