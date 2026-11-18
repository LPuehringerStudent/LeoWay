import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { LeaveByApi, LeaveByResult } from '../core/leave-by-api';
import { RoutesApi, SavedRoute } from '../core/routes-api';
import { Session } from '../core/session';
import { WebUntisApi, WebUntisStatus } from '../core/webuntis-api';

/** What Home shows, from real data only (#61). Missing parts are null, never made up. */
export type HomeData =
  | { kind: 'guest' }
  | {
      kind: 'student';
      username: string;
      /** null if the status could not be loaded. */
      webUntis: WebUntisStatus | null;
      /** null if the routes could not be loaded. */
      routes: SavedRoute[] | null;
      /** null if the leave-by time could not be calculated right now (server or Linz AG unreachable). */
      leaveBy: LeaveByResult | null;
      /** When leaveBy was loaded, shown next to timetable-only or outdated times (F03). */
      loadedAt: Date;
      /** True if the refresh failed and leaveBy is the last good result (see keepLastLeaveBy). */
      outdated?: boolean;
    };

@Injectable({ providedIn: 'root' })
export class HomeDataService {
  private readonly session = inject(Session);
  private readonly webUntis = inject(WebUntisApi);
  private readonly routesApi = inject(RoutesApi);
  private readonly leaveByApi = inject(LeaveByApi);

  async load(): Promise<HomeData> {
    const me = await this.session.refresh();
    if (!me || me.admin) return { kind: 'guest' };
    const [webUntis, routes, leaveBy] = await Promise.all([
      firstValueFrom(this.webUntis.status()).catch(() => null),
      firstValueFrom(this.routesApi.list()).catch(() => null),
      firstValueFrom(this.leaveByApi.get()).catch(() => null),
    ]);
    return { kind: 'student', username: me.username, webUntis, routes, leaveBy, loadedAt: new Date() };
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

/**
 * A failed refresh keeps the last good leave-by time while its lesson is still ahead, marked as outdated, instead
 * of replacing it with "could not be calculated" (#61). A new answer, even "no route", always wins.
 */
export function keepLastLeaveBy(prev: HomeData | null, next: HomeData, now: Date = new Date()): HomeData {
  if (next.kind !== 'student' || next.leaveBy !== null) return next;
  if (prev?.kind !== 'student' || !prev.leaveBy?.lessonStart) return next;
  if (Date.parse(prev.leaveBy.lessonStart) <= now.getTime()) return next;
  return { ...next, leaveBy: prev.leaveBy, loadedAt: prev.loadedAt, outdated: true };
}
