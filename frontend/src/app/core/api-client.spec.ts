import { TestBed } from '@angular/core/testing';
import { API_BASE_URL, ApiClient } from './api-client';

describe('ApiClient', () => {
  const client = (base: string) => {
    TestBed.configureTestingModule({ providers: [{ provide: API_BASE_URL, useValue: base }] });
    return TestBed.inject(ApiClient);
  };

  it('uses the same origin by default', () => {
    expect(client('').url('/api/health/ready')).toBe('/api/health/ready');
  });

  it('prefixes an absolute base URL without double slashes', () => {
    expect(client('https://leoway.example/').url('api/x')).toBe('https://leoway.example/api/x');
  });
});
