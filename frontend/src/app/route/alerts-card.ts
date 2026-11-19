import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AlertChannel, AlertsApi } from '../core/alerts-api';

/**
 * Alert channels on Route and alerts (#62, F04). Discord first: the student pastes a webhook URL from their own
 * channel and gets a test message. The URL is a secret: it stays in the input until saved and is never shown again.
 */
@Component({
  selector: 'app-alerts-card',
  imports: [FormsModule],
  templateUrl: './alerts-card.html',
  styleUrl: '../connect/connect-page.css',
})
export class AlertsCard implements OnInit {
  private readonly api = inject(AlertsApi);

  protected readonly discord = signal<AlertChannel | null>(null);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly editing = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected webhookUrl = '';

  async ngOnInit() {
    try {
      const channels = await firstValueFrom(this.api.list());
      this.discord.set(channels.find((c) => c.kind === 'DISCORD') ?? null);
    } catch {
      this.error.set('Your alert channels could not be loaded.');
    }
    this.loaded.set(true);
  }

  protected async save() {
    if (this.busy() || !this.webhookUrl.trim()) return;
    await this.run(async () => {
      this.discord.set(await firstValueFrom(this.api.setDiscord(this.webhookUrl.trim())));
      this.webhookUrl = '';
      this.editing.set(false);
      this.message.set('Saved. A test message is on its way to your Discord channel.');
    });
  }

  protected async test() {
    await this.run(async () => {
      this.discord.set(await firstValueFrom(this.api.test('discord')));
      this.message.set('Test message sent.');
    }, true);
  }

  protected async remove() {
    await this.run(async () => {
      await firstValueFrom(this.api.remove('discord'));
      this.discord.set(null);
      this.message.set('Discord alerts removed.');
    });
  }

  private async run(work: () => Promise<void>, reloadOnConflict = false) {
    if (this.busy()) return;
    this.busy.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      await work();
    } catch (e) {
      this.error.set(errorText(e));
      if (reloadOnConflict && e instanceof HttpErrorResponse && e.status === 409) {
        const channels = await firstValueFrom(this.api.list()).catch(() => null);
        if (channels) this.discord.set(channels.find((c) => c.kind === 'DISCORD') ?? null);
      }
    } finally {
      this.busy.set(false);
    }
  }
}

/** What went wrong, in words; the server's own message for 400 and 409. */
export function errorText(e: unknown): string {
  if (!(e instanceof HttpErrorResponse)) return 'Something went wrong. Try again.';
  const fromServer = e.error?.errors?.[0];
  switch (e.status) {
    case 400:
    case 409:
      return fromServer ?? 'Discord did not accept this webhook URL.';
    case 429:
      return `Wait ${e.headers.get('Retry-After') ?? 'a few'} seconds before sending another test message.`;
    case 503:
      return "Discord can't be reached right now. Nothing was changed; try again in a moment.";
    default:
      return 'Something went wrong. Try again.';
  }
}
