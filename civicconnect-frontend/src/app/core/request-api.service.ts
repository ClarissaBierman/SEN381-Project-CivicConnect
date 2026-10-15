import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { Category } from '../category';
import {
  ActivityReport,
  ApiProblem,
  AuditEntry,
  CategoryCounts,
  Counts,
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
  RequestUpdate,
  STATUS_LABELS,
} from './models';

/*
 * Client for the Requests, Reporting and related endpoints (API-04 to API-17).
 *
 * Calls the real Spring Boot API. The server's JSON is mapped to the shapes the screens already use,
 * so the screens did not have to change (apart from no longer sending a version number), and every
 * error is turned into the PED error body (ApiProblem) so problemMessage() keeps working.
 */

const API = '/api/v1';

// ---- server shapes (only what is mapped here) ----------------------------------------------------

interface ServerPage<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

interface ServerCategory {
  id: number;
  catName: string;
  active: boolean;
}

interface ServerSummary {
  id: number;
  refNum: string;
  categoryId: number;
  categoryName: string;
  status: RequestStatus;
  priority: Priority | null;
  location: string;
  handlerId: number | null;
  handlerName: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
}

interface ServerComment {
  id: number;
  authorName: string | null;
  content: string;
  visibleToRequester: boolean;
  createdAt: string;
}

interface ServerDetail extends ServerSummary {
  description: string;
  attachmentUrl: string | null;
  requesterId: number;
  allowedTransitions: RequestStatus[] | null;
  comments: ServerComment[];
  statusHistory?: { status: RequestStatus; at: string }[];
}

interface ServerHistoryEntry {
  at: string;
  actor: string;
  action: string;
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus | null;
}

interface ServerReport {
  generatedAt: string;
  totals: Counts;
  byCategory: CategoryCounts[];
}

@Injectable({ providedIn: 'root' })
export class RequestApiService {
  private readonly http = inject(HttpClient);

  // ---- API-13 GET /categories  (the server forces active-only for requesters) ----
  getCategories(activeOnly = true): Observable<Category[]> {
    const params = new HttpParams().set('active', activeOnly);
    return this.call(
      this.http.get<ServerCategory[]>(`${API}/categories`, { params }).pipe(map((list) => list.map(toCategory))),
    );
  }

  // ---- API-04 POST /requests ----
  submitRequest(body: NewRequest): Observable<CreatedRequest> {
    return this.call(
      this.http.post<ServerDetail>(`${API}/requests`, body).pipe(
        map((d) => ({ id: d.id, refNum: d.refNum, status: d.status, createdAt: d.createdAt })),
      ),
    );
  }

  // ---- API-05 GET /requests/mine  (own requests only, newest first) ----
  getMyRequests(page = 0, size = 20): Observable<Page<RequestSummary>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'createdAt,desc');
    return this.call(
      this.http.get<ServerPage<ServerSummary>>(`${API}/requests/mine`, { params }).pipe(map((p) => toPage(p, toSummary))),
    );
  }

  // ---- API-06 GET /requests/{id} ----
  getRequest(id: number): Observable<RequestDetail> {
    return this.call(this.http.get<ServerDetail>(`${API}/requests/${id}`).pipe(map(toDetail)));
  }

  // ---- API-07 GET /requests  (handler: own categories only; manager: all) ----
  getRequests(f: RequestFilter): Observable<Page<RequestSummary>> {
    let params = new HttpParams().set('page', f.page).set('size', f.size).set('sort', f.sort);
    if (f.status) params = params.set('status', f.status);
    if (f.open) params = params.set('open', true);
    if (f.unassigned) params = params.set('unassigned', true);
    if (f.categoryId) params = params.set('categoryId', f.categoryId);
    if (f.from) params = params.set('from', f.from);
    if (f.to) params = params.set('to', f.to);
    return this.call(
      this.http.get<ServerPage<ServerSummary>>(`${API}/requests`, { params }).pipe(map((p) => toPage(p, toSummary))),
    );
  }

  // ---- API-08 POST /requests/{id}/assignment  (handler: self, no body; manager: employeeId) ----
  assign(id: number, employeeId?: number): Observable<RequestDetail> {
    const body = employeeId === undefined ? null : { employeeId };
    return this.call(this.http.post<ServerDetail>(`${API}/requests/${id}/assignment`, body).pipe(map(toDetail)));
  }

  // ---- API-09 PATCH /requests/{id}/status  (an optional note is saved as a comment) ----
  changeStatus(id: number, toStatus: RequestStatus, note?: string, noteVisibleToRequester = false): Observable<RequestDetail> {
    const body = { toStatus, note: note?.trim() || null, noteVisibleToRequester };
    return this.call(this.http.patch<ServerDetail>(`${API}/requests/${id}/status`, body).pipe(map(toDetail)));
  }

  // ---- API-10 POST /requests/{id}/comments  (append-only) ----
  addComment(id: number, content: string, visibleToRequester = false): Observable<RequestComment> {
    return this.call(
      this.http
        .post<ServerComment>(`${API}/requests/${id}/comments`, { content: content.trim(), visibleToRequester })
        .pipe(map(toComment)),
    );
  }

  // ---- API-11 GET /requests/{id}/audit  (server: oldest first; screens show newest first) ----
  getAudit(id: number): Observable<AuditEntry[]> {
    return this.call(
      this.http.get<ServerHistoryEntry[]>(`${API}/requests/${id}/audit`).pipe(
        map((list) =>
          list
            .map((e) => ({ at: e.at, actor: e.actor, action: e.action, fromStatus: e.fromStatus, toStatus: e.toStatus }))
            .reverse(),
        ),
      ),
    );
  }

  // ---- API-12 PATCH /requests/{id}/priority  (Management only, FR-016) ----
  setPriority(id: number, priority: Priority): Observable<RequestDetail> {
    return this.call(this.http.patch<ServerDetail>(`${API}/requests/${id}/priority`, { priority }).pipe(map(toDetail)));
  }

  // ---- API-17 GET /reports/activity  (Management only, counts only) ----
  getActivity(from: string | null, to: string | null, categoryId: number | null, status: RequestStatus | null): Observable<ActivityReport> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (categoryId) params = params.set('categoryId', categoryId);
    if (status) params = params.set('status', status);
    return this.call(
      this.http
        .get<ServerReport>(`${API}/reports/activity`, { params })
        .pipe(map((r) => ({ ...r.totals, generatedAt: r.generatedAt, byCategory: r.byCategory }))),
    );
  }

  // ---- GET /categories/{id}/handlers  (Management: active handlers who cover the category) ----
  getHandlers(categoryId: number): Observable<HandlerOption[]> {
    return this.call(this.http.get<HandlerOption[]>(`${API}/categories/${categoryId}/handlers`));
  }

  /** Every error reaches the screens as the PED error body (ApiProblem), as the in-memory version did. */
  private call<T>(request: Observable<T>): Observable<T> {
    return request.pipe(catchError((err: HttpErrorResponse) => throwError(() => toProblem(err))));
  }
}

