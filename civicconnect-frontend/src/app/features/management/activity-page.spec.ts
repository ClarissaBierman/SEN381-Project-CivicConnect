import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { serverPage } from '../../testing/api-fakes';
import { ActivityPage } from './activity-page';

describe('ActivityPage', () => {
  let http: HttpTestingController;

  const report = {
    generatedAt: '2026-10-16T10:00:00Z',
    totals: { total: 0, open: 0, overdue: 0, resolved: 0, closed: 0, rejected: 0 },
    byCategory: [],
  };

  function flushApply() {
    http.match((r) => r.url === '/api/v1/reports/activity').forEach((r) => r.flush(report));
    const lists = http.match((r) => r.url === '/api/v1/requests');
    const openList = lists.find((r) => r.request.params.get('size') === '100')!;
    lists.forEach((r) => r.flush(serverPage([])));
    return openList;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the chosen status with the open-requests list (FR-013)', () => {
    const page = TestBed.createComponent(ActivityPage).componentInstance as any;
    page.ngOnInit();
    http.expectOne((r) => r.url === '/api/v1/categories').flush([]);
    expect(flushApply().request.params.has('status')).toBe(false);

    page.filters.patchValue({ status: 'ASSIGNED' });
    page.apply();

    const openList = flushApply();
    expect(openList.request.params.get('status')).toBe('ASSIGNED');
    expect(openList.request.params.get('open')).toBe('true');
  });
});
