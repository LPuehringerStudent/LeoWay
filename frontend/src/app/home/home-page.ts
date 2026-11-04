import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ApiHealth } from '../core/api-health';

@Component({
  selector: 'app-home-page',
  template: `
    <main>
      <h1>LeoWay</h1>
      <p role="status">API status: {{ status() ?? 'Checking' }}</p>
    </main>
  `,
})
export class HomePage {
  protected readonly status = toSignal(inject(ApiHealth).status);
}
