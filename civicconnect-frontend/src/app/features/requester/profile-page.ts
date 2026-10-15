import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { ApiProblem, ClientProfile, ProfileChange } from '../../core/models';
import { problemMessage } from '../../core/problem-message';
import { ProfileApiService } from '../../core/profile-api.service';

type Field = 'firstName' | 'surname' | 'email' | 'phone' | 'dateOfBirth' | 'password' | 'confirmPassword' | 'currentPassword';

const localIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const CLIENT_MESSAGES: Record<Field, string> = {
  firstName: 'Enter your first name (max 100 characters).',
  surname: 'Enter your surname (max 100 characters).',
  email: 'Enter a valid email address.',
  phone: 'Phone number can be at most 30 characters.',
  dateOfBirth: 'Date of birth must be in the past.',
  password: 'Password must be 8 to 72 characters.',
  confirmPassword: 'Passwords do not match.',
  currentPassword: 'Enter your current password to change your email or password.',
};

// The requester's own account (GET/PATCH /clients/me). Only changed fields are sent; changing the
// email or password needs the current password (B3).
@Component({
  selector: 'app-profile-page',
  imports: [ReactiveFormsModule],
  template: `
    <section class="panel narrow">
      <h1>My profile</h1>

      @if (loadError()) {
        <p class="error-box" role="alert">{{ loadError() }}</p>
      }
      @if (message()) {
        <p class="confirm-box" role="status">{{ message() }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="field">
          <label for="firstName">First name</label>
          <input id="firstName" formControlName="firstName" autocomplete="given-name" [class.invalid]="!!error('firstName')" [attr.aria-invalid]="!!error('firstName')"
                   [attr.aria-describedby]="error('firstName') ? 'firstName-error' : null" />
          @if (error('firstName'); as m) { <span class="error" id="firstName-error">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="surname">Surname</label>
          <input id="surname" formControlName="surname" autocomplete="family-name" [class.invalid]="!!error('surname')" [attr.aria-invalid]="!!error('surname')"
                   [attr.aria-describedby]="error('surname') ? 'surname-error' : null" />
          @if (error('surname'); as m) { <span class="error" id="surname-error">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="phone">Phone (optional)</label>
          <input id="phone" type="tel" formControlName="phone" autocomplete="tel" [class.invalid]="!!error('phone')" [attr.aria-invalid]="!!error('phone')"
                   [attr.aria-describedby]="error('phone') ? 'phone-error' : null" />
          @if (error('phone'); as m) { <span class="error" id="phone-error">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="dateOfBirth">Date of birth (optional)</label>
          <input id="dateOfBirth" type="date" formControlName="dateOfBirth" [max]="today" [class.invalid]="!!error('dateOfBirth')" [attr.aria-invalid]="!!error('dateOfBirth')"
                   [attr.aria-describedby]="error('dateOfBirth') ? 'dateOfBirth-error' : null" />
          @if (error('dateOfBirth'); as m) { <span class="error" id="dateOfBirth-error">{{ m }}</span> }
        </div>

        <h2>Sign-in details</h2>
        <div class="field">
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" autocomplete="email" [class.invalid]="!!error('email')" [attr.aria-invalid]="!!error('email')"
                   [attr.aria-describedby]="error('email') ? 'email-error' : null" />
          @if (error('email'); as m) { <span class="error" id="email-error">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="password">New password (leave blank to keep)</label>
          <input id="password" type="password" formControlName="password" autocomplete="new-password" [class.invalid]="!!error('password')" [attr.aria-invalid]="!!error('password')"
                   [attr.aria-describedby]="error('password') ? 'password-error' : null" />
          @if (error('password'); as m) { <span class="error" id="password-error">{{ m }}</span> }
        </div>

        @if (form.controls.password.value) {
          <div class="field">
            <label for="confirmPassword">Confirm new password</label>
            <input id="confirmPassword" type="password" formControlName="confirmPassword" autocomplete="new-password"
                   [class.invalid]="!!error('confirmPassword')" [attr.aria-invalid]="!!error('confirmPassword')"
                   [attr.aria-describedby]="error('confirmPassword') ? 'confirmPassword-error' : null" />
            @if (error('confirmPassword'); as m) { <span class="error" id="confirmPassword-error">{{ m }}</span> }
          </div>
        }

        @if (needsCurrentPassword()) {
          <div class="field">
            <label for="currentPassword">Current password</label>
            <input id="currentPassword" type="password" formControlName="currentPassword" autocomplete="current-password"
                   [class.invalid]="!!error('currentPassword')" [attr.aria-invalid]="!!error('currentPassword')"
                   [attr.aria-describedby]="error('currentPassword') ? 'currentPassword-error' : null" />
            @if (error('currentPassword'); as m) { <span class="error" id="currentPassword-error">{{ m }}</span> }
          </div>
        }

        @if (saveError()) {
          <div class="error-box" role="alert">{{ saveError() }}</div>
        }

        <button type="submit" class="btn primary" [disabled]="busy() || !profile()">
          {{ busy() ? 'Saving...' : 'Save changes' }}
        </button>
      </form>
    </section>
  `,
})
export class ProfilePage implements OnInit {
  private readonly api = inject(ProfileApiService);
  private readonly fb = inject(FormBuilder);

