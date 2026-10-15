import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';
import { AuthResponse, LoginBody, MeResponse, RegisterBody } from './auth.models';
import { CurrentUser } from './models';
import { SessionService } from './session.service';

export function toCurrentUser(me: MeResponse): CurrentUser {
  const role = me.role;
  const shortName = `${me.firstName.charAt(0)}. ${me.surname}`;
  if (role === 'REQUESTER') {
    return { role, badge: `Requester #${me.id}`, requesterNumber: me.id };
  }
  return {
    role,
    badge: `${role === 'MANAGEMENT' ? 'Manager' : 'Staff'} - ${shortName}`,
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
