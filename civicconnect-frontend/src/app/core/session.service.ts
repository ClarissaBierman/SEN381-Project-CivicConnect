import { Injectable, signal } from '@angular/core';
import { CurrentUser, Role } from './models';

const TOKEN_KEY = 'cc.token';
const EXPIRES_KEY = 'cc.expiresAt';
const USER_KEY = 'cc.user';

export const HOME_ROUTE: Record<Role, string> = {
  REQUESTER: '/requests/new',
  STAFF: '/staff/requests',
  MANAGEMENT: '/management/activity',
};

// Test users for the unit tests (signInAs), so a test can show a screen without logging in.
const TEST_USERS: Record<Role, CurrentUser> = {
  REQUESTER: { role: 'REQUESTER', badge: 'Requester #10234', requesterNumber: 10234 },
  STAFF: { role: 'STAFF', badge: 'Staff - T. Mokoena', employeeId: 1 },
  MANAGEMENT: { role: 'MANAGEMENT', badge: 'Manager - A. Naidoo', employeeId: 3 },
};

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly tokenValue = signal<string | null>(null);
  readonly user = signal<CurrentUser | null>(null);

  constructor() {
    this.restore();
  }

  token(): string | null {
    return this.tokenValue();
  }

  start(token: string, expiresAt: string, user: CurrentUser): void {
    this.tokenValue.set(token);
    this.user.set(user);
    this.write(TOKEN_KEY, token);
    this.write(EXPIRES_KEY, expiresAt);
    this.write(USER_KEY, JSON.stringify(user));
  }

  storeToken(token: string, expiresAt: string): void {
    this.tokenValue.set(token);
    this.write(TOKEN_KEY, token);
    this.write(EXPIRES_KEY, expiresAt);
  }

  signInAs(role: Role): void {
    this.tokenValue.set(null);
    this.user.set(TEST_USERS[role]);
  }

  signOut(): void {
    this.tokenValue.set(null);
    this.user.set(null);
    for (const key of [TOKEN_KEY, EXPIRES_KEY, USER_KEY]) {
      try {
        sessionStorage.removeItem(key);
      } catch {}
    }
  }

  private restore(): void {
    try {
      const token = sessionStorage.getItem(TOKEN_KEY);
      const expiresAt = sessionStorage.getItem(EXPIRES_KEY);
      const user = sessionStorage.getItem(USER_KEY);
      if (!token || !expiresAt || !user) return;
      if (new Date(expiresAt).getTime() <= Date.now()) {
        this.signOut();
        return;
      }
      const parsed = JSON.parse(user) as CurrentUser;
      // A session saved under the old role names (HANDLER/MANAGER) is dropped: log in again
      if (!(parsed.role in HOME_ROUTE)) {
        this.signOut();
        return;
      }
      this.tokenValue.set(token);
      this.user.set(parsed);
    } catch {
      this.signOut();
    }
  }

  private write(key: string, value: string): void {
    try {
      sessionStorage.setItem(key, value);
    } catch {}
  }
}