// ---- mapping ------------------------------------------------------------------------------------

export function toProblem(err: HttpErrorResponse): ApiProblem {
  const body = err.error as Partial<ApiProblem> | null;
  if (body && typeof body === 'object' && body.code) {
    return {
      status: body.status ?? err.status,
      code: body.code,
      title: body.title ?? '',
      detail: body.detail ?? '',
      fieldErrors: body.fieldErrors ?? [],
    };
  }
  // No error body: the server could not be reached, or something in between answered
  return err.status === 0
    ? { status: 0, code: 'SERVICE_UNAVAILABLE', title: 'Service unavailable', detail: 'The server cannot be reached.' }
    : { status: err.status, code: 'UNKNOWN', title: err.statusText, detail: err.message };
}

function toCategory(c: ServerCategory): Category {
  return { catId: c.id, catName: c.catName, active: c.active };
}

function toPage<S, T>(p: ServerPage<S>, mapRow: (row: S) => T): Page<T> {
  return {
    content: p.content.map(mapRow),
    page: p.page.number,
    size: p.page.size,
    totalElements: p.page.totalElements,
    totalPages: Math.max(1, p.page.totalPages),
  };
}

function toSummary(s: ServerSummary): RequestSummary {
  return {
    id: s.id,
    refNum: s.refNum,
    categoryId: s.categoryId,
    categoryName: s.categoryName,
    status: s.status,
    priority: s.priority,
    location: s.location,
    handlerId: s.handlerId,
    handlerName: s.handlerName,
    createdAt: s.createdAt,
    updatedAt: s.updatedAt,
    closedAt: s.closedAt,
  };
}

function toComment(c: ServerComment): RequestComment {
  return {
    id: c.id,
    at: c.createdAt,
    author: c.authorName ?? 'CivicConnect',
    content: c.content,
    visibleToRequester: c.visibleToRequester,
  };
}

function toDetail(d: ServerDetail): RequestDetail {
  const comments = d.comments.map(toComment);
  return {
    ...toSummary(d),
    description: d.description,
    attachmentUrl: d.attachmentUrl,
    requesterNumber: d.requesterId,
    allowedTransitions: d.allowedTransitions ?? [],
    comments,
    updates: requesterUpdates(d, comments),
  };
}

/**
 * FR-004 "Updates from CivicConnect", newest first: every status the request has had (from the
 * server's statusHistory, with the time of each change) and every note staff marked as visible to the
 * requester. No staff names are included (ASR-005).
 */
function requesterUpdates(d: ServerDetail, comments: RequestComment[]): RequestUpdate[] {
  const updates: RequestUpdate[] = [];
  const history = d.statusHistory?.length ? d.statusHistory : [{ status: 'NEW' as RequestStatus, at: d.createdAt }];
  history.forEach((h, i) =>
    updates.push({ at: h.at, text: i === 0 ? 'Request submitted' : `Status: ${STATUS_LABELS[h.status]}` }),
  );
  for (const c of comments.filter((x) => x.visibleToRequester)) {
    updates.push({ at: c.at, text: `Note: ${c.content}` });
  }
  // Newest first; entries with the same time keep their order reversed (later event first)
  return updates
    .map((u, i) => ({ u, i }))
    .sort((a, b) => b.u.at.localeCompare(a.u.at) || b.i - a.i)
    .map((x) => x.u);
}
