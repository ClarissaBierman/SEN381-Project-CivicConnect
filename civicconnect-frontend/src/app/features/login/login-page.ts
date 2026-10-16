import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../core/auth.service';
import { HOME_ROUTE, SessionService } from '../../core/session.service';

// FR-014, API-02 and API-03
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="panel narrow">
      <h1>CivicConnect</h1>
      <h2>Log in</h2>

      @if (notice()) {
        <p class="confirm-box" role="status">{{ notice() }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="email">Email</label>
          <input
            id="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            [class.invalid]="showError('email')"
            [attr.aria-invalid]="showError('email')"
            [attr.aria-describedby]="showError('email') ? 'email-error' : null"
          />
          @if (showError('email')) {
            <span class="error" id="email-error">Enter a valid email address.</span>
          }
        </div>

        <div class="field">
          <label for="password">Password</label>
          <input
            id="password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            [class.invalid]="showError('password')"
            [attr.aria-invalid]="showError('password')"
            [attr.aria-describedby]="showError('password') ? 'password-error' : null"
          />
          @if (showError('password')) {
            <span class="error" id="password-error">Enter your password.</span>
          }
        </div>

        @if (error()) {
          <div class="error-box" role="alert">{{ error() }}</div>
        }

        <button type="submit" class="btn primary" [disabled]="busy()">
          {{ busy() ? 'Logging in...' : 'Log in' }}
        </button>
      </form>

      <p class="hint">No account yet? <a routerLink="/register">Register here</a></p>
    </section>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    const user = this.session.user();
    if (user) {
      this.router.navigateByUrl(HOME_ROUTE[user.role]);
      return;
    }
    const params = inject(ActivatedRoute).snapshot.queryParamMap;
    if (params.has('registered'))
      this.notice.set('If this email address was not registered yet, your account has been created. Log in to continue.');
    if (params.has('expired')) this.notice.set('Your session has expired. Please log in again.');
    if (params.has('deactivated')) this.notice.set('Your account is no longer active. Please contact CivicConnect.');
  }

  protected showError(field: 'email' | 'password'): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this.submitted());
  }

  submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid) return;

    this.busy.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth
      .login({ email: email.trim(), password })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (user) => this.router.navigateByUrl(HOME_ROUTE[user.role]),
        error: (err: HttpErrorResponse) => {
          this.session.signOut();
          this.error.set(this.loginMessage(err));
        },
      });
  }

  private loginMessage(err: HttpErrorResponse): string {
    if (err.status === 401) return 'Incorrect email or password.';
    // B4: after 5 wrong passwords the email is locked for 15 minutes; the server says how long
    if (err.status === 429) return err.error?.detail ?? 'Too many attempts. Please wait and try again.';
    if (err.status === 0) return 'Cannot reach the server. Check that the backend is running.';
    if (err.status === 400) return 'Enter a valid email address and password.';
    return 'Something went wrong. Please try again.';
  }
}
