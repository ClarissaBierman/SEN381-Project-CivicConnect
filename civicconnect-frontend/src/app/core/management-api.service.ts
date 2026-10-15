import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { Category } from '../category';
import {
  AuditRecord,
  AuditResource,
  ClientContact,
  Employee,
  EmployeeChange,
  NewEmployee,
  Page,
} from './models';
import { toProblem } from './request-api.service';

const API = '/api/v1';

interface ServerPage<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

interface ServerCategory {
  id: number;
  catName: string;
  active: boolean;
}

const toCategory = (c: ServerCategory): Category => ({ catId: c.id, catName: c.catName, active: c.active });

const toPage = <T>(p: ServerPage<T>): Page<T> => ({
  content: p.content,
  page: p.page.number,
  size: p.page.size,
  totalElements: p.page.totalElements,
  totalPages: Math.max(1, p.page.totalPages),
});

// Management-only endpoints: categories (API-14/15), employees and their categories (API-16),
// client contact and activation, and the per-table audit trail.
@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private readonly http = inject(HttpClient);

  // ---- categories ----
  createCategory(catName: string): Observable<Category> {
    return this.call(this.http.post<ServerCategory>(`${API}/categories`, { catName }).pipe(map(toCategory)));
  }

  updateCategory(id: number, change: { catName?: string; active?: boolean }): Observable<Category> {
    return this.call(this.http.patch<ServerCategory>(`${API}/categories/${id}`, change).pipe(map(toCategory)));
  }

  // ---- employees ----
  getEmployees(page = 0, size = 100): Observable<Page<Employee>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'surname');
    return this.call(this.http.get<ServerPage<Employee>>(`${API}/employees`, { params }).pipe(map(toPage)));
  }

  createEmployee(body: NewEmployee): Observable<Employee> {
    return this.call(this.http.post<Employee>(`${API}/employees`, body));
  }

  updateEmployee(id: number, change: EmployeeChange): Observable<Employee> {
    return this.call(this.http.patch<Employee>(`${API}/employees/${id}`, change));
  }

  getEmployeeCategories(id: number): Observable<Category[]> {
    return this.call(
      this.http.get<ServerCategory[]>(`${API}/employees/${id}/categories`).pipe(map((l) => l.map(toCategory))),
    );
  }

  setEmployeeCategories(id: number, categoryIds: number[]): Observable<Category[]> {
    return this.call(
      this.http
        .put<ServerCategory[]>(`${API}/employees/${id}/categories`, { categoryIds })
        .pipe(map((l) => l.map(toCategory))),
    );
  }

  // ---- clients ----
  getClientContact(id: number): Observable<ClientContact> {
    return this.call(this.http.get<ClientContact>(`${API}/clients/${id}/contact`));
  }

  setClientActive(id: number, active: boolean): Observable<void> {
    return this.call(this.http.patch<void>(`${API}/clients/${id}/active`, { active }));
  }

  // ---- audit ----
  getAudit(
    resource: AuditResource,
    recordId: string | null,
    operation: string | null,
    page = 0,
    size = 20,
  ): Observable<Page<AuditRecord>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'changedAt,desc');
    if (recordId) params = params.set('recordId', recordId);
    if (operation) params = params.set('operation', operation);
    return this.call(
      this.http.get<ServerPage<AuditRecord>>(`${API}/audit/${resource}`, { params }).pipe(map(toPage)),
    );
  }

  private call<T>(request: Observable<T>): Observable<T> {
    return request.pipe(catchError((err: HttpErrorResponse) => throwError(() => toProblem(err))));
  }
}
