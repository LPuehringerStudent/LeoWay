import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RoutesApi, SavedRoute } from '../core/routes-api';
import { Session } from '../core/session';
import { WebUntisApi, WebUntisStatus } from '../core/webuntis-api';

/**
 * What Home shows, from real data only (#61). The leave-by time and the line's delay need the calculation from
 * #58; until then Home says so instead of showing a made-up time.
 */
export type HomeData =
  | { kind: 'guest' }
  | {
      kind: 'student';
      username: string;
      /** null if the status could not be loaded. */
      webUntis: WebUntisStatus | null;
      /** null if the routes could not be loaded. */
      routes: SavedRoute[] | null;
    };

@Injectable({ providedIn: 'root' })
export class HomeDataService {
  private readonly session = inject(Session);
  private readonly webUntis = inject(WebUntisApi);
  private readonly routesApi = inject(RoutesApi);

  async load(): Promise<HomeData> {
    const me = await this.session.refresh();
    if (!me) return { kind: 'guest' };
    const [webUntis, routes] = await Promise.all([
      firstValueFrom(this.webUntis.status()).catch(() => null),
      firstValueFrom(this.routesApi.list()).catch(() => null),
    ]);
    return { kind: 'student', username: me.username, webUntis, routes };
  }
}

/** "Today", "Tomorrow" or the weekday, plus the date, for a lesson date like 2026-11-16. */
export function dayLabel(date: string, today: Date = new Date()): { day: string; date: string } {
  const d = new Date(`${date}T12:00`);
  const iso = (x: Date) =>
    `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
  const tomorrow = new Date(today);
  tomorrow.setDate(today.getDate() + 1);
  const day =
    date === iso(today) ? 'Today' : date === iso(tomorrow) ? 'Tomorrow' : d.toLocaleDateString('en-GB', { weekday: 'long' });
  return { day, date: d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' }) };
}
