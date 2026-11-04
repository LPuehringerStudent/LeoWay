import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Session } from '../core/session';
import { WebUntisApi, WebUntisStatus } from '../core/webuntis-api';

/** Route and alerts. Sprint 1 has the WebUntis section (F01); routes and alerts come later. */
@Component({
  selector: 'app-route-page',
  imports: [RouterLink],
  templateUrl: './route-page.html',
  styleUrl: '../connect/connect-page.css',
})
export class RoutePage implements OnInit {
  protected readonly session = inject(Session);
  private readonly webUntis = inject(WebUntisApi);
  private readonly router = inject(Router);

  protected readonly status = signal<WebUntisStatus | null>(null);
  protected readonly loading = signal(true);

  async ngOnInit() {
    const me = await this.session.refresh();
    if (me) {
      this.status.set(await firstValueFrom(this.webUntis.status()).catch(() => null));
    }
    this.loading.set(false);
  }

  protected async disconnect() {
    await firstValueFrom(this.webUntis.disconnect());
    this.status.set(await firstValueFrom(this.webUntis.status()));
  }

  protected async logout() {
    await this.session.logout();
    await this.router.navigateByUrl('/');
  }

  protected dayLabel(date: string): string {
    const today = new Date().toISOString().slice(0, 10);
    return date === today ? 'today' : new Date(date + 'T12:00').toLocaleDateString('en-GB', { weekday: 'long' });
  }
}
