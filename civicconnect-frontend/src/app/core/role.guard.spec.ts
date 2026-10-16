import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Role } from './models';
import { roleGuard } from './role.guard';
import { SessionService } from './session.service';

describe('roleGuard', () => {
  const visit = (as: Role | null, allowed: Role[]) => {
    const session = TestBed.inject(SessionService);
    if (as) session.signInAs(as);
    else session.signOut();
    const route = { data: { roles: allowed } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot));
  };
  const url = (result: unknown) => (result as UrlTree).toString();

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('sends a requester who opens a management page back to their home page', () => {
    expect(url(visit('REQUESTER', ['MANAGEMENT']))).toBe('/requests/new');
  });

  it('lets staff and managers open My account, but not requesters', () => {
    expect(visit('STAFF', ['STAFF', 'MANAGEMENT'])).toBe(true);
    expect(visit('MANAGEMENT', ['STAFF', 'MANAGEMENT'])).toBe(true);
    expect(url(visit('REQUESTER', ['STAFF', 'MANAGEMENT']))).toBe('/requests/new');
  });

  it('sends a visitor who is not logged in to the login page', () => {
    expect(url(visit(null, ['MANAGEMENT']))).toBe('/login');
  });
});
