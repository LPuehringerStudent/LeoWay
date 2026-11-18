import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api-client';

export interface AdminFeed {
  feedId: string;
  state: 'OK' | 'STALE' | 'DOWN';
  lastSuccessAt: string | null;
  lastErrorAt: string | null;
  lastError: string | null;
}

@Injectable({ providedIn: 'root' })
export class AdminFeedsApi {
  private readonly api = inject(ApiClient);

  list() {
    return this.api.get<AdminFeed[]>('/api/admin/feeds', { timeout: 10_000 });
  }
}
