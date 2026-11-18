import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Session } from './session';

describe('Session', () => {
  let session: Session;
  let http: HttpTestingController;
  const flush = () => new Promise((r) => setTimeout(r));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(Session);
    http = TestBed.inject(HttpTestingController);
  });

  it('posts username and key form-encoded and loads the account', async () => {
    const result = session.login('max.muster', 'KEY123');
    const req = http.expectOne('/j_security_check');
    expect(req.request.body).toBe('j_username=max.muster&j_password=KEY123');
    req.flush('');
    await flush();
    http.expectOne('/api/session').flush({ username: 'max.muster', admin: false });
    expect(await result).toBe('ok');
    expect(session.me()?.username).toBe('max.muster');
  });

  it('reports a rejected key when WebUntis is reachable', async () => {
    const result = session.login('max.muster', 'WRONG');
    http.expectOne('/j_security_check').flush('', { status: 401, statusText: 'Unauthorized' });
    await flush();
    http.expectOne('/api/session/webuntis-reachable').flush({ webUntisReachable: true });
    expect(await result).toBe('rejected');
  });

  it('recognizes an administrator without calling the student account API', async () => {
    const result = session.refresh();
    http.expectOne('/api/session').flush({ username: 'root.admin', admin: true });
    expect((await result)?.admin).toBe(true);
    http.expectNone('/api/me');
  });

  it('does not restore an old session when a request finishes after logout', async () => {
    const refresh = session.refresh();
    const oldRequest = http.expectOne('/api/session');
    const logout = session.logout();
    http.expectOne('/api/session/logout').flush(null);
    await logout;
    oldRequest.flush({ username: 'root.admin', admin: true });
    expect(await refresh).toBeNull();
    expect(session.me()).toBeNull();
  });

  it('reports unavailable when WebUntis is down', async () => {
    const result = session.login('max.muster', 'KEY123');
    http.expectOne('/j_security_check').flush('', { status: 401, statusText: 'Unauthorized' });
    await flush();
    http.expectOne('/api/session/webuntis-reachable').flush({ webUntisReachable: false });
    expect(await result).toBe('unavailable');
  });

  it('never writes the key to storage', async () => {
    localStorage.clear();
    sessionStorage.clear();
    session.login('max.muster', 'KEY123');
    http.expectOne('/j_security_check').flush('');
    await flush();
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('KEY123');
  });
});
