import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api-client';

/** One alert channel (`/api/me/alerts`, #62). The target (webhook URL) is never sent back. */
export interface AlertChannel {
  kind: 'DISCORD';
  /** False once the channel refused a message for good (webhook deleted); saving it again fixes it. */
  working: boolean;
  since: string;
  brokenAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class AlertsApi {
  private readonly api = inject(ApiClient);

  list(): Observable<AlertChannel[]> {
    return this.api.get<AlertChannel[]>('/api/me/alerts');
  }

  /** Sends a test message to the webhook and stores it only if Discord accepts it. */
  setDiscord(webhookUrl: string): Observable<AlertChannel> {
    return this.api.put<AlertChannel>('/api/me/alerts/discord', { webhookUrl });
  }

  test(kind: 'discord'): Observable<AlertChannel> {
    return this.api.post<AlertChannel>(`/api/me/alerts/${kind}/test`, null);
  }

  remove(kind: 'discord'): Observable<void> {
    return this.api.delete(`/api/me/alerts/${kind}`);
  }
}
