import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiHealth } from './core/api-health';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <header class="bar">
      <a class="brand" routerLink="/">
        <span class="brand__mark" aria-hidden="true">L</span>LeoWay
      </a>
      <nav aria-label="Main">
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"
           ariaCurrentWhenActive="page">Home</a>
        <a routerLink="/route" routerLinkActive="active" ariaCurrentWhenActive="page">Route</a>
        <a routerLink="/privacy" routerLinkActive="active" ariaCurrentWhenActive="page">Privacy</a>
      </nav>
    </header>
    <main id="main" tabindex="-1"><router-outlet /></main>
    <footer class="foot">
      <p role="status">
        @switch (api()) {
          @case ('Ready') { LeoWay server is running }
          @case ('Unavailable') { <strong>LeoWay server not reachable.</strong> Data may be out of date. }
          @default { Checking the LeoWay server… }
        }
      </p>
      <p>A school project at HTL Leonding</p>
    </footer>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100dvh; }
    .bar {
      display: flex; justify-content: space-between; align-items: center; gap: 0.5rem;
      padding: 0.55rem 1rem; background: var(--night); color: var(--on-night);
    }
    .brand {
      display: flex; align-items: center; gap: 0.45rem;
      color: inherit; font-family: var(--display); font-weight: 700; font-size: 1.2rem; text-decoration: none;
    }
    .brand__mark {
      display: grid; place-items: center; width: 1.6rem; height: 1.6rem; border-radius: 0.35rem;
      background: var(--amber); color: var(--night); font-size: 1.05rem;
    }
    nav { display: flex; gap: 0.15rem; }
    nav a {
      color: var(--on-night); text-decoration: none; padding: 0.45rem 0.55rem; border-radius: 0.35rem;
      border-bottom: 2px solid transparent;
    }
    nav a:hover { background: var(--night-line); }
    nav a.active { border-bottom-color: var(--amber); font-weight: 600; }
    main { width: 100%; max-width: 32rem; margin: 0 auto; padding: 0.9rem 1rem 1.5rem; flex: 1; }
    main:focus { outline: none; }
    .foot {
      width: 100%; max-width: 32rem; margin: 0 auto; padding: 0 1rem 1.25rem;
      color: var(--muted); font-size: 0.8rem; display: flex; flex-direction: column; gap: 0.2rem;
    }
    .foot p { margin: 0; }
    .skip { position: absolute; left: -999px; }
    .skip:focus { left: 0.5rem; top: 0.5rem; z-index: 1; background: var(--surface); color: var(--text); padding: 0.5rem; }
  `,
})
export class App {
  protected readonly api = toSignal(inject(ApiHealth).status);
}