  // Local date (toISOString() is UTC, which is still "yesterday" between 00:00 and 02:00 in SAST)
  protected readonly today = localIsoDate(new Date());
  protected readonly profile = signal<ClientProfile | null>(null);
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    surname: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', Validators.maxLength(30)],
    dateOfBirth: [''],
    password: ['', [Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: [''],
    currentPassword: [''],
  });

  ngOnInit(): void {
    this.api.getProfile().subscribe({
      next: (p) => this.fill(p),
      error: (p) => this.loadError.set(problemMessage(p)),
    });
  }

  protected needsCurrentPassword(): boolean {
    const p = this.profile();
    const v = this.form.getRawValue();
    return !!v.password || (!!p && v.email.trim().toLowerCase() !== p.email.toLowerCase());
  }

  protected error(field: Field): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const control = this.form.controls[field];
    if (!control.touched && !this.submitted()) return null;
    const v = this.form.getRawValue();
    switch (field) {
      case 'confirmPassword':
        return v.password && v.confirmPassword !== v.password ? CLIENT_MESSAGES.confirmPassword : null;
      case 'currentPassword':
        return this.needsCurrentPassword() && !v.currentPassword ? CLIENT_MESSAGES.currentPassword : null;
      case 'dateOfBirth':
        return v.dateOfBirth && v.dateOfBirth >= this.today ? CLIENT_MESSAGES.dateOfBirth : null;
      default:
        return control.invalid ? CLIENT_MESSAGES[field] : null;
    }
  }

  protected save(): void {
    this.submitted.set(true);
    this.message.set(null);
    this.saveError.set(null);
    this.serverErrors.set({});
    const p = this.profile();
    if (!p) return;
    const fields: Field[] = ['firstName', 'surname', 'email', 'phone', 'dateOfBirth', 'password', 'confirmPassword', 'currentPassword'];
    if (this.form.invalid || fields.some((f) => this.error(f))) return;

    const change = this.changes(p);
    if (Object.keys(change).length === 0) {
      this.message.set('Nothing to save.');
      return;
    }
    this.busy.set(true);
    this.api
      .updateProfile(change)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => {
          this.fill(updated);
          this.submitted.set(false);
          this.message.set('Profile saved.');
        },
        error: (e: ApiProblem) => this.handleError(e),
      });
  }

  private changes(p: ClientProfile): ProfileChange {
    const v = this.form.getRawValue();
    const change: ProfileChange = {};
    if (v.firstName.trim() !== p.firstName) change.firstName = v.firstName.trim();
    if (v.surname.trim() !== p.surname) change.surname = v.surname.trim();
    if (v.phone.trim() !== (p.phone ?? '')) change.phone = v.phone.trim();
    if (v.dateOfBirth && v.dateOfBirth !== p.dateOfBirth) change.dateOfBirth = v.dateOfBirth;
    if (v.email.trim().toLowerCase() !== p.email.toLowerCase()) change.email = v.email.trim();
    if (v.password) change.password = v.password;
    if (change.email || change.password) change.currentPassword = v.currentPassword;
    return change;
  }

  private fill(p: ClientProfile): void {
    this.profile.set(p);
    this.form.reset({
      firstName: p.firstName,
      surname: p.surname,
      email: p.email,
      phone: p.phone ?? '',
      dateOfBirth: p.dateOfBirth ?? '',
      password: '',
      confirmPassword: '',
      currentPassword: '',
    });
  }

  private handleError(e: ApiProblem): void {
    if (e.code === 'VALIDATION_FAILED' && e.fieldErrors?.length) {
      const errors: Partial<Record<Field, string>> = {};
      for (const fe of e.fieldErrors) errors[fe.field as Field] = fe.message;
      this.serverErrors.set(errors);
    } else if (e.code === 'CONFLICT') {
      this.serverErrors.set({ email: e.detail });
    } else {
      this.saveError.set(problemMessage(e));
    }
  }
}
