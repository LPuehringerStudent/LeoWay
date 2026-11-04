import { HttpClient } from '@angular/common/http';
import { inject, Injectable, InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Base URL of the LeoWay API, without a trailing slash. Empty means same origin. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});

/** Shared HTTP client for the LeoWay API. Feature services build on this instead of HttpClient. */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL).replace(/\/+$/, '');

  /** Absolute or same-origin URL for an API path such as `/api/health/ready`. */
  url(path: string): string {
    return this.baseUrl + (path.startsWith('/') ? path : `/${path}`);
  }

  get<T>(path: string, options: { timeout?: number } = {}): Observable<T> {
    return this.http.get<T>(this.url(path), options);
  }

  getText(path: string, options: { timeout?: number } = {}): Observable<string> {
    return this.http.get(this.url(path), { ...options, responseType: 'text' });
  }
}
