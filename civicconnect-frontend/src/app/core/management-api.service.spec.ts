import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ManagementApiService } from './management-api.service';
import { ApiProblem } from './models';

describe('ManagementApiService (HTTP mapping)', () => {
  let api: ManagementApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ManagementApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps categories from the server shape', async () => {
    const created = firstValueFrom(api.createCategory('Roads'));
    const post = http.expectOne('/api/v1/categories');
    expect(post.request.body).toEqual({ catName: 'Roads' });
    post.flush({ id: 7, catName: 'Roads', active: true });
    expect(await created).toEqual({ catId: 7, catName: 'Roads', active: true });

    const updated = firstValueFrom(api.updateCategory(7, { active: false }));
    const patch = http.expectOne('/api/v1/categories/7');
    expect(patch.request.method).toBe('PATCH');
    patch.flush({ id: 7, catName: 'Roads', active: false });
    expect((await updated).active).toBe(false);
  });

  it('lists employees sorted by surname and flattens the page', async () => {
    const result = firstValueFrom(api.getEmployees());
    const req = http.expectOne((r) => r.url === '/api/v1/employees');
    expect(req.request.params.get('sort')).toBe('surname');
    req.flush({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } });
    expect(await result).toEqual({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 });
  });

  it('replaces a handler\'s categories with PUT', async () => {
    const result = firstValueFrom(api.setEmployeeCategories(3, [2, 4]));
    const req = http.expectOne('/api/v1/employees/3/categories');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ categoryIds: [2, 4] });
    req.flush([{ id: 2, catName: 'Water', active: true }]);
    expect((await result).map((c) => c.catId)).toEqual([2]);
  });

  it('sends audit filters only when set', async () => {
    const result = firstValueFrom(api.getAudit('employees', '5', null, 1, 20));
    const req = http.expectOne((r) => r.url === '/api/v1/audit/employees');
    expect(req.request.params.get('recordId')).toBe('5');
    expect(req.request.params.has('operation')).toBe(false);
    expect(req.request.params.get('sort')).toBe('changedAt,desc');
    req.flush({ content: [], page: { size: 20, number: 1, totalElements: 21, totalPages: 2 } });
    expect((await result).page).toBe(1);
  });

  it('turns errors into ApiProblem', async () => {
    const result = firstValueFrom(api.updateEmployee(1, { active: false }));
    http.expectOne('/api/v1/employees/1').flush(
      { code: 'LAST_MANAGER', title: 'Conflict', detail: 'Cannot remove the last manager.' },
      { status: 409, statusText: 'Conflict' },
    );
    await expect(result).rejects.toMatchObject({ status: 409, code: 'LAST_MANAGER' } satisfies Partial<ApiProblem>);
  });
});
