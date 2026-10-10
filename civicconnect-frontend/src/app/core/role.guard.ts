import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from './models';
import { HOME_ROUTE, SessionService } from './session.service';

// Hides screens from the wrong role. This is usability only - the API re-checks the role on
// every call (ASR-001, DEC-010), so a user who bypasses the guard still gets 403.
export const roleGuard: CanActivateFn = (route) => {
  const user = inject(SessionService).user();
  const router = inject(Router);
  if (!user) return router.parseUrl('/login');
  const roles = (route.data['roles'] ?? []) as Role[];
  return roles.includes(user.role) ? true : router.parseUrl(HOME_ROUTE[user.role]);
};
