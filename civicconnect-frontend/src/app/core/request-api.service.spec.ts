import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ApiProblem } from './models';
import { RequestApiService } from './request-api.service';

// The mapping between the real API and the shapes the screens use, and the error contract (PED 21.4 / 21.5).
// The business rules themselves are tested in the backend.
describe('RequestApiService (HTTP mapping)', () => {
  let api: RequestApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(RequestApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const serverDetail = {
    id: 31, refNum: 'CC-2026-000031', categoryId: 4, categoryName: 'IT Support', status: 'IN_PROGRESS',
    priority: 'HIGH', location: 'Office 3', handlerId: 3, handlerName: 'Ayesha Patel',
    createdAt: '2026-10-10T08:00:00Z', updatedAt: '2026-10-12T09:30:00Z', closedAt: null,
    description: 'Printer jammed', attachmentUrl: null, requesterId: 1,
    allowedTransitions: ['RESOLVED'],
    statusHistory: [
      { status: 'NEW', at: '2026-10-10T08:00:00Z' },
      { status: 'ASSIGNED', at: '2026-10-10T09:00:00Z' },
      { status: 'IN_PROGRESS', at: '2026-10-12T09:30:00Z' },
    ],
    comments: [
      { id: 1, authorName: 'Ayesha Patel', content: 'Part ordered', visibleToRequester: true, createdAt: '2026-10-11T10:00:00Z' },
      { id: 2, authorName: 'Ayesha Patel', content: 'Supplier slow', visibleToRequester: false, createdAt: '2026-10-12T09:30:00Z' },
    ],
  };

  it('API-06: maps the detail, and builds the requester updates from the status history and visible notes only', async () => {
    const result = firstValueFrom(api.getRequest(31));
    http.expectOne('/api/v1/requests/31').flush(serverDetail);
    const d = await result;

    expect(d.requesterNumber).toBe(1);
    expect(d.handlerName).toBe('Ayesha Patel');
    expect(d.comments.map((c) => c.author)).toEqual(['Ayesha Patel', 'Ayesha Patel']);
    expect(d.updates.map((u) => u.text)).toEqual(['Status: In Progress', 'Note: Part ordered', 'Status: Assigned', 'Request submitted']);
  });

  it('API-07: sends filters and sort, and flattens Spring\'s page object', async () => {
    const result = firstValueFrom(
      api.getRequests({ status: 'NEW', categoryId: 4, from: '2026-10-01', to: null, sort: 'createdAt,desc', page: 1, size: 20 }),
    );
    const req = http.expectOne((r) => r.url === '/api/v1/requests');
    expect(req.request.params.get('status')).toBe('NEW');
    expect(req.request.params.get('categoryId')).toBe('4');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    expect(req.request.params.has('to')).toBe(false);
    req.flush({ content: [], page: { size: 20, number: 1, totalElements: 21, totalPages: 2 } });

    const page = await result;
    expect(page).toEqual({ content: [], page: 1, size: 20, totalElements: 21, totalPages: 2 });
  });

  it('API-07: open and unassigned are only sent when set', async () => {
    const result = firstValueFrom(
      api.getRequests({ status: null, open: true, unassigned: true, categoryId: null, from: null, to: '2026-09-30', sort: 'priority,desc', page: 0, size: 1 }),
    );
    const req = http.expectOne((r) => r.url === '/api/v1/requests');
    expect(req.request.params.get('open')).toBe('true');
    expect(req.request.params.get('unassigned')).toBe('true');
    expect(req.request.params.get('to')).toBe('2026-09-30');
    expect(req.request.params.has('from')).toBe(false);
    req.flush({ content: [], page: { size: 1, number: 0, totalElements: 4, totalPages: 4 } });
    expect((await result).totalElements).toBe(4);
  });

  it('API-08: a handler accepts with no body; a manager sends the employeeId', async () => {
    const self = firstValueFrom(api.assign(31));
    const r1 = http.expectOne('/api/v1/requests/31/assignment');
    expect(r1.request.body).toBeNull();
    r1.flush(serverDetail);
    await self;

    const byManager = firstValueFrom(api.assign(31, 3));
    const r2 = http.expectOne('/api/v1/requests/31/assignment');
    expect(r2.request.body).toEqual({ employeeId: 3 });
    r2.flush(serverDetail);
    await byManager;
  });

  it('errors reach the screens as the PED error body, e.g. 409 ALREADY_ASSIGNED and 429 with its detail', async () => {
    const conflict = firstValueFrom(api.assign(31)).catch((e: ApiProblem) => e);
    http.expectOne('/api/v1/requests/31/assignment').flush(
      { status: 409, code: 'ALREADY_ASSIGNED', title: 'Already assigned', detail: 'This request is already assigned.', fieldErrors: [] },
      { status: 409, statusText: 'Conflict' },
    );
    expect(((await conflict) as ApiProblem).code).toBe('ALREADY_ASSIGNED');

    const limited = firstValueFrom(api.getMyRequests()).catch((e: ApiProblem) => e);
    http.expectOne((r) => r.url === '/api/v1/requests/mine').flush(
      { status: 429, code: 'TOO_MANY_REQUESTS', title: 'Too many requests', detail: 'Please wait 3 seconds and try again.' },
      { status: 429, statusText: 'Too Many Requests' },
    );
    expect(((await limited) as ApiProblem).detail).toContain('wait 3 seconds');
  });

  it('a network failure becomes SERVICE_UNAVAILABLE', async () => {
    const down = firstValueFrom(api.getRequest(1)).catch((e: ApiProblem) => e);
    http.expectOne('/api/v1/requests/1').error(new ProgressEvent('error'), { status: 0 });
    expect(((await down) as ApiProblem).code).toBe('SERVICE_UNAVAILABLE');
  });

  it('API-17: flattens the report totals for the tiles', async () => {
    const result = firstValueFrom(api.getActivity('2026-10-01', '2026-10-31', null, null));
    const req = http.expectOne((r) => r.url === '/api/v1/reports/activity');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    req.flush({
      generatedAt: '2026-10-14T12:00:00Z',
      totals: { total: 7, open: 3, overdue: 2, resolved: 3, closed: 0, rejected: 1 },
      byCategory: [],
    });
    const r = await result;
    expect(r.open).toBe(3);
    expect(r.overdue).toBe(2);
  });

  it('API-12: sets the priority without a version number', async () => {
    const result = firstValueFrom(api.setPriority(31, 'LOW'));
    const req = http.expectOne('/api/v1/requests/31/priority');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ priority: 'LOW' });
    req.flush({ ...serverDetail, priority: 'LOW' });
    expect((await result).priority).toBe('LOW');
  });
});
