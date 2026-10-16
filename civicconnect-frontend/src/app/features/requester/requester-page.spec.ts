import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { serverPage } from '../../testing/api-fakes';
import { RequesterPage } from './requester-page';

describe('RequesterPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('page 3 stays on screen when the reply for page 2 would arrive later (D-38)', () => {
    const page = TestBed.createComponent(RequesterPage).componentInstance as any;
    page.ngOnInit();
    http.expectOne((r) => r.url === '/api/v1/categories').flush([]);
    http.expectOne((r) => r.url === '/api/v1/requests/mine').flush(serverPage([], 0, 5));

    page.goToPage(1);
    const pageTwo = http.expectOne(
      (r) => r.url === '/api/v1/requests/mine' && r.params.get('page') === '1',
    );
    page.goToPage(1);
    http
      .expectOne((r) => r.url === '/api/v1/requests/mine' && r.params.get('page') === '2')
      .flush(serverPage([], 2, 5));

    expect(pageTwo.cancelled).toBe(true);
    expect(page.myRequests().page).toBe(2);
  });
});
