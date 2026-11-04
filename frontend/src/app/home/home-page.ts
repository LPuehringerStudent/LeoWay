import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ApiHealth } from '../core/api-health';
import { TimeSourceLabel } from '../shared/time-source';
import { HomeSummaryService } from './home-summary';

/**
 * Home works like a departure board (U01): first lesson, leave-by time and the line's delay
 * fit above the fold on a 360 px screen. Data is mocked until #7 and the leave-by calculation land.
 */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink, TimeSourceLabel],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage {
  protected readonly status = toSignal(inject(ApiHealth).status);
  protected readonly summary = toSignal(inject(HomeSummaryService).today);

  protected readonly delayText = computed(() => {
    const delay = this.summary()?.line.delayMinutes;
    if (delay == null) return 'no live data';
    if (delay === 0) return 'on time';
    return delay > 0 ? `+${delay} min` : `${delay} min`;
  });
}
