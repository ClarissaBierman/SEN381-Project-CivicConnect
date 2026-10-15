import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ClientProfile, ProfileChange } from './models';
import { toProblem } from './request-api.service';

// The requester's own account (GET/PATCH /clients/me).
@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  private readonly http = inject(HttpClient);

  getProfile(): Observable<ClientProfile> {
    return this.http.get<ClientProfile>('/api/v1/clients/me').pipe(catchError((e) => this.fail(e)));
  }

  updateProfile(change: ProfileChange): Observable<ClientProfile> {
    return this.http.patch<ClientProfile>('/api/v1/clients/me', change).pipe(catchError((e) => this.fail(e)));
  }

  private fail(err: HttpErrorResponse): Observable<never> {
    return throwError(() => toProblem(err));
  }
}
