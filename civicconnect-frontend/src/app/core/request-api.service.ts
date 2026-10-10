import { inject, Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { delay, switchMap } from 'rxjs/operators';
import { Category } from '../category';
import {
  ActivityReport,
  ApiProblem,
  AuditEntry,
  CreatedRequest,
  HandlerOption,
  NewRequest,
  Page,
  Priority,
  RequestComment,
  RequestDetail,
  RequestFilter,
  RequestStatus,
  RequestSummary,
  STATUS_LABELS,
} from './models';
import { SessionService } from './session.service';

/*
 * Client for the Requests endpoints (API-04 to API-13, API-17).
 *
 * TEMPORARY IN-MEMORY IMPLEMENTATION: the backend Requests module is still "Planned" in the
 * PED endpoint contract, so each method below simulates the documented server behaviour
 * (status codes, error codes, scope checks) against seed data. The method signatures match the
 * contract, so when an endpoint is built only the method body changes, e.g.
 *
 *   submitRequest(body) { return this.http.post<CreatedRequest>('/api/v1/requests', body); }
 *
 * The screens do not need to change.
 */

// --- simulated server state ---------------------------------------------------------------

interface StoredRequest {
  id: number;
  refNum: string;
  categoryId: number;
  description: string;
  location: string;
  attachmentUrl: string | null;
  status: RequestStatus;
  priority: Priority | null;
  requesterNumber: number;
  handlerId: number | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  audit: AuditEntry[];
  comments: RequestComment[];
}

interface StoredHandler {
  id: number;
  name: string;
  categoryIds: number[]; // EmployeeCategory scope (DEC-002)
}

// Server-side rule (TransitionPolicy, DEC-011). It lives here ONLY because this class is
// standing in for the server - the real Angular code never decides transitions itself,
// it just renders allowedTransitions from API-06.
const TRANSITIONS: Record<RequestStatus, RequestStatus[]> = {
  NEW: ['ASSIGNED', 'REJECTED'],
  ASSIGNED: ['IN_PROGRESS', 'REJECTED'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
  REJECTED: [],
};

const OPEN: RequestStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS'];
const READ_ONLY: RequestStatus[] = ['CLOSED', 'REJECTED']; // FR-011
const OVERDUE_DAYS = 7; // FR-012 / CR-004
const LATENCY_MS = 150;

const daysAgo = (days: number, hour = 10): string => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
};

@Injectable({ providedIn: 'root' })
export class RequestApiService {
  private readonly session = inject(SessionService);

  private readonly categories: Category[] = [
    { catId: 1, catName: 'Plumbing', active: true },
    { catId: 2, catName: 'Electrical', active: true },
    { catId: 3, catName: 'IT support', active: true },
    { catId: 4, catName: 'Parking (retired)', active: false },
  ];

  private readonly handlers: StoredHandler[] = [
    { id: 1, name: 'T. Mokoena', categoryIds: [1, 2] },
    { id: 2, name: 'J. Smith', categoryIds: [1, 3] },
  ];

  private requests: StoredRequest[] = [
    this.seed(17, 3, 'Laptop will not connect to Wi-Fi', 'Library, desk 4', 'CLOSED', 'LOW', 10234, 2, 20),
    this.seed(19, 2, 'Light flickering in corridor', 'Block A, floor 2', 'RESOLVED', 'LOW', 10511, 1, 18),
    this.seed(28, 1, 'Leaking tap in kitchen', 'Block B, kitchen', 'IN_PROGRESS', 'MEDIUM', 10377, 2, 12),
    this.seed(31, 2, 'Plug point sparks when used', 'Block C, room 12', 'ASSIGNED', 'HIGH', 10234, 1, 9),
    this.seed(35, 3, 'Printer queue stuck', 'Admin office', 'NEW', null, 10600, null, 8),
    this.seed(39, 1, 'Toilet keeps running', 'Block D, ground floor', 'NEW', null, 10788, null, 3),
    this.seed(42, 1, 'Blocked drain outside entrance', 'Main entrance', 'NEW', null, 10234, null, 1),
  ];
  private nextId = 43;

  // ---- API-13 GET /categories  (requesters only ever get active ones) ----
  getCategories(activeOnly = true): Observable<Category[]> {
    const forced = this.session.user()?.role === 'REQUESTER' ? true : activeOnly;
    return this.ok(this.categories.filter((c) => !forced || c.active));
  }

  // ---- API-04 POST /requests ----
  submitRequest(body: NewRequest): Observable<CreatedRequest> {
    const fieldErrors: { field: string; message: string }[] = [];
    if (!body.categoryId) fieldErrors.push({ field: 'categoryId', message: 'Category is required' });
    if (!body.description?.trim()) fieldErrors.push({ field: 'description', message: 'Description is required' });
    else if (body.description.length > 2000)
      fieldErrors.push({ field: 'description', message: 'Description must be 2000 characters or fewer' });
    if (!body.location?.trim()) fieldErrors.push({ field: 'location', message: 'Location is required' });
    else if (body.location.length > 255)
      fieldErrors.push({ field: 'location', message: 'Location must be 255 characters or fewer' });
    if (fieldErrors.length) {
      return this.fail(400, 'VALIDATION_FAILED', 'Validation failed', 'Some fields are invalid.', fieldErrors);
    }
    const category = this.categories.find((c) => c.catId === body.categoryId);
    if (!category?.active) {
      return this.fail(422, 'CATEGORY_INACTIVE', 'Category inactive', 'This category is no longer available.');
    }

    const now = new Date().toISOString();
    const id = this.nextId++;
    const requesterNumber = this.session.user()?.requesterNumber ?? 0;
    const stored: StoredRequest = {
      id,
      refNum: this.refNum(id, now),
      categoryId: body.categoryId,
      description: body.description.trim(),
      location: body.location.trim(),
      attachmentUrl: body.attachmentUrl,
      status: 'NEW',
      priority: null,
      requesterNumber,
      handlerId: null,
      createdAt: now,
      updatedAt: now,
      version: 0,
      audit: [{ at: now, actor: `Requester #${requesterNumber}`, action: 'submitted request', fromStatus: null, toStatus: 'NEW' }],
      comments: [],
    };
    this.requests.push(stored);
    return this.ok({ id, refNum: stored.refNum, status: stored.status, createdAt: now }, 201);
  }

  // ---- API-05 GET /requests/mine  (own requests only, newest first) ----
  getMyRequests(page = 0, size = 20): Observable<Page<RequestSummary>> {
    const me = this.session.user()?.requesterNumber;
    const mine = this.requests
      .filter((r) => r.requesterNumber === me)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return this.ok(this.page(mine.map((r) => this.toSummary(r)), page, size));
  }

  // ---- API-06 GET /requests/{id} ----
  getRequest(id: number): Observable<RequestDetail> {
    const r = this.requests.find((x) => x.id === id);
    const user = this.session.user();
    // Another client's request returns 404, not 403, so its existence is not revealed (OWASP API1).
    if (!r || (user?.role === 'REQUESTER' && r.requesterNumber !== user.requesterNumber)) {
      return this.fail(404, 'NOT_FOUND', 'Not found', 'Request not found.');
    }
    if (!this.inScope(r)) return this.forbidden();

    const isStaff = user?.role !== 'REQUESTER';
    return this.ok({
      ...this.toSummary(r),
      description: r.description,
      location: r.location,
      attachmentUrl: r.attachmentUrl,
      requesterNumber: r.requesterNumber,
      version: r.version,
      updates: this.requesterUpdates(r),
      comments: r.comments,
      allowedTransitions: isStaff ? this.allowedFor(r) : [],
    });
  }

  // ---- API-07 GET /requests  (handler: own categories only; manager: all) ----
  getRequests(f: RequestFilter): Observable<Page<RequestSummary>> {
    if (this.session.user()?.role === 'REQUESTER') return this.forbidden();
    const rows = this.requests
      .filter((r) => this.inScope(r))
      .filter((r) => !f.status || r.status === f.status)
      .filter((r) => !f.categoryId || r.categoryId === f.categoryId)
      .filter((r) => !f.from || r.createdAt.slice(0, 10) >= f.from)
      .filter((r) => !f.to || r.createdAt.slice(0, 10) <= f.to)
      .sort((a, b) => (f.sort === 'createdAt,asc' ? 1 : -1) * a.createdAt.localeCompare(b.createdAt));
    return this.ok(this.page(rows.map((r) => this.toSummary(r)), f.page, f.size));
  }

  // ---- API-08 POST /requests/{id}/assignment  (handler: self; manager: any handler) ----
  assign(id: number, version: number, employeeId?: number): Observable<RequestDetail> {
    const user = this.session.user();
    const r = this.requests.find((x) => x.id === id);
    if (!r) return this.fail(404, 'NOT_FOUND', 'Not found', 'Request not found.');
    if (!this.inScope(r)) return this.forbidden();
    if (r.version !== version) return this.versionConflict();
    if (READ_ONLY.includes(r.status)) return this.closed();
    if (r.handlerId !== null) {
      return this.fail(409, 'ALREADY_ASSIGNED', 'Already assigned', 'This request is already assigned.');
    }
    const handlerId = user?.role === 'MANAGER' && employeeId ? employeeId : user?.employeeId;
    const handler = this.handlers.find((h) => h.id === handlerId);
    if (!handler || !handler.categoryIds.includes(r.categoryId)) {
      return this.fail(422, 'OUT_OF_SCOPE', 'Handler not in category', 'That handler does not cover this category.');
    }
    const from = r.status;
    r.handlerId = handler.id;
    if (r.status === 'NEW') r.status = 'ASSIGNED';
    this.touch(r, user?.role === 'MANAGER' ? `assigned to ${handler.name}` : 'accepted request', from);
    return this.getRequest(id);
  }

  // ---- API-09 PATCH /requests/{id}/status ----
  changeStatus(id: number, toStatus: RequestStatus, version: number, note?: string): Observable<RequestDetail> {
    const r = this.requests.find((x) => x.id === id);
    if (!r) return this.fail(404, 'NOT_FOUND', 'Not found', 'Request not found.');
    if (!this.inScope(r)) return this.forbidden();
    if (r.version !== version) return this.versionConflict();
    if (READ_ONLY.includes(r.status)) return this.closed();
    if (!this.allowedFor(r).includes(toStatus)) {
      return this.fail(
        422,
        'INVALID_TRANSITION',
        'Invalid status transition',
        `A request cannot move from ${STATUS_LABELS[r.status]} to ${STATUS_LABELS[toStatus]}.`,
      );
    }
    const from = r.status;
    r.status = toStatus;
    if (note?.trim()) r.comments.push({ at: new Date().toISOString(), author: this.actorName(), content: note.trim() });
    this.touch(r, 'changed status', from);
    return this.getRequest(id);
  }

  // ---- API-10 POST /requests/{id}/comments  (append-only) ----
  addComment(id: number, content: string): Observable<RequestComment> {
    const r = this.requests.find((x) => x.id === id);
    if (!r) return this.fail(404, 'NOT_FOUND', 'Not found', 'Request not found.');
    if (!this.inScope(r)) return this.forbidden();
    if (!content.trim()) {
      return this.fail(400, 'VALIDATION_FAILED', 'Validation failed', 'Note is empty.', [
        { field: 'content', message: 'Note cannot be empty' },
      ]);
    }
    if (READ_ONLY.includes(r.status)) return this.closed();
    const comment = { at: new Date().toISOString(), author: this.actorName(), content: content.trim() };
    r.comments.push(comment);
    r.audit.push({ at: comment.at, actor: comment.author, action: 'added a note', fromStatus: null, toStatus: null });
    return this.ok(comment, 201);
  }

  // ---- API-11 GET /requests/{id}/audit  (newest first) ----
  getAudit(id: number): Observable<AuditEntry[]> {
    const r = this.requests.find((x) => x.id === id);
    if (!r) return this.fail(404, 'NOT_FOUND', 'Not found', 'Request not found.');
    if (this.session.user()?.role === 'REQUESTER' || !this.inScope(r)) return this.forbidden();
    return this.ok([...r.audit].reverse());
  }

  // ---- API-12 PATCH /requests/{id}/priority  (Management only, FR-016) ----
  setPriority(id: number, priority: Priority, version: number): Observable<RequestDetail> {
    if (this.session.user()?.role !== 'MANAGER') return this.forbidden();
    const r = this.requests.find((x) => x.id === id);
    if (!r) return this.fail(404, 'NOT_FOUND', 'Not found', 'Request not found.');
    if (r.version !== version) return this.versionConflict();
    r.priority = priority;
    this.touch(r, `set priority to ${priority.toLowerCase()}`, null);
    return this.getRequest(id);
  }

  // ---- API-17 GET /reports/activity  (Management only, counts only) ----
  getActivity(from: string | null, to: string | null, categoryId: number | null, status: RequestStatus | null): Observable<ActivityReport> {
    if (this.session.user()?.role !== 'MANAGER') return this.forbidden();
    if (from && to && from > to) {
      return this.fail(400, 'VALIDATION_FAILED', 'Validation failed', '"From" date must be before "To" date.');
    }
    const rows = this.requests
      .filter((r) => !from || r.createdAt.slice(0, 10) >= from)
      .filter((r) => !to || r.createdAt.slice(0, 10) <= to)
      .filter((r) => !categoryId || r.categoryId === categoryId)
      .filter((r) => !status || r.status === status);

    const count = (list: StoredRequest[]) => ({
      open: list.filter((r) => OPEN.includes(r.status)).length,
      overdue: list.filter((r) => this.isOverdue(r)).length,
      resolved: list.filter((r) => r.status === 'RESOLVED').length,
      closed: list.filter((r) => r.status === 'CLOSED').length,
    });
    const byCategory = this.categories
      .filter((c) => !categoryId || c.catId === categoryId)
      .map((c) => ({ categoryId: c.catId!, categoryName: c.catName, ...count(rows.filter((r) => r.categoryId === c.catId)) }));
    return this.ok({ ...count(rows), byCategory });
  }

  // NOT YET IN THE API CONTRACT: the manager's "Assign to handler" list needs a way to list handlers
  // for a category. Raise a change request to add e.g. GET /employees?role=HANDLER&categoryId=.
  getHandlers(categoryId: number): Observable<HandlerOption[]> {
    return this.ok(this.handlers.filter((h) => h.categoryIds.includes(categoryId)).map(({ id, name }) => ({ id, name })));
  }

  // --- helpers ------------------------------------------------------------------------------

  private seed(
    id: number, categoryId: number, description: string, location: string, status: RequestStatus,
    priority: Priority | null, requesterNumber: number, handlerId: number | null, ageDays: number,
  ): StoredRequest {
    const createdAt = daysAgo(ageDays, 11);
    const handler = this.handlers.find((h) => h.id === handlerId)?.name ?? 'Handler';
    const audit: AuditEntry[] = [
      { at: createdAt, actor: `Requester #${requesterNumber}`, action: 'submitted request', fromStatus: null, toStatus: 'NEW' },
    ];
    // Walk the state machine so the seeded audit trail is consistent with the current status.
    const path: RequestStatus[] = ['ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
    let prev: RequestStatus = 'NEW';
    for (let i = 0; i < path.indexOf(status) + 1; i++) {
      audit.push({ at: daysAgo(ageDays - i - 1, 9 + i), actor: handler, action: i === 0 ? 'accepted request' : 'changed status', fromStatus: prev, toStatus: path[i] });
      prev = path[i];
    }
    return {
      id, refNum: this.refNum(id, createdAt), categoryId, description, location, attachmentUrl: null,
      status, priority, requesterNumber, handlerId, createdAt, updatedAt: audit[audit.length - 1].at,
      version: audit.length - 1, audit, comments: [],
    };
  }

  private refNum(id: number, iso: string): string {
    return `CC-${iso.slice(0, 4)}-${String(id).padStart(5, '0')}`;
  }

  private toSummary(r: StoredRequest): RequestSummary {
    return {
      id: r.id,
      refNum: r.refNum,
      categoryId: r.categoryId,
      categoryName: this.categories.find((c) => c.catId === r.categoryId)?.catName ?? '?',
      status: r.status,
      priority: r.priority,
      handlerId: r.handlerId,
      handlerName: this.handlers.find((h) => h.id === r.handlerId)?.name ?? null,
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
    };
  }

  // FR-004 wording for requesters - no staff names (ASR-005).
  private requesterUpdates(r: StoredRequest) {
    return [...r.audit]
      .filter((a) => a.toStatus)
      .reverse()
      .map((a) => ({
        at: a.at,
        text: a.toStatus === 'NEW' ? 'Request submitted'
          : a.toStatus === 'ASSIGNED' ? 'Assigned to a handler'
          : `Status changed to ${STATUS_LABELS[a.toStatus!]}`,
      }));
  }

  private allowedFor(r: StoredRequest): RequestStatus[] {
    // ASSIGNED is reached through API-08 (accept/assign), not the status dropdown.
    return TRANSITIONS[r.status].filter((s) => s !== 'ASSIGNED');
  }

  private inScope(r: StoredRequest): boolean {
    const user = this.session.user();
    if (user?.role !== 'HANDLER') return true;
    return this.handlers.find((h) => h.id === user.employeeId)?.categoryIds.includes(r.categoryId) ?? false;
  }

  private isOverdue(r: StoredRequest): boolean {
    const ageMs = Date.now() - new Date(r.createdAt).getTime();
    return OPEN.includes(r.status) && ageMs > OVERDUE_DAYS * 24 * 60 * 60 * 1000;
  }

  private actorName(): string {
    const user = this.session.user();
    return this.handlers.find((h) => h.id === user?.employeeId)?.name ?? user?.badge ?? 'Unknown';
  }

  private touch(r: StoredRequest, action: string, from: RequestStatus | null): void {
    const now = new Date().toISOString();
    r.version++;
    r.updatedAt = now;
    const statusChanged = from !== null && from !== r.status;
    r.audit.push({
      at: now,
      actor: this.actorName(),
      action,
      fromStatus: statusChanged ? from : null,
      toStatus: statusChanged ? r.status : null,
    });
  }

  private page<T>(rows: T[], page: number, size: number): Page<T> {
    return {
      content: rows.slice(page * size, page * size + size),
      page,
      size,
      totalElements: rows.length,
      totalPages: Math.max(1, Math.ceil(rows.length / size)),
    };
  }

  private ok<T>(body: T, _status = 200): Observable<T> {
    // structuredClone so screens can never mutate "server" state by reference
    return of(structuredClone(body)).pipe(delay(LATENCY_MS));
  }

  private fail<T>(status: number, code: string, title: string, detail: string, fieldErrors?: ApiProblem['fieldErrors']): Observable<T> {
    const problem: ApiProblem = { status, code, title, detail, fieldErrors };
    return timer(LATENCY_MS).pipe(switchMap(() => throwError(() => problem)));
  }

  private forbidden<T>(): Observable<T> {
    return this.fail(403, 'FORBIDDEN', 'Forbidden', 'You do not have access.');
  }

  private versionConflict<T>(): Observable<T> {
    return this.fail(409, 'VERSION_CONFLICT', 'Version conflict', 'This request was updated by someone else.');
  }

  private closed<T>(): Observable<T> {
    return this.fail(422, 'REQUEST_CLOSED', 'Request closed', 'Closed or rejected requests are read-only.');
  }
}
