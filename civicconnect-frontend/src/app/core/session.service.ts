import { Injectable, signal } from '@angular/core';
import { CurrentUser, Role } from './models';

// TEMPORARY: development sign-in so the role-based screens can be shown before JWT auth exists.
// Replace with POST /api/v1/auth/login (API-02) + GET /auth/me (API-03) when DEC-010 is implemented.
// The menu only hides screens - the API must still check the role on every call (ASR-001).
const DEV_USERS: Record<Role, CurrentUser> = {
  REQUESTER: { role: 'REQUESTER', badge: 'Requester #10234', requesterNumber: 10234 },
  HANDLER: { role: 'HANDLER', badge: 'Handler - T. Mokoena', employeeId: 1 },
  MANAGER: { role: 'MANAGER', badge: 'Manager - A. Naidoo', employeeId: 3 },
};

const STORAGE_KEY = 'cc.devRole';

export const HOME_ROUTE: Record<Role, string> = {
  REQUESTER: '/requests/new',
  HANDLER: '/staff/requests',
  MANAGER: '/management/activity',
};

@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly user = signal<CurrentUser | null>(this.restore());

  signInAs(role: Role): void {
    this.user.set(DEV_USERS[role]);
    try {
      sessionStorage.setItem(STORAGE_KEY, role);
    } catch {
      // storage blocked - session just won't survive a refresh
    }
  }

  signOut(): void {
    this.user.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {}
  }

  private restore(): CurrentUser | null {
    try {
      const role = sessionStorage.getItem(STORAGE_KEY) as Role | null;
      return role && DEV_USERS[role] ? DEV_USERS[role] : null;
    } catch {
      return null;
    }
  }
}
