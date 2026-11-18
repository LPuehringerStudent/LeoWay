import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, exhaustMap, filter, map, merge, of, Subject, timer } from 'rxjs';
import { AdminFeed, AdminFeedsApi } from '../../core/admin-feeds-api';

@Component({
  selector: 'app-admin-feeds-page',
  imports: [RouterLink],
  templateUrl: './admin-feeds-page.html',
  styleUrl: './admin-feeds-page.css',
})
export class AdminFeedsPage implements OnInit {
  private readonly api = inject(AdminFeedsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<void>();
  protected readonly feeds = signal<AdminFeed[] | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly denied = signal(false);
  protected readonly loggedOut = signal(false);
  protected readonly error = signal(false);
  protected readonly checkedAt = signal<string | null>(null);
  protected readonly ok = computed(() => this.feeds()?.filter(f => f.state === 'OK').length ?? 0);
  protected readonly stale = computed(() => this.feeds()?.filter(f => f.state === 'STALE').length ?? 0);
  protected readonly down = computed(() => this.feeds()?.filter(f => f.state === 'DOWN').length ?? 0);
  protected readonly heading = computed(() => {
    if (this.error()) return this.feeds() ? 'Status may be out of date' : 'Feed status unavailable';
    const feeds = this.feeds();
    if (feeds === null) return 'Checking data sources';
    if (!feeds.length) return 'No feeds reporting yet';
    const issues = this.stale() + this.down();
    return issues ? `${issues} ${issues === 1 ? 'feed needs' : 'feeds need'} attention` : 'All feeds operational';
  });

  ngOnInit() {
    merge(timer(0, 30_000), this.requests).pipe(
      filter(() => !this.denied()),
      exhaustMap(() => {
        this.refreshing.set(true);
        return this.api.list().pipe(
          map(feeds => ({ feeds, status: 200 })),
          catchError((e: unknown) => of({ feeds: null, status: e instanceof HttpErrorResponse ? e.status : 0 })),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => {
      this.refreshing.set(false);
      if (result.status === 401 || result.status === 403) {
        this.feeds.set(null);
        this.checkedAt.set(null);
        this.denied.set(true);
        this.loggedOut.set(result.status === 401);
      } else if (result.feeds !== null) {
        this.feeds.set([...result.feeds].sort((a, b) => a.feedId.localeCompare(b.feedId)));
        this.checkedAt.set(new Date().toISOString());
        this.error.set(false);
      } else {
        // Keep the last successful snapshot; a failed request doesn't mean a feed is DOWN.
        this.error.set(true);
      }
    });
  }

  protected refresh() {
    this.requests.next();
  }

  protected name(id: string) {
    return id === 'linz-ag' ? 'Linz AG' : id;
  }

  protected stateText(state: AdminFeed['state']) {
    return { OK: 'Receiving updates', STALE: 'Updates are overdue', DOWN: 'Source unavailable' }[state];
  }

  protected formatTime(value: string) {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Europe/Vienna', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).format(new Date(value));
  }
}
