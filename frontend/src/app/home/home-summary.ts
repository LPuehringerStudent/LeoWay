import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { TimeSource } from '../shared/time-source';

/** Everything Home shows for today (spec section 2 sketch). Times are local "HH:mm" strings. */
export interface HomeSummary {
  lastSync: string;
  firstLesson: { start: string; subject: string };
  leaveBy: { time: string; source: TimeSource };
  line: { name: string; departure: string; delayMinutes: number | null; source: TimeSource };
}

/** Mock data until the timetable (#7) and the leave-by calculation exist. */
export const MOCK_HOME_SUMMARY: HomeSummary = {
  lastSync: '05:45',
  firstLesson: { start: '08:55', subject: 'Mathematics' },
  leaveBy: { time: '07:42', source: 'prediction' },
  line: { name: 'Bus 19', departure: '07:51', delayMinutes: 6, source: 'prediction' },
};

@Injectable({ providedIn: 'root' })
export class HomeSummaryService {
  readonly today: Observable<HomeSummary> = of(MOCK_HOME_SUMMARY);
}
