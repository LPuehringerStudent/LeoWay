import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <header class="bar">
      <a class="brand" routerLink="/">LeoWay</a>
      <nav aria-label="Main">
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"
           ariaCurrentWhenActive="page">Home</a>
        <a routerLink="/route" routerLinkActive="active" ariaCurrentWhenActive="page">Route</a>
        <a routerLink="/privacy" routerLinkActive="active" ariaCurrentWhenActive="page">Privacy</a>
      </nav>
    </header>
    <main id="main" tabindex="-1"><router-outlet /></main>
  `,
  styles: `
    .bar {
      display: flex; justify-content: space-between; align-items: center; gap: 0.5rem;
      padding: 0.5rem 1rem; background: var(--accent); color: var(--on-accent);
    }
    .brand { color: inherit; font-weight: 700; text-decoration: none; font-size: 1.1rem; }
    nav { display: flex; gap: 0.25rem; }
    nav a { color: inherit; text-decoration: none; padding: 0.4rem 0.5rem; border-radius: 0.35rem; }
    nav a.active { background: var(--accent-hover); text-decoration: underline; }
    main { max-width: 32rem; margin: 0 auto; padding: 0.75rem 1rem; }
    main:focus { outline: none; }
    .skip { position: absolute; left: -999px; }
    .skip:focus { left: 0.5rem; top: 0.5rem; z-index: 1; background: var(--surface); color: var(--text); padding: 0.5rem; }
  `,
})
export class App {}
