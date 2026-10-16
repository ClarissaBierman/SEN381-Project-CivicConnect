import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Employee } from '../../core/models';
import { serverPage } from '../../testing/api-fakes';
import { StaffPage } from './staff-page';

describe('StaffPage', () => {
  let http: HttpTestingController;

  const staff = (id: number): Employee => ({
    id,
    firstName: 'Staff',
    surname: `Member ${id}`,
    email: `staff${id}@civicconnect.example`,
    phone: null,
    role: 'STAFF',
    active: true,
  });
  const cat = (id: number) => ({ id, catName: `Category ${id}`, active: true });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('a save for employee 1 that finishes after employee 2 was selected leaves employee 2 unchanged (D-38)', () => {
    const page = TestBed.createComponent(StaffPage).componentInstance as any;
    page.ngOnInit();
    http.expectOne((r) => r.url === '/api/v1/employees').flush(serverPage([staff(1), staff(2)]));
    http.expectOne((r) => r.url === '/api/v1/categories').flush([cat(1), cat(2), cat(3)]);

    page.select(staff(1));
    http.expectOne('/api/v1/employees/1/categories').flush([cat(1)]);
    page.toggleCategory(2);
    page.saveCategories(staff(1));
    const save = http.expectOne('/api/v1/employees/1/categories');

    page.select(staff(2));
    http.expectOne('/api/v1/employees/2/categories').flush([cat(3)]);
    save.flush([cat(1), cat(2)]);

    expect(page.selected().id).toBe(2);
    expect([...page.chosen()]).toEqual([3]);
    expect(page.message()).toBeNull();
  });
});
