import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { ApiProblem } from '../../core/models';
import { problemMessage } from '../../core/problem-message';
import { ProfileApiService } from '../../core/profile-api.service';

type Field = 'currentPassword' | 'password' | 'confirmPassword';

const CLIENT_MESSAGES: Record<Field, string> = {
  currentPassword: 'Enter your current password.',
  password: 'Password must be 8 to 72 characters.',
  confirmPassword: 'Passwords do not match.',
};

// Staff and managers change their own password (API-26). A manager gives a new staff member a
// temporary password on the Staff screen; the staff member changes it here.
@Component({
  selector: 'app-account-page',
  imports: [ReactiveFormsModule],
  template: `
    <section class="panel narrow">
      <h1>My account</h1>
      <h2>Change password</h2>

      @if (message()) {
        <p class="confirm-box" role="status">{{ message() }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="field">
          <label for="currentPassword">Current password</label>
          <input id="currentPassword" type="password" formControlName="currentPassword" autocomplete="current-password"
                 [class.invalid]="!!error('currentPassword')" [attr.aria-invalid]="!!error('currentPassword')"
                 [attr.aria-describedby]="error('currentPassword') ? 'currentPassword-error' : null" />
          @if (error('currentPassword'); as m) { <span class="error" id="currentPassword-error">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="password">New password</label>
          <input id="password" type="password" formControlName="password" autocomplete="new-password"
                 [class.invalid]="!!error('password')" [attr.aria-invalid]="!!error('password')"
                 [attr.aria-describedby]="error('password') ? 'password-error' : null" />
          @if (error('password'); as m) { <span class="error" id="password-error">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="confirmPassword">Confirm new password</label>
          <input id="confirmPassword" type="password" formControlName="confirmPassword" autocomplete="new-password"
                 [class.invalid]="!!error('confirmPassword')" [attr.aria-invalid]="!!error('confirmPassword')"
                 [attr.aria-describedby]="error('confirmPassword') ? 'confirmPassword-error' : null" />
          @if (error('confirmPassword'); as m) { <span class="error" id="confirmPassword-error">{{ m }}</span> }
        </div>

        @if (saveError()) {
          <div class="error-box" role="alert">{{ saveError() }}</div>
        }

        <button type="submit" class="btn primary" [disabled]="busy()">
          {{ busy() ? 'Saving...' : 'Change password' }}
        </button>
      </form>
    </section>
  `,
})
export class AccountPage {
  private readonly api = inject(ProfileApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  protected readonly form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: [''],
  });

  protected error(field: Field): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const control = this.form.controls[field];
    if (!control.touched && !this.submitted()) return null;
    if (field === 'confirmPassword') {
      const v = this.form.getRawValue();
      return v.confirmPassword !== v.password ? CLIENT_MESSAGES.confirmPassword : null;
    }
    return control.invalid ? CLIENT_MESSAGES[field] : null;
  }

  protected save(): void {
    this.submitted.set(true);
    this.message.set(null);
    this.saveError.set(null);
    this.serverErrors.set({});
    const fields: Field[] = ['currentPassword', 'password', 'confirmPassword'];
    if (this.form.invalid || fields.some((f) => this.error(f))) return;

    const v = this.form.getRawValue();
    this.busy.set(true);
    this.api
      .changeEmployeePassword(v.currentPassword, v.password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.form.reset();
          this.submitted.set(false);
          this.message.set('Password changed. Use the new password next time you log in.');
        },
        error: (e: ApiProblem) => this.handleError(e),
      });
  }

  private handleError(e: ApiProblem): void {
    if (e.code === 'VALIDATION_FAILED' && e.fieldErrors?.length) {
      const errors: Partial<Record<Field, string>> = {};
      for (const fe of e.fieldErrors) errors[fe.field as Field] = fe.message;
      this.serverErrors.set(errors);
    } else {
      this.saveError.set(problemMessage(e));
    }
  }
}
