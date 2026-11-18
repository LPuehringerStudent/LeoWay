import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { RoutesApi, SavedRoute } from '../core/routes-api';
import { Stop, StopsApi } from '../core/stops-api';
import { StopPicker } from './stop-picker';

export interface RoutePreview {
  home: Stop | null;
  school: Stop | null;
  walkToStopMinutes: number;
  walkToSchoolMinutes: number;
  bufferMinutes: number;
}

@Component({
  selector: 'app-route-editor',
  imports: [ReactiveFormsModule, StopPicker, RouterLink],
  templateUrl: './route-editor.html',
  styleUrl: './route-editor.css',
})
export class RouteEditor implements OnInit {
  readonly route = input<SavedRoute | null>(null);
  readonly saved = output<SavedRoute>();
  readonly cancelled = output<void>();
  readonly previewChange = output<RoutePreview>();
  private readonly api = inject(RoutesApi);
  private readonly stops = inject(StopsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly errorBox = viewChild<ElementRef<HTMLElement>>('errorBox');
  protected readonly home = signal<Stop | null>(null);
  protected readonly school = signal<Stop | null>(null);
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly errors = signal<string[]>([]);
  protected readonly form = this.fb.group({
    walkToStopMinutes: [
      5,
      [Validators.required, Validators.min(0), Validators.max(60), Validators.pattern(/^\d+$/)],
    ],
    walkToSchoolMinutes: [
      5,
      [Validators.required, Validators.min(0), Validators.max(60), Validators.pattern(/^\d+$/)],
    ],
    bufferMinutes: [
      10,
      [Validators.required, Validators.min(0), Validators.max(30), Validators.pattern(/^\d+$/)],
    ],
  });

  ngOnInit() {
    const route = this.route();
    if (route) {
      this.form.patchValue(route);
      this.home.set(
        this.stops.get(route.homeStopId) ?? {
          id: route.homeStopId,
          name: `Stop ${route.homeStopId}`,
        },
      );
      this.school.set(
        this.stops.get(route.schoolStopId) ?? {
          id: route.schoolStopId,
          name: `Stop ${route.schoolStopId}`,
        },
      );
      this.stops
        .resolve(route.homeStopId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((stop) => {
          if (stop && this.home()?.id === stop.id) {
            this.home.set(stop);
            this.preview();
          }
        });
      this.stops
        .resolve(route.schoolStopId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((stop) => {
          if (stop && this.school()?.id === stop.id) {
            this.school.set(stop);
            this.preview();
          }
        });
    }
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.preview());
    this.preview();
  }

  protected select(which: 'home' | 'school', stop: Stop | null) {
    this[which].set(stop);
    this.preview();
  }

  private preview() {
    this.previewChange.emit({
      home: this.home(),
      school: this.school(),
      ...this.form.getRawValue(),
    });
  }

  protected async save() {
    if (this.busy()) return;
    this.submitted.set(true);
    const home = this.home();
    const school = this.school();
    const errors: string[] = [];
    if (!home) errors.push('Choose your home stop from the suggestions.');
    if (!school) errors.push('Choose your school stop from the suggestions.');
    if (home && home.id === school?.id) errors.push('Choose two different stops.');
    for (const [key, label, max] of [
      ['walkToStopMinutes', 'Walk to the stop', 60],
      ['walkToSchoolMinutes', 'Walk to school', 60],
      ['bufferMinutes', 'Extra buffer', 30],
    ] as const) {
      if (this.form.controls[key].invalid)
        errors.push(`${label} must be a whole number from 0 to ${max} minutes.`);
    }
    this.errors.set(errors);
    if (errors.length || !home || !school) {
      this.focusErrors();
      return;
    }
    this.busy.set(true);
    try {
      const input = { homeStopId: home.id, schoolStopId: school.id, ...this.form.getRawValue() };
      const route = this.route();
      const saved = await firstValueFrom(
        route ? this.api.update(route.id, input) : this.api.create(input),
      );
      this.saved.emit(saved);
    } catch (e) {
      const error = e instanceof HttpErrorResponse ? e : null;
      const messages: unknown = error?.error?.errors;
      this.errors.set(
        Array.isArray(messages) && messages.every((m) => typeof m === 'string')
          ? messages.map((m) =>
              m
                .replaceAll('walkToStopMinutes', 'Walk to the stop')
                .replaceAll('walkToSchoolMinutes', 'Walk to school')
                .replaceAll('bufferMinutes', 'Extra buffer')
                .replaceAll('homeStopId', 'Home stop')
                .replaceAll('schoolStopId', 'School stop'),
            )
          : [
              error?.status === 401 || error?.status === 403
                ? 'Your session ended. Please log in again.'
                : error?.status === 404
                  ? 'This route no longer exists. Cancel and reload your routes.'
                  : 'Your route could not be saved. Your choices are still here. Please try again.',
            ],
      );
      this.focusErrors();
    } finally {
      this.busy.set(false);
    }
  }

  private focusErrors() {
    setTimeout(() => this.errorBox()?.nativeElement.focus());
  }
}
