import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountPage } from './account-page';

describe('AccountPage', () => {
  let http: HttpTestingController;

  function render() {
    const fixture = TestBed.createComponent(AccountPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>('#' + id)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { fixture, el, type, submit };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AccountPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('does not send anything when the new passwords do not match', () => {
    const { el, type, submit } = render();
    type('currentPassword', 'Old12345!');
    type('password', 'New12345!');
    type('confirmPassword', 'Different1!');
    submit();
    expect(el.querySelector('#confirmPassword-error')?.textContent).toContain('do not match');
    http.expectNone('/api/v1/employees/me/password');
  });

  it('sends the change and confirms it', () => {
    const { fixture, el, type, submit } = render();
    type('currentPassword', 'Old12345!');
    type('password', 'New12345!');
    type('confirmPassword', 'New12345!');
    submit();
    const req = http.expectOne('/api/v1/employees/me/password');
    expect(req.request.body).toEqual({ currentPassword: 'Old12345!', password: 'New12345!' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(el.querySelector('[role=status]')?.textContent).toContain('Password changed');
  });

  it('shows a wrong current password next to that field', () => {
    const { fixture, el, type, submit } = render();
    type('currentPassword', 'wrong');
    type('password', 'New12345!');
    type('confirmPassword', 'New12345!');
    submit();
    http.expectOne('/api/v1/employees/me/password').flush(
      { code: 'VALIDATION_FAILED', detail: 'Some fields are invalid.', fieldErrors: [{ field: 'currentPassword', message: 'Current password is incorrect' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(el.querySelector('#currentPassword-error')?.textContent).toContain('incorrect');
  });
});
