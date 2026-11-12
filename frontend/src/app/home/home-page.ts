import { Component, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FeedHealth, FeedState } from '../core/feed-health';
import { TimeSourceLabel } from '../shared/time-source';
import { dayLabel, HomeData, HomeDataService } from './home-data';

/**
 * Home works like a departure board (U01): the next first lesson and, from #58 on, the leave-by time fit above
 * the fold on a 360 px screen. Everything shown is real data; missing parts say what is missing.
 */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink, TimeSourceLabel],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage implements OnInit {
  private readonly data = inject(HomeDataService);
  protected readonly home = signal<HomeData | null>(null);
  protected readonly feed = toSignal(inject(FeedHealth).linzAg);
  protected readonly dayLabel = dayLabel;

  async ngOnInit() {
    this.home.set(await this.data.load());
  }

  protected feedText(state: FeedState | undefined): string {
    switch (state) {
      case 'OK': return 'Working';
      case 'STALE': return 'No update for over five minutes';
      case 'DOWN': return 'Not available right now';
      case 'UNKNOWN': return 'Starting up';
      case 'OFFLINE': return 'LeoWay server not reachable';
      default: return 'Checking';
    }
  }
}
