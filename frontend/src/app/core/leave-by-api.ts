import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api-client';

export type LeaveByStatus =
  | 'IN_TIME'
  | 'NO_BUFFER'
  | 'LATE'
  | 'NO_CONNECTION'
  | 'NO_ROUTE'
  | 'NO_LESSON'
  | 'NOT_CONNECTED'
  | 'NEEDS_RECONNECT';

/**
 * `GET /api/me/leave-by` (#58). Times are Vienna time with their offset, e.g. `2026-11-19T07:17:00+01:00`;
 * they are null when there is nothing to calculate.
 */
export interface LeaveByResult {
  status: LeaveByStatus;
  routeId: number | null;
  lessonStart: string | null;
  leaveAt: string | null;
  atSchool: string | null;
  connection: {
    /** Every line of the trip, "3 > 19" with a change. */
    line: string;
    plannedDeparture: string;
    departure: string;
    plannedArrival: string;
    arrival: string;
  } | null;
  source: 'TIMETABLE' | 'PREDICTION' | null;
  minutesLate: number | null;
  /** Open delay warning for this lesson (F03, #63); only for the first saved route. */
  warning?: DelayWarning | null;
}

/** The connection the timetable chose runs late enough to make the student late (F03). */
export interface DelayWarning {
  line: string;
  plannedDeparture: string;
  delayMinutes: number;
  atSchool: string;
  minutesLate: number;
  exam: boolean;
  /** Another connection: in time, or only arriving earlier (`inTime` false). Null if none helps. */
  alternative: {
    line: string;
    departure: string;
    leaveAt: string;
    atSchool: string;
    inTime: boolean;
  } | null;
}

@Injectable({ providedIn: 'root' })
export class LeaveByApi {
  private readonly api = inject(ApiClient);

  get(): Observable<LeaveByResult> {
    return this.api.get<LeaveByResult>('/api/me/leave-by');
  }
}

/** "07:17" from a Vienna time like 2026-11-19T07:17:00+01:00. The server already sends local time. */
export function clock(time: string): string {
  return time.slice(11, 16);
}
