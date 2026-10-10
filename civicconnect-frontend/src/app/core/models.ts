// Shapes of the /api/v1 DTOs from PED v2.0 Section 21 (API conventions + endpoint contract).
// Field names are camelCase and timestamps ISO-8601 UTC, as the contract requires.

export type Role = 'REQUESTER' | 'HANDLER' | 'MANAGER';

// FR-009 state machine values - must match ServiceRequest.Status and the DB CHECK constraint.
export type RequestStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REJECTED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH';

export const STATUS_LABELS: Record<RequestStatus, string> = {
  NEW: 'New',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  REJECTED: 'Rejected',
};

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
};

export const ALL_STATUSES = Object.keys(STATUS_LABELS) as RequestStatus[];
export const ALL_PRIORITIES = Object.keys(PRIORITY_LABELS) as Priority[];

export interface CurrentUser {
  role: Role;
  badge: string; // text in the top-right pill, e.g. "Requester #10234"
  requesterNumber?: number;
  employeeId?: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// API-04 body
export interface NewRequest {
  categoryId: number;
  description: string;
  location: string;
  attachmentUrl: string | null;
}

// API-04 201 response
export interface CreatedRequest {
  id: number;
  refNum: string;
  status: RequestStatus;
  createdAt: string;
}

// Row in API-05 (requester) and API-07 (staff) pages
export interface RequestSummary {
  id: number;
  refNum: string;
  categoryId: number;
  categoryName: string;
  status: RequestStatus;
  priority: Priority | null;
  handlerId: number | null;
  handlerName: string | null;
  createdAt: string;
  updatedAt: string;
}

// Requester-visible "Updates from CivicConnect" line (FR-004)
export interface RequestUpdate {
  at: string;
  text: string;
}

export interface RequestComment {
  at: string;
  author: string;
  content: string;
}

// API-06 response. allowedTransitions is only filled for staff (computed server-side by TransitionPolicy, DEC-011).
export interface RequestDetail extends RequestSummary {
  description: string;
  location: string;
  attachmentUrl: string | null;
  requesterNumber: number; // requester shown by number only (ASR-005)
  version: number; // optimistic locking (DEC-006)
  updates: RequestUpdate[];
  comments: RequestComment[];
  allowedTransitions: RequestStatus[];
}

// API-11 entry
export interface AuditEntry {
  at: string;
  actor: string;
  action: string;
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus | null;
}

// API-07 query params
export interface RequestFilter {
  status: RequestStatus | null;
  categoryId: number | null;
  from: string | null; // yyyy-MM-dd
  to: string | null;
  sort: 'createdAt,desc' | 'createdAt,asc';
  page: number;
  size: number;
}

export interface CategoryCounts {
  categoryId: number;
  categoryName: string;
  open: number;
  overdue: number;
  resolved: number;
  closed: number;
}

// API-17 response - counts only, no personal data (ASR-005, POPIA)
export interface ActivityReport {
  open: number;
  overdue: number;
  resolved: number;
  closed: number;
  byCategory: CategoryCounts[];
}

export interface HandlerOption {
  id: number;
  name: string;
}

// Error body from PED Section 21.5 (Spring ProblemDetail + code + fieldErrors)
export interface ApiProblem {
  status: number;
  code: string;
  title: string;
  detail: string;
  fieldErrors?: { field: string; message: string }[];
}
