import { TestBed } from '@angular/core/testing';
import { SessionService } from './session.service';

describe('SessionService', () => {
  beforeEach(() => sessionStorage.clear());

  it('restores a saved session', () => {
    sessionStorage.setItem('cc.token', 't');
    sessionStorage.setItem('cc.expiresAt', new Date(Date.now() + 60_000).toISOString());
    sessionStorage.setItem('cc.user', JSON.stringify({ role: 'STAFF', badge: 'Staff - A. B', employeeId: 3 }));
    expect(TestBed.inject(SessionService).user()?.role).toBe('STAFF');
  });

  it('drops a session saved under the old role names', () => {
    sessionStorage.setItem('cc.token', 't');
    sessionStorage.setItem('cc.expiresAt', new Date(Date.now() + 60_000).toISOString());
    sessionStorage.setItem('cc.user', JSON.stringify({ role: 'HANDLER', badge: 'Handler - A. B', employeeId: 3 }));
    const session = TestBed.inject(SessionService);
    expect(session.user()).toBeNull();
    expect(sessionStorage.getItem('cc.token')).toBeNull();
  });
});
