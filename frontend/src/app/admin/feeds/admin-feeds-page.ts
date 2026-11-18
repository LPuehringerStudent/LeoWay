import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
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
  protected readonly feeds = signal<AdminFeed[] | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly denied = signal(false);
  protected readonly loggedOut = signal(false);
  protected readonly error = signal(false);
  protected readonly checkedAt = signal<string | null>(null);

  ngOnInit() {
    this.refreshing.set(true);
    this.api.list().pipe(
      map(feeds => ({ feeds, status: 200 })),
      catchError((e: unknown) => of({ feeds: null, status: e instanceof HttpErrorResponse ? e.status : 0 })),
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
