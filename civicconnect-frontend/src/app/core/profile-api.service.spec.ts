import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ProfileApiService } from './profile-api.service';

describe('ProfileApiService', () => {
  let api: ProfileApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ProfileApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('patches only the fields given', async () => {
    const result = firstValueFrom(api.updateProfile({ email: 'new@x.co', currentPassword: 'old' }));
    const req = http.expectOne('/api/v1/clients/me');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ email: 'new@x.co', currentPassword: 'old' });
    req.flush({ id: 1, firstName: 'A', surname: 'B', email: 'new@x.co', phone: null, dateOfBirth: null, active: true });
    expect((await result).email).toBe('new@x.co');
  });

  it('reports a wrong current password as a field error', async () => {
    const result = firstValueFrom(api.updateProfile({ password: 'Secret123!', currentPassword: 'bad' }));
    http.expectOne('/api/v1/clients/me').flush(
      { code: 'VALIDATION_FAILED', detail: 'Invalid', fieldErrors: [{ field: 'currentPassword', message: 'Current password is incorrect' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await expect(result).rejects.toMatchObject({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'currentPassword' }] });
  });
});
