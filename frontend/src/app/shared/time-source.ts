import { Component, input } from '@angular/core';

/** Where a shown time comes from (U03): a live prediction or the plain timetable. */
export type TimeSource = 'prediction' | 'timetable';

/** Small label next to every time, so students can tell predictions from timetable times (U03). */
@Component({
  selector: 'app-time-source',
  template: `<span class="tag" [class.tag--timetable]="source() === 'timetable'">{{ text() }}</span>`,
  styles: `
    .tag {
      display: inline-block;
      padding: 0.05rem 0.4rem;
      border-radius: 0.25rem;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.4;
      background: var(--prediction-bg);
      color: var(--prediction-fg);
      white-space: nowrap;
    }
    .tag--timetable {
      background: var(--timetable-bg);
      color: var(--timetable-fg);
    }
  `,
})
export class TimeSourceLabel {
  readonly source = input.required<TimeSource>();
  protected text = () => (this.source() === 'prediction' ? 'Prediction' : 'Timetable only');
}
