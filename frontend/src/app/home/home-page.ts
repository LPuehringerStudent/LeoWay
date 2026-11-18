import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FeedHealth, FeedState } from '../core/feed-health';
import { clock, LeaveByResult } from '../core/leave-by-api';
import { TimeSourceLabel } from '../shared/time-source';
import { dayLabel, HomeData, HomeDataService, keepLastLeaveBy } from './home-data';

/** How often Home reloads while it is open and visible (#61). */
export const REFRESH_MS = 60_000;

/**
 * Home works like a departure board (U01): the next first lesson and the leave-by time fit above the fold on a
 * 360 px screen. Everything shown is real data; missing parts say what is missing. Reloads every minute while open.
 */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink, TimeSourceLabel],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage implements OnInit {
  private readonly data = inject(HomeDataService);
  protected readonly home = signal<HomeData | null>(null);
  protected readonly feed = toSignal(inject(FeedHealth).linzAg);
  protected readonly dayLabel = dayLabel;
  protected readonly clock = clock;
  private readonly destroyRef = inject(DestroyRef);

  async ngOnInit() {
    const timer = setInterval(() => {
      if (!document.hidden) void this.reload();
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
    await this.reload();
  }

  private async reload() {
    const next = await this.data.load();
    this.home.set(keepLastLeaveBy(this.home(), next));
  }

  /** Short line under the leave-by time; null for a plain IN_TIME. */
  protected leaveNote(l: LeaveByResult): string | null {
    switch (l.status) {
      case 'NO_BUFFER': return 'No buffer left: this gets you there just in time';
      case 'LATE': return `No connection gets you there in time: ${l.minutesLate} min late`;
      default: return null;
    }
  }

  /** Why there is no leave-by time; null if there is one. */
  protected leaveMissing(l: LeaveByResult | null, hasRoutes: boolean | undefined): string | null {
    if (!l) return 'Could not be calculated right now';
    switch (l.status) {
      case 'IN_TIME': case 'NO_BUFFER': case 'LATE': return null;
      case 'NO_ROUTE': return hasRoutes === false ? 'Needs a saved route' : 'Could not be calculated right now';
      case 'NO_CONNECTION': return 'No connection found';
      default: return 'Not available';
    }
  }

  protected timeOfDay(d: Date): string {
    return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

  protected feedText(state: FeedState | undefined): string {
    switch (state) {
      case 'OK': return 'Working';
      case 'STALE': return 'No update for over five minutes';
      case 'DOWN': return 'Not available right now';
      case 'UNKNOWN': return 'Starting up';
      case 'OFFLINE': return 'LeoWay server not reachable';
      default: return 'Checking';
    }
  }
}
