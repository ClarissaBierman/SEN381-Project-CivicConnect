import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';
import { AuthResponse, BackendRole, LoginBody, MeResponse, RegisterBody } from './auth.models';
import { CurrentUser, Role } from './models';
import { SessionService } from './session.service';

// The API calls staff STAFF and MANAGEMENT, the screens use HANDLER and MANAGER.
// Translating here means no screen or route guard has to change.
export const ROLE_FROM_API: Record<BackendRole, Role> = {
  REQUESTER: 'REQUESTER',
  STAFF: 'HANDLER',
  MANAGEMENT: 'MANAGER',
};

export function toCurrentUser(me: MeResponse): CurrentUser {
  const role = ROLE_FROM_API[me.role];
  const shortName = `${me.firstName.charAt(0)}. ${me.surname}`;
  if (role === 'REQUESTER') {
    return { role, badge: `Requester #${me.id}`, requesterNumber: me.id };
  }
  return {
    role,
    badge: `${role === 'MANAGER' ? 'Manager' : 'Handler'} - ${shortName}`,
    employeeId: me.id,
  };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);

  login(body: LoginBody): Observable<CurrentUser> {
    return this.http.post<AuthResponse>('/api/v1/auth/login', body).pipe(
      tap((auth) => this.session.storeToken(auth.token, auth.expiresAt)),
      switchMap((auth) =>
        this.http.get<MeResponse>('/api/v1/auth/me').pipe(
          map(toCurrentUser),
          tap((user) => this.session.start(auth.token, auth.expiresAt, user)),
        ),
      ),
    );
  }

  register(body: RegisterBody): Observable<unknown> {
    return this.http.post('/api/v1/auth/register', body);
  }
}
