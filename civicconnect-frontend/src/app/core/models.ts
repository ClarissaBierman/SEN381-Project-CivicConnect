// Shapes of the /api/v1 DTOs from PED v2.0 Section 21 (API conventions + endpoint contract).
// Field names are camelCase and timestamps ISO-8601 UTC instants ("...Z"), as the contract requires.

// Same role names as the API and the database (no translation layer).
export type Role = 'REQUESTER' | 'STAFF' | 'MANAGEMENT';

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

// Row in API-05 (requester) and API-07 (staff) pages. handlerId/handlerName are null for requesters.
export interface RequestSummary {
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

// Requester-visible "Updates from CivicConnect" line (FR-004)
export interface RequestUpdate {
  at: string;
  text: string;
}

export interface RequestComment {
  id: number;
  at: string;
  author: string; // "CivicConnect" for requesters (no staff names, ASR-005)
  content: string;
  visibleToRequester: boolean;
}

// API-06 response. allowedTransitions is only filled for staff (computed server-side by TransitionPolicy, DEC-011).
// Concurrent changes are handled by a row lock on the server, so no version number is sent (DEC-006 change).
export interface RequestDetail extends RequestSummary {
  description: string;
  attachmentUrl: string | null;
  requesterNumber: number; // requester shown by number only (ASR-005)
  updates: RequestUpdate[]; // built in RequestApiService from the dates and requester-visible notes
  comments: RequestComment[];
  allowedTransitions: RequestStatus[];
}

// API-11 entry (newest first in the screens)
export interface AuditEntry {
  at: string;
  actor: string;
  action: string;
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus | null;
}

// API-07 query params. open = New/Assigned/In Progress; unassigned = no current handler.
export interface RequestFilter {
  status: RequestStatus | null;
  open?: boolean;
  unassigned?: boolean;
  categoryId: number | null;
  from: string | null; // yyyy-MM-dd
  to: string | null;
  sort: 'createdAt,desc' | 'createdAt,asc' | 'priority,desc';
  page: number;
  size: number;
}

export interface Counts {
  total: number;
  open: number;
  overdue: number;
  resolved: number;
  closed: number;
  rejected: number;
}

export interface CategoryCounts extends Counts {
  categoryId: number;
  categoryName: string;
}

// API-17 response - counts only, no personal data (ASR-005, POPIA). Totals are flattened onto the report.
export interface ActivityReport extends Counts {
  generatedAt: string;
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

// ---- Management: employees (API: /employees, /employees/{id}/categories) ----

export type EmployeeRole = Exclude<Role, 'REQUESTER'>;

export const EMPLOYEE_ROLE_LABELS: Record<EmployeeRole, string> = {
  STAFF: 'Staff',
  MANAGEMENT: 'Manager',
};

export interface Employee {
  id: number;
  firstName: string;
  surname: string;
  email: string;
  phone: string | null;
  role: EmployeeRole;
  active: boolean;
}

export interface NewEmployee {
  firstName: string;
  surname: string;
  email: string;
  password: string;
  phone: string | null;
  role: EmployeeRole;
}

export interface EmployeeChange {
  role?: EmployeeRole;
  active?: boolean;
}

// ---- Clients ----

// GET /clients/me - the requester's own profile
export interface ClientProfile {
  id: number;
  firstName: string;
  surname: string;
  email: string;
  phone: string | null;
  dateOfBirth: string | null;
  active: boolean;
}

// PATCH /clients/me - only fields sent are changed; email/password need currentPassword
export interface ProfileChange {
  firstName?: string;
  surname?: string;
  phone?: string | null;
  dateOfBirth?: string;
  email?: string;
  password?: string;
  currentPassword?: string;
}

// GET /clients/{id}/contact - Management only, contact details only (ASR-005)
export interface ClientContact {
  id: number;
  firstName: string;
  surname: string;
  email: string;
  phone: string | null;
  active: boolean;
}

// ---- Audit (/audit/{resource}) ----

export type AuditResource =
  | 'requests'
  | 'assignments'
  | 'comments'
  | 'categories'
  | 'handler-categories'
  | 'employees'
  | 'clients';

export const AUDIT_RESOURCE_LABELS: Record<AuditResource, string> = {
  requests: 'Requests',
  assignments: 'Assignments',
  comments: 'Comments',
  categories: 'Categories',
  'handler-categories': 'Handler categories',
  employees: 'Employees',
  clients: 'Client accounts',
};

export const ALL_AUDIT_RESOURCES = Object.keys(AUDIT_RESOURCE_LABELS) as AuditResource[];

export interface AuditRecord {
  audId: number;
  resource: AuditResource;
  operation: 'INSERT' | 'UPDATE' | 'DELETE';
  recordId: string;
  oldData: Record<string, unknown> | null;
  newData: Record<string, unknown> | null;
  changedFields: string[];
  empId: number | null;
  clientId: number | null;
  changedAt: string;
}
