import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ClientProfile, ProfileChange } from './models';
import { toProblem } from './request-api.service';

// The logged-in user's own account: a requester's profile (GET/PATCH /clients/me) and an employee's
// own password (PATCH /employees/me/password, API-26).
@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  private readonly http = inject(HttpClient);

  getProfile(): Observable<ClientProfile> {
    return this.http.get<ClientProfile>('/api/v1/clients/me').pipe(catchError((e) => this.fail(e)));
  }

  updateProfile(change: ProfileChange): Observable<ClientProfile> {
    return this.http.patch<ClientProfile>('/api/v1/clients/me', change).pipe(catchError((e) => this.fail(e)));
  }

  // Staff and managers: the current password is always required; 204 on success.
  changeEmployeePassword(currentPassword: string, password: string): Observable<void> {
    return this.http
      .patch<void>('/api/v1/employees/me/password', { currentPassword, password })
      .pipe(catchError((e) => this.fail(e)));
  }

  private fail(err: HttpErrorResponse): Observable<never> {
    return throwError(() => toProblem(err));
  }
}
