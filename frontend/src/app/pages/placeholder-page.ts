import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

/** Stand-in for screens later sprints build. The title comes from the route. */
@Component({
  selector: 'app-placeholder-page',
  imports: [RouterLink],
  template: `
    <h1>{{ title }}</h1>
    <p>This screen is not built yet.</p>
    <p><a routerLink="/">Back to Home</a></p>
  `,
})
export class PlaceholderPage {
  protected readonly title = inject(ActivatedRoute).snapshot.title ?? '';
}
