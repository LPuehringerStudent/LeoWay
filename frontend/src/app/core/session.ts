import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiClient } from './api-client';

export interface Me {
  username: string;
}

export type LoginResult = 'ok' | 'rejected' | 'unavailable';

/**
 * Login state. Logging in = connecting WebUntis (#20): the backend checks the access key with WebUntis.
 * The key only lives in the form while typing; it is never written to storage.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(ApiClient);
  /** undefined while unknown, null when logged out. */
  readonly me = signal<Me | null | undefined>(undefined);

  async refresh(): Promise<Me | null> {
    try {
      const me = await firstValueFrom(this.api.get<Me>('/api/me'));
      this.me.set(me);
      return me;
    } catch {
      this.me.set(null);
      return null;
    }
  }

  async login(username: string, accessKey: string): Promise<LoginResult> {
    try {
      await firstValueFrom(this.api.postForm('/j_security_check', { j_username: username, j_password: accessKey }));
      await this.refresh();
      return 'ok';
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 401) {
        // 401 means either a wrong key or WebUntis being down: ask the backend which one.
        const r = await firstValueFrom(
          this.api.get<{ webUntisReachable: boolean }>('/api/session/webuntis-reachable'),
        ).catch(() => ({ webUntisReachable: false }));
        return r.webUntisReachable ? 'rejected' : 'unavailable';
      }
      return 'unavailable';
    }
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.api.post('/api/session/logout', {})).catch(() => undefined);
    this.me.set(null);
  }
}
