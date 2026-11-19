import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api-client';

/** A saved route (`/api/me/routes`). Stops are ids; no addresses or coordinates are stored. */
export interface SavedRoute {
  id: number;
  homeStopId: string;
  schoolStopId: string;
  walkToStopMinutes: number;
  walkToSchoolMinutes: number;
  bufferMinutes: number;
}

export type RouteInput = Omit<SavedRoute, 'id'>;

@Injectable({ providedIn: 'root' })
export class RoutesApi {
  private readonly api = inject(ApiClient);

  list(): Observable<SavedRoute[]> {
    return this.api.get<SavedRoute[]>('/api/me/routes');
  }

  create(route: RouteInput): Observable<SavedRoute> {
    return this.api.post<SavedRoute>('/api/me/routes', route);
  }

  update(id: number, route: RouteInput): Observable<SavedRoute> {
    return this.api.put<SavedRoute>(`/api/me/routes/${id}`, route);
  }

  delete(id: number): Observable<void> {
    return this.api.delete(`/api/me/routes/${id}`);
  }
}
