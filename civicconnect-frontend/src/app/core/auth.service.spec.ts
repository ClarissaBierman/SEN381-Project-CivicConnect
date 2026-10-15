import { HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { CurrentUser } from './models';
import { SessionService } from './session.service';

describe('AuthService', () => {
  let auth: AuthService;
  let session: SessionService;
  let http: HttpTestingController;

  const loginOk = {
    token: 'abc.def.ghi',
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the API role STAFF and sends the token on /auth/me', () => {
    let user: CurrentUser | undefined;
    auth.login({ email: 'thabo@civic.test', password: 'Secret123' }).subscribe((u) => (user = u));

    http.expectOne('/api/v1/auth/login').flush({ ...loginOk, role: 'STAFF' });
    const me = http.expectOne('/api/v1/auth/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    me.flush({
      id: 7,
      role: 'STAFF',
      firstName: 'Thabo',
      surname: 'Mokoena',
      email: 'thabo@civic.test',
    });

    expect(user).toEqual({ role: 'STAFF', badge: 'Staff - T. Mokoena', employeeId: 7 });
    expect(session.user()?.role).toBe('STAFF');
  });

  it('keeps MANAGEMENT and gives REQUESTER a requester number', () => {
    auth.login({ email: 'a@civic.test', password: 'Secret123' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ ...loginOk, role: 'MANAGEMENT' });
    http
      .expectOne('/api/v1/auth/me')
      .flush({
        id: 3,
        role: 'MANAGEMENT',
        firstName: 'Anita',
        surname: 'Naidoo',
        email: 'a@civic.test',
      });
    expect(session.user()).toEqual({
      role: 'MANAGEMENT',
      badge: 'Manager - A. Naidoo',
      employeeId: 3,
    });

    session.signOut();
    auth.login({ email: 'r@civic.test', password: 'Secret123' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ ...loginOk, role: 'REQUESTER' });
    http
      .expectOne('/api/v1/auth/me')
      .flush({
        id: 10234,
        role: 'REQUESTER',
        firstName: 'Lebo',
        surname: 'Dube',
        email: 'r@civic.test',
      });
    expect(session.user()).toEqual({
      role: 'REQUESTER',
      badge: 'Requester #10234',
      requesterNumber: 10234,
    });
  });

  it('does not start a session when the password is wrong (401)', () => {
    let failed: HttpErrorResponse | undefined;
    auth
      .login({ email: 'r@civic.test', password: 'wrong' })
      .subscribe({ error: (e) => (failed = e) });

    http
      .expectOne('/api/v1/auth/login')
      .flush(
        {
          status: 401,
          code: 'UNAUTHENTICATED',
          title: 'Not logged in',
          detail: 'Invalid email or password',
        },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(failed?.status).toBe(401);
    expect(session.user()).toBeNull();
    expect(session.token()).toBeNull();
  });

  it('logs the user out when the API rejects an expired token', () => {
    session.start('old.token', loginOk.expiresAt, {
      role: 'REQUESTER',
      badge: 'Requester #1',
      requesterNumber: 1,
    });

    auth
      .register({ firstName: 'x', surname: 'y', email: 'x@y.z', phone: null, password: '12345678' })
      .subscribe({ error: () => {} });
    http
      .expectOne('/api/v1/auth/register')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(session.user()).toBeNull();
    expect(session.token()).toBeNull();
  });
});
