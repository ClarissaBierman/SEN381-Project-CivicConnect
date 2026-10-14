import { Injectable, signal } from '@angular/core';
import { CurrentUser, Role } from './models';

const TOKEN_KEY = 'cc.token';
const EXPIRES_KEY = 'cc.expiresAt';
const USER_KEY = 'cc.user';

export const HOME_ROUTE: Record<Role, string> = {
  REQUESTER: '/requests/new',
  HANDLER: '/staff/requests',
  MANAGER: '/management/activity',
};

// Used by the unit tests and by screens that still run on the in-memory RequestApiService.
const DEV_USERS: Record<Role, CurrentUser> = {
  REQUESTER: { role: 'REQUESTER', badge: 'Requester #10234', requesterNumber: 10234 },
  HANDLER: { role: 'HANDLER', badge: 'Handler - T. Mokoena', employeeId: 1 },
  MANAGER: { role: 'MANAGER', badge: 'Manager - A. Naidoo', employeeId: 3 },
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
    this.user.set(DEV_USERS[role]);
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
      this.tokenValue.set(token);
      this.user.set(JSON.parse(user) as CurrentUser);
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
