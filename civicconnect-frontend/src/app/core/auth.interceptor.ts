import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  const router = inject(Router);

  const token = session.token();
  const isApi = req.url.startsWith('/api/');
  const outgoing =
    token && isApi ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(outgoing).pipe(
    catchError((err: HttpErrorResponse) => {
      const isLogin = req.url.endsWith('/auth/login');
      if (err.status === 401 && isApi && !isLogin) {
        // B1: a deactivated account gets its own message instead of "session expired"
        const deactivated = /no longer active/i.test(err.error?.detail ?? '');
        session.signOut();
        router.navigate(['/login'], { queryParams: deactivated ? { deactivated: 1 } : { expired: 1 } });
      }
      return throwError(() => err);
    }),
  );
};
