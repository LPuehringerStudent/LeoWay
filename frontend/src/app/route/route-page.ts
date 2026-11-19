import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Session } from '../core/session';
import { WebUntisApi, WebUntisStatus } from '../core/webuntis-api';
import { RoutesApi, SavedRoute } from '../core/routes-api';
import { Stop, StopsApi } from '../core/stops-api';
import { RouteEditor, RoutePreview } from './route-editor';
import { RouteMap } from './route-map';
import { AlertsCard } from './alerts-card';
import { HttpErrorResponse } from '@angular/common/http';
import { RECONNECT_REQUIRED } from '../core/session-errors';

/** Own saved routes and WebUntis connection for students, including admins with student access. */
@Component({
  selector: 'app-route-page',
  imports: [RouterLink, RouteEditor, RouteMap, AlertsCard],
  templateUrl: './route-page.html',
  styleUrls: ['../connect/connect-page.css', './route-page.css'],
})
export class RoutePage implements OnInit {
  protected readonly session = inject(Session);
  private readonly webUntis = inject(WebUntisApi);
  private readonly router = inject(Router);
  private readonly routesApi = inject(RoutesApi);
  protected readonly stops = inject(StopsApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly status = signal<WebUntisStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly reconnectRequired = signal(false);
  protected readonly reconnectMessage = RECONNECT_REQUIRED;
  protected readonly routes = signal<SavedRoute[]>([]);
  protected readonly routesLoading = signal(false);
  protected readonly routeError = signal('');
  protected readonly notice = signal('');
  protected readonly activeId = signal<number | null>(null);
  protected readonly activeRoute = computed(
    () => this.routes().find((route) => route.id === this.activeId()) ?? null,
  );
  protected readonly editor = signal<{ key: number; route: SavedRoute | null } | null>(null);
  protected readonly preview = signal<RoutePreview | null>(null);
  protected readonly deleteId = signal<number | null>(null);
  protected readonly deleting = signal(false);
  private editorKey = 0;
  protected readonly mapHome = computed(() =>
    this.editor() ? (this.preview()?.home ?? null) : this.stop(this.activeRoute()?.homeStopId),
  );
  protected readonly mapSchool = computed(() =>
    this.editor() ? (this.preview()?.school ?? null) : this.stop(this.activeRoute()?.schoolStopId),
  );

  async ngOnInit() {
    const me = await this.session.refresh();
    if (me) {
      await Promise.all([
        firstValueFrom(this.webUntis.status())
          .then((status) => this.status.set(status))
          .catch((error) => {
            this.status.set(null);
            if (error instanceof HttpErrorResponse && error.status === 403)
              this.reconnectRequired.set(true);
          }),
        this.loadRoutes(),
      ]);
    }
    this.loading.set(false);
  }

  protected async loadRoutes() {
    this.routesLoading.set(true);
    this.routeError.set('');
    try {
      const routes = await firstValueFrom(this.routesApi.list());
      this.routes.set(routes);
      this.activeId.set(routes[0]?.id ?? null);
      for (const id of new Set(routes.flatMap((route) => [route.homeStopId, route.schoolStopId]))) {
        this.stops.resolve(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
      }
      if (!routes.length) this.edit(null);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 403) {
        this.reconnectRequired.set(true);
        this.routeError.set(RECONNECT_REQUIRED);
      } else this.routeError.set('Your saved routes could not be loaded. Please try again.');
    } finally {
      this.routesLoading.set(false);
    }
  }

  protected stop(id: string | undefined): Stop | null {
    return id ? (this.stops.get(id) ?? { id, name: `Stop ${id}` }) : null;
  }

  protected edit(route: SavedRoute | null) {
    this.preview.set(null);
    this.editor.set({ key: ++this.editorKey, route });
    this.notice.set('');
    this.deleteId.set(null);
    if (route) this.activeId.set(route.id);
  }

  protected closeEditor() {
    this.editor.set(null);
    this.preview.set(null);
  }

  protected saved(route: SavedRoute) {
    const editing = this.routes().some((r) => r.id === route.id);
    this.routes.update((routes) =>
      editing ? routes.map((r) => (r.id === route.id ? route : r)) : [...routes, route],
    );
    this.activeId.set(route.id);
    this.closeEditor();
    this.notice.set(editing ? 'Your route has been updated.' : 'Your route has been saved.');
  }

  protected async remove(route: SavedRoute) {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.routeError.set('');
    try {
      await firstValueFrom(this.routesApi.delete(route.id));
      this.routes.update((routes) => routes.filter((r) => r.id !== route.id));
      if (this.activeId() === route.id) this.activeId.set(this.routes()[0]?.id ?? null);
      if (this.editor()?.route?.id === route.id) this.closeEditor();
      this.deleteId.set(null);
      this.notice.set('Route deleted.');
    } catch {
      this.routeError.set('This route could not be deleted. Please try again.');
    } finally {
      this.deleting.set(false);
    }
  }

  protected async disconnect() {
    await firstValueFrom(this.webUntis.disconnect());
    this.status.set(await firstValueFrom(this.webUntis.status()));
  }

  protected async logout() {
    await this.session.logout();
    await this.router.navigateByUrl('/connect', {
      replaceUrl: true,
      state: { returningUser: true },
    });
  }

  protected dayLabel(date: string): string {
    const today = new Date().toISOString().slice(0, 10);
    return date === today
      ? 'today'
      : new Date(date + 'T12:00').toLocaleDateString('en-GB', { weekday: 'long' });
  }
}
