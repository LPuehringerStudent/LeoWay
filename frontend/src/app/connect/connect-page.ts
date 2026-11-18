import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LoginResult, Session } from '../core/session';
import { PrivacyInfo } from '../privacy/privacy-info';

/**
 * F01 Connect WebUntis = log in (#20). The form appears only after the privacy information was confirmed (L01).
 * Username and key stay in component state; nothing is written to local storage.
 */
@Component({
  selector: 'app-connect-page',
  imports: [FormsModule, PrivacyInfo],
  templateUrl: './connect-page.html',
  styleUrl: './connect-page.css',
})
export class ConnectPage {
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  // A user who just logged out already acknowledged the information before their previous login.
  protected readonly consented = signal(this.router.currentNavigation()?.extras.state?.['returningUser'] === true);
  protected readonly busy = signal(false);
  protected readonly result = signal<LoginResult | null>(null);
  protected username = '';
  protected accessKey = '';

  protected async submit() {
    if (this.busy() || !this.username.trim() || !this.accessKey.trim()) return;
    this.busy.set(true);
    this.result.set(null);
    const result = await this.session.login(this.username.trim(), this.accessKey.trim());
    this.busy.set(false);
    if (result === 'ok') {
      this.accessKey = '';
      await this.router.navigateByUrl(this.session.me()?.admin ? '/admin/feeds' : '/route');
    } else {
      this.result.set(result);
    }
  }
}
