import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AlertChannel } from '../core/alerts-api';
import { AlertsCard, errorText } from './alerts-card';

const URL = 'https://discord.com/api/webhooks/1234567890123456789/secret-token';
const ON: AlertChannel = { kind: 'DISCORD', working: true, since: '2026-11-18T18:00:00Z', brokenAt: null };

async function render(channels: AlertChannel[]) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AlertsCard);
  fixture.detectChanges();
  http.expectOne('/api/me/alerts').flush(channels);
  const page = fixture.nativeElement as HTMLElement;
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  await settle();
  const text = () => page.textContent?.replace(/\s+/g, ' ') ?? '';
  return { fixture, http, page, text, settle };
}

describe('AlertsCard', () => {
  it('saves a webhook, clears the input and never shows the URL again', async () => {
    const { http, page, text, settle } = await render([]);
    const input = page.querySelector<HTMLInputElement>('#webhook')!;
    expect(input.type).toBe('password');
    input.value = URL;
    input.dispatchEvent(new Event('input'));
    await settle();
    page.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/me/alerts/discord');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ webhookUrl: URL });
    req.flush(ON);
    await settle();
    expect(text()).toContain('Discord: on');
    expect(text()).toContain('test message is on its way');
    expect(page.innerHTML).not.toContain('secret-token');
    expect(page.querySelector('#webhook')).toBeNull();
    http.verify();
  });

  it('shows a broken channel and asks for a new webhook', async () => {
    const { page, text } = await render([{ ...ON, working: false, brokenAt: '2026-11-18T19:00:00Z' }]);
    expect(text()).toContain('no longer accepts this webhook');
    const testButton = [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Send test'));
    expect(testButton?.disabled).toBe(true);
  });

  it('marks the channel broken when the test message is refused', async () => {
    const { http, page, text, settle } = await render([ON]);
    [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Send test'))!.click();
    http.expectOne('/api/me/alerts/discord/test').flush(
      { errors: ['Discord no longer accepts this webhook. Paste a new webhook URL.'] },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();
    http.expectOne('/api/me/alerts').flush([{ ...ON, working: false, brokenAt: '2026-11-18T19:00:00Z' }]);
    await vi.waitFor(async () => {
      await settle();
      expect(text()).toContain('It was probably deleted');
    });
    expect(text()).toContain('Paste a new webhook URL');
    http.verify();
  });

  it('turns Discord alerts off', async () => {
    const { http, page, text, settle } = await render([ON]);
    [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Turn off'))!.click();
    const req = http.expectOne('/api/me/alerts/discord');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(text()).toContain('Discord alerts removed');
    expect(page.querySelector('#webhook')).not.toBeNull();
  });
});

describe('errorText', () => {
  const err = (status: number, error: unknown = null, headers = new HttpHeaders()) =>
    new HttpErrorResponse({ status, error, headers });
  it('explains every failure', () => {
    expect(errorText(err(400, { errors: ['webhookUrl is not a Discord webhook URL'] })))
      .toBe('webhookUrl is not a Discord webhook URL');
    expect(errorText(err(429, null, new HttpHeaders({ 'Retry-After': '6' })))).toContain('Wait 6 seconds');
    expect(errorText(err(503))).toContain("Discord can't be reached");
    expect(errorText(new Error('x'))).toContain('Something went wrong');
  });
});
