import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../core/auth.service';
import { ApiProblem } from '../../core/models';

type Field = 'firstName' | 'surname' | 'email' | 'phone' | 'password' | 'confirmPassword';

const CLIENT_MESSAGES: Record<Field, string> = {
  firstName: 'Enter your first name (max 100 characters).',
  surname: 'Enter your surname (max 100 characters).',
  email: 'Enter a valid email address.',
  phone: 'Phone number can be at most 30 characters.',
  password: 'Password must be 8 to 72 characters.',
  confirmPassword: 'Passwords do not match.',
};

// API-01. Only requesters register themselves, staff accounts are created by Management.
// The server gives the same answer for a new and an already registered email (D-25), so the login
// page's notice is worded for both.
@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="panel narrow">
      <h1>CivicConnect</h1>
      <h2>Create an account</h2>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="firstName">First name</label>
          <input
            id="firstName"
            formControlName="firstName"
            autocomplete="given-name"
            [class.invalid]="!!message('firstName')"
            [attr.aria-invalid]="!!message('firstName')"
            [attr.aria-describedby]="message('firstName') ? 'firstName-error' : null"
          />
          @if (message('firstName'); as m) {
            <span class="error" id="firstName-error">{{ m }}</span>
          }
        </div>

        <div class="field">
          <label for="surname">Surname</label>
          <input
            id="surname"
            formControlName="surname"
            autocomplete="family-name"
            [class.invalid]="!!message('surname')"
            [attr.aria-invalid]="!!message('surname')"
            [attr.aria-describedby]="message('surname') ? 'surname-error' : null"
          />
          @if (message('surname'); as m) {
            <span class="error" id="surname-error">{{ m }}</span>
          }
        </div>

        <div class="field">
          <label for="email">Email</label>
          <input
            id="email"
            type="email"
            formControlName="email"
            autocomplete="email"
            [class.invalid]="!!message('email')"
            [attr.aria-invalid]="!!message('email')"
            [attr.aria-describedby]="message('email') ? 'email-error' : null"
          />
          @if (message('email'); as m) {
            <span class="error" id="email-error">{{ m }}</span>
          }
        </div>

        <div class="field">
          <label for="phone">Phone (optional)</label>
          <input
            id="phone"
            type="tel"
            formControlName="phone"
            autocomplete="tel"
            [class.invalid]="!!message('phone')"
            [attr.aria-invalid]="!!message('phone')"
            [attr.aria-describedby]="message('phone') ? 'phone-error' : null"
          />
          @if (message('phone'); as m) {
            <span class="error" id="phone-error">{{ m }}</span>
          }
        </div>

        <div class="field">
          <label for="password">Password</label>
          <input
            id="password"
            [type]="showPasswords() ? 'text' : 'password'"
            formControlName="password"
            autocomplete="new-password"
            [class.invalid]="!!message('password')"
            [attr.aria-invalid]="!!message('password')"
            [attr.aria-describedby]="message('password') ? 'password-error' : null"
          />
          <span class="hint">At least 8 characters.</span>
          @if (message('password'); as m) {
            <span class="error" id="password-error">{{ m }}</span>
          }
        </div>

        <div class="field">
          <label for="confirmPassword">Confirm password</label>
          <input
            id="confirmPassword"
            [type]="showPasswords() ? 'text' : 'password'"
            formControlName="confirmPassword"
            autocomplete="new-password"
            [class.invalid]="!!message('confirmPassword')"
            [attr.aria-invalid]="!!message('confirmPassword')"
            [attr.aria-describedby]="message('confirmPassword') ? 'confirmPassword-error' : null"
          />
          @if (message('confirmPassword'); as m) {
            <span class="error" id="confirmPassword-error">{{ m }}</span>
          }
        </div>

        <label class="inline-row">
          <input
            type="checkbox"
            [checked]="showPasswords()"
            (change)="showPasswords.set(!showPasswords())"
          />
          Show passwords
        </label>

        @if (error()) {
          <div class="error-box" role="alert">{{ error() }}</div>
        }

        <button type="submit" class="btn primary" [disabled]="busy()">
          {{ busy() ? 'Creating account...' : 'Create account' }}
        </button>
      </form>

      <p class="hint">Already have an account? <a routerLink="/login">Log in</a></p>
    </section>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly showPasswords = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    surname: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', Validators.maxLength(30)],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', Validators.required],
  });

  protected message(field: Field): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const control = this.form.controls[field];
    if (!control.touched && !this.submitted()) return null;
    if (field === 'confirmPassword') {
      const { password, confirmPassword } = this.form.getRawValue();
      return confirmPassword !== password || control.invalid
        ? CLIENT_MESSAGES.confirmPassword
        : null;
    }
    return control.invalid ? CLIENT_MESSAGES[field] : null;
  }

  submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    const v = this.form.getRawValue();
    if (this.form.invalid || v.password !== v.confirmPassword) return;

    this.busy.set(true);
    this.auth
      .register({
        firstName: v.firstName.trim(),
        surname: v.surname.trim(),
        email: v.email.trim(),
        phone: v.phone.trim() || null,
        password: v.password,
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/login'], { queryParams: { registered: 1 } }),
        error: (err: HttpErrorResponse) => this.handleError(err),
      });
  }

  private handleError(err: HttpErrorResponse): void {
    const problem = err.error as Partial<ApiProblem> | null;
    if (err.status === 400 && problem?.fieldErrors?.length) {
      const mapped: Partial<Record<Field, string>> = {};
      for (const fe of problem.fieldErrors) mapped[fe.field as Field] = fe.message;
      this.serverErrors.set(mapped);
      return;
    }
    if (err.status === 0) {
      this.error.set('Cannot reach the server. Check that the backend is running.');
      return;
    }
    this.error.set('Something went wrong. Please try again.');
  }
}
