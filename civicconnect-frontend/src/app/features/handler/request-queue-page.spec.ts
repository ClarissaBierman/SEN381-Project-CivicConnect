import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RequestDetail, RequestStatus } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { serverDetail, serverPage } from '../../testing/api-fakes';
import { RequestQueuePage } from './request-queue-page';

describe('RequestQueuePage', () => {
  let http: HttpTestingController;

  function start(role: 'STAFF' | 'MANAGEMENT') {
    TestBed.inject(SessionService).signInAs(role);
    const page = TestBed.createComponent(RequestQueuePage).componentInstance as any;
    page.ngOnInit();
    http.expectOne((r) => r.url === '/api/v1/categories').flush([]);
    http.expectOne((r) => r.url === '/api/v1/requests').flush(serverPage([]));
    return page;
  }

  function openRequest(page: any, id: number, status: RequestStatus) {
    page.open(id);
    http.expectOne(`/api/v1/requests/${id}`).flush(serverDetail(id, status));
    http.expectOne(`/api/v1/requests/${id}/audit`).flush([]);
    http.match(`/api/v1/categories/1/handlers`).forEach((r) => r.flush([]));
  }

  const detail = (status: RequestStatus, handlerId: number | null) =>
    ({ status, handlerId }) as RequestDetail;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ignores a contact reply that arrives after another request was opened (D-38)', () => {
    const page = start('MANAGEMENT');
    openRequest(page, 1, 'NEW');
    page.showContact();
    const lateContact = http.expectOne('/api/v1/clients/101/contact');

    openRequest(page, 2, 'NEW');
    lateContact.flush({
      id: 101,
      firstName: 'Thabo',
      surname: 'Mokoena',
      email: 't@x.co',
      phone: null,
      active: true,
    });

    expect(page.detail().id).toBe(2);
    expect(page.contact()).toBeNull();
  });

  it('keeps the new selection when a status change for the old request finishes later', () => {
    const page = start('STAFF');
    openRequest(page, 1, 'ASSIGNED');
    page.toStatus = 'IN_PROGRESS';
    page.updateStatus();
    const save = http.expectOne('/api/v1/requests/1/status');

    openRequest(page, 2, 'ASSIGNED');
    save.flush(serverDetail(1, 'IN_PROGRESS'));
    http.expectOne((r) => r.url === '/api/v1/requests').flush(serverPage([]));

    expect(page.detail().id).toBe(2);
    http.expectNone('/api/v1/requests/1');
  });

  it('only offers Accept to staff for New, unassigned requests', () => {
    const page = start('STAFF');
    expect(page.canAssign(detail('NEW', null))).toBe(true);
    expect(page.canAssign(detail('NEW', 7))).toBe(false);
    for (const s of [
      'ASSIGNED',
      'IN_PROGRESS',
      'RESOLVED',
      'CLOSED',
      'REJECTED',
    ] as RequestStatus[]) {
      expect(page.canAssign(detail(s, null))).toBe(false);
    }
  });

  it('offers Assign to managers for New, Assigned and In Progress only', () => {
    const page = start('MANAGEMENT');
    for (const s of ['NEW', 'ASSIGNED', 'IN_PROGRESS'] as RequestStatus[]) {
      expect(page.canAssign(detail(s, 7))).toBe(true);
    }
    for (const s of ['RESOLVED', 'CLOSED', 'REJECTED'] as RequestStatus[]) {
      expect(page.canAssign(detail(s, 7))).toBe(false);
    }
  });
});
