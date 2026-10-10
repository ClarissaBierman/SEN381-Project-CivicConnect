import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ApiProblem } from './models';
import { RequestApiService } from './request-api.service';
import { SessionService } from './session.service';

// These pin down the contract behaviour the screens rely on (PED 21.4 / 21.5).
// When the real HttpClient calls replace the in-memory version, the same cases move to backend tests.
describe('RequestApiService (in-memory contract)', () => {
  let api: RequestApiService;
  let session: SessionService;

  const problemOf = async (p: Promise<unknown>): Promise<ApiProblem> => {
    try {
      await p;
    } catch (e) {
      return e as ApiProblem;
    }
    throw new Error('expected the call to fail');
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    api = TestBed.inject(RequestApiService);
    session = TestBed.inject(SessionService);
  });

  it('FR-001: rejects a request without a location with 400 fieldErrors', async () => {
    session.signInAs('REQUESTER');
    const p = await problemOf(
      firstValueFrom(api.submitRequest({ categoryId: 1, description: 'Leak', location: ' ', attachmentUrl: null })),
    );
    expect(p.status).toBe(400);
    expect(p.fieldErrors?.map((f) => f.field)).toEqual(['location']);
  });

  it('FR-001: a new request starts as NEW with a reference number', async () => {
    session.signInAs('REQUESTER');
    const created = await firstValueFrom(
      api.submitRequest({ categoryId: 1, description: 'Leak', location: 'Block A', attachmentUrl: null }),
    );
    expect(created.status).toBe('NEW');
    expect(created.refNum).toMatch(/^CC-\d{4}-\d{5}$/);
  });

  it('FR-002: a requester cannot see another client\'s request (404, not 403)', async () => {
    session.signInAs('REQUESTER');
    const p = await problemOf(firstValueFrom(api.getRequest(28))); // belongs to #10377
    expect(p.status).toBe(404);
  });

  it('FR-005: a handler only sees requests in their categories', async () => {
    session.signInAs('HANDLER'); // T. Mokoena: Plumbing + Electrical
    const page = await firstValueFrom(
      api.getRequests({ status: null, categoryId: null, from: null, to: null, sort: 'createdAt,desc', page: 0, size: 20 }),
    );
    expect(page.content.every((r) => r.categoryName !== 'IT support')).toBe(true);
  });

  it('FR-009: an illegal transition is refused with 422 INVALID_TRANSITION', async () => {
    session.signInAs('HANDLER');
    const d = await firstValueFrom(api.getRequest(31)); // ASSIGNED
    expect(d.allowedTransitions).toEqual(['IN_PROGRESS', 'REJECTED']);
    const p = await problemOf(firstValueFrom(api.changeStatus(31, 'CLOSED', d.version)));
    expect(p.code).toBe('INVALID_TRANSITION');
  });

  it('DEC-006: a stale version is refused with 409 VERSION_CONFLICT', async () => {
    session.signInAs('HANDLER');
    const d = await firstValueFrom(api.getRequest(31));
    const p = await problemOf(firstValueFrom(api.changeStatus(31, 'IN_PROGRESS', d.version - 1)));
    expect(p.code).toBe('VERSION_CONFLICT');
  });

  it('FR-008: accepting an already assigned request returns 409 ALREADY_ASSIGNED', async () => {
    session.signInAs('HANDLER');
    const d = await firstValueFrom(api.getRequest(31));
    const p = await problemOf(firstValueFrom(api.assign(31, d.version)));
    expect(p.code).toBe('ALREADY_ASSIGNED');
  });

  it('FR-012: activity report is Management only', async () => {
    session.signInAs('HANDLER');
    const p = await problemOf(firstValueFrom(api.getActivity(null, null, null, null)));
    expect(p.status).toBe(403);
  });
});
