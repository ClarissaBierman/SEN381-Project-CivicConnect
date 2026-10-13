import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RegisterBody } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  register(body: RegisterBody): Observable<unknown> {
    return this.http.post('/api/v1/auth/register', body);
  }
}
