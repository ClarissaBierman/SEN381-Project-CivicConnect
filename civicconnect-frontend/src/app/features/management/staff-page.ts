import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, of, Subject } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import { Category } from '../../category';
import { ManagementApiService } from '../../core/management-api.service';
import { ApiProblem, Employee, EMPLOYEE_ROLE_LABELS, EmployeeRole } from '../../core/models';
import { problemMessage } from '../../core/problem-message';
import { RequestApiService } from '../../core/request-api.service';

type NewField = 'firstName' | 'surname' | 'email' | 'password' | 'phone';

// Management: employee accounts and the categories each staff member covers (FR-005, API-16).
// Deactivating a staff member or removing a category takes them off their open requests in it;
// the last manager cannot be removed.
@Component({
  selector: 'app-staff-page',
  imports: [ReactiveFormsModule, FormsModule],
  template: `
    <div class="two-col wide-left">
      <section class="panel">
        <h1>Staff</h1>
        @if (listError()) {
          <p class="error-box" role="alert">{{ listError() }}</p>
        }
        <table>
          <thead>
            <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th></tr>
          </thead>
          <tbody>
            @for (e of employees(); track e.id) {
              <tr class="clickable" [class.selected]="selected()?.id === e.id" (click)="select(e)">
                <td><button type="button" class="link-btn row-link" [attr.aria-pressed]="selected()?.id === e.id">{{ e.firstName }} {{ e.surname }}</button></td>
                <td>{{ e.email }}</td>
                <td>{{ roleLabels[e.role] }}</td>
                <td>{{ e.active ? 'Active' : 'Inactive' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="4" class="hint">No employees loaded.</td></tr>
            }
          </tbody>
        </table>

        <h2>Add employee</h2>
        <form [formGroup]="newForm" (ngSubmit)="create()" novalidate>
          <div class="inline-row">
            <div class="field">
              <label for="firstName">First name</label>
              <input id="firstName" formControlName="firstName" [class.invalid]="!!newError('firstName')" [attr.aria-invalid]="!!newError('firstName')"
                     [attr.aria-describedby]="newError('firstName') ? 'new-firstName-error' : null" />
              @if (newError('firstName'); as m) { <small class="error" id="new-firstName-error">{{ m }}</small> }
            </div>
            <div class="field">
              <label for="surname">Surname</label>
              <input id="surname" formControlName="surname" [class.invalid]="!!newError('surname')" [attr.aria-invalid]="!!newError('surname')"
                     [attr.aria-describedby]="newError('surname') ? 'new-surname-error' : null" />
              @if (newError('surname'); as m) { <small class="error" id="new-surname-error">{{ m }}</small> }
            </div>
          </div>
          <div class="inline-row">
            <div class="field">
              <label for="email">Email</label>
              <input id="email" type="email" formControlName="email" [class.invalid]="!!newError('email')" [attr.aria-invalid]="!!newError('email')"
                     [attr.aria-describedby]="newError('email') ? 'new-email-error' : null" />
              @if (newError('email'); as m) { <small class="error" id="new-email-error">{{ m }}</small> }
            </div>
            <div class="field">
              <label for="phone">Phone (optional)</label>
              <input id="phone" type="tel" formControlName="phone" />
            </div>
          </div>
          <div class="inline-row">
            <div class="field">
              <label for="password">Temporary password</label>
              <input id="password" type="password" formControlName="password" autocomplete="new-password"
                     [class.invalid]="!!newError('password')" [attr.aria-invalid]="!!newError('password')"
                     [attr.aria-describedby]="newError('password') ? 'new-password-error' : null" />
              @if (newError('password'); as m) { <small class="error" id="new-password-error">{{ m }}</small> }
            </div>
            <div class="field">
              <label for="role">Role</label>
              <select id="role" formControlName="role">
                @for (r of roles; track r) {
                  <option [value]="r">{{ roleLabels[r] }}</option>
                }
              </select>
            </div>
          </div>
          @if (createError()) {
            <p class="error-box" role="alert">{{ createError() }}</p>
          }
          <button type="submit" class="btn primary" [disabled]="busy()">Add employee</button>
        </form>
      </section>

      <section class="panel">
        @if (selected(); as e) {
          <h2>{{ e.firstName }} {{ e.surname }}</h2>
          <p class="hint">{{ e.email }}{{ e.phone ? ' | ' + e.phone : '' }}</p>

          @if (message()) {
            <p class="confirm-box" role="status">{{ message() }}</p>
          }
          @if (detailError()) {
            <p class="error-box" role="alert">{{ detailError() }}</p>
          }

          <div class="inline-row">
            <label for="editRole">Role:</label>
            <select id="editRole" [(ngModel)]="editRole">
              @for (r of roles; track r) {
                <option [ngValue]="r">{{ roleLabels[r] }}</option>
              }
            </select>
            <button type="button" class="btn" [disabled]="busy() || editRole === e.role" (click)="saveRole(e)">Save role</button>
          </div>
          <div class="inline-row">
            <span>Status: {{ e.active ? 'Active' : 'Inactive' }}</span>
            <button type="button" class="btn" [disabled]="busy()" (click)="toggleActive(e)">
              {{ e.active ? 'Deactivate' : 'Activate' }}
            </button>
          </div>
          <small class="hint">
            Deactivating a staff member, or making them a manager, takes them off their open requests
            (Assigned ones go back to the queue). The last active manager cannot be deactivated.
          </small>

          @if (e.role === 'STAFF') {
            <h3>Categories covered</h3>
            <fieldset class="stack" [disabled]="busy() || loadingCategories()">
              <legend class="sr-only">Categories this staff member covers</legend>
              @for (c of categories(); track c.catId) {
                <label class="inline-row">
                  <input type="checkbox" [checked]="chosen().has(c.catId!)" (change)="toggleCategory(c.catId!)" />
                  {{ c.catName }}{{ c.active ? '' : ' (inactive)' }}
                </label>
              }
            </fieldset>
            <div class="inline-row">
              <button type="button" class="btn" [disabled]="busy() || loadingCategories() || !categoriesChanged()" (click)="saveCategories(e)">
                Save categories
              </button>
              <small class="hint">Removing a category returns their open requests in it to the queue.</small>
            </div>
          }
        } @else {
          <p class="hint">Select an employee to change their role, status or categories.</p>
        }
      </section>
    </div>
  `,
})
export class StaffPage implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly requests = inject(RequestApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  // Each click cancels the previous employee's category load, so the boxes always belong to the
  // employee shown (otherwise "Save categories" could save one person's categories onto another)
  private readonly selection = new Subject<Employee>();

  protected readonly roleLabels = EMPLOYEE_ROLE_LABELS;
  protected readonly roles = Object.keys(EMPLOYEE_ROLE_LABELS) as EmployeeRole[];

  protected readonly employees = signal<Employee[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly selected = signal<Employee | null>(null);
  protected readonly chosen = signal<Set<number>>(new Set());
  private readonly saved = signal<Set<number>>(new Set());
  protected readonly busy = signal(false);
  protected readonly loadingCategories = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly detailError = signal<string | null>(null);
  protected readonly listError = signal<string | null>(null);
  protected readonly createError = signal<string | null>(null);
  protected readonly createSubmitted = signal(false);
  protected readonly serverErrors = signal<Partial<Record<NewField, string>>>({});

  protected readonly categoriesChanged = computed(() => {
    const a = this.chosen();
    const b = this.saved();
    return a.size !== b.size || [...a].some((id) => !b.has(id));
  });

  protected editRole: EmployeeRole = 'STAFF';

  protected readonly newForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    surname: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', Validators.maxLength(30)],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: ['STAFF' as EmployeeRole],
  });

  ngOnInit(): void {
    this.selection
      .pipe(
        tap((e) => {
          this.selected.set(e);
          this.editRole = e.role;
          this.message.set(null);
          this.detailError.set(null);
          this.chosen.set(new Set());
          this.saved.set(new Set());
          this.loadingCategories.set(e.role === 'STAFF');
        }),
        switchMap((e) =>
          e.role !== 'STAFF'
            ? of(null)
            : this.api.getEmployeeCategories(e.id).pipe(
                catchError((p) => {
                  this.detailError.set(problemMessage(p));
                  return of(null);
                }),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((list) => {
        this.loadingCategories.set(false);
        if (!list) return;
        const ids = new Set(list.map((c) => c.catId!));
        this.chosen.set(new Set(ids));
        this.saved.set(ids);
      });
    this.loadEmployees();
    this.requests.getCategories(false).subscribe((c) => this.categories.set(c));
  }

  protected newError(field: NewField): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const control = this.newForm.controls[field];
    if (!control.invalid || !(control.touched || this.createSubmitted())) return null;
    if (control.hasError('required')) return 'Required';
    if (control.hasError('email')) return 'Enter a valid email address';
    if (control.hasError('minlength')) return 'At least 8 characters';
    return 'Too long';
  }

  protected select(e: Employee): void {
    this.selection.next(e);
  }

  protected toggleCategory(id: number): void {
    const next = new Set(this.chosen());
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.chosen.set(next);
  }

  protected saveRole(e: Employee): void {
    this.run(this.api.updateEmployee(e.id, { role: this.editRole }), `Role changed to ${this.roleLabels[this.editRole]}.`);
  }

  protected toggleActive(e: Employee): void {
    this.run(this.api.updateEmployee(e.id, { active: !e.active }), e.active ? 'Account deactivated.' : 'Account activated.');
  }

  // The reply only updates the boxes if the same employee is still selected (D-38).
  protected saveCategories(e: Employee): void {
    this.busy.set(true);
    this.detailError.set(null);
    this.message.set(null);
    this.api
      .setEmployeeCategories(e.id, [...this.chosen()])
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (list) => {
          if (this.selected()?.id !== e.id) return;
          const ids = new Set(list.map((c) => c.catId!));
          this.chosen.set(new Set(ids));
          this.saved.set(ids);
          this.message.set('Categories saved.');
        },
        error: (p) => {
          if (this.selected()?.id === e.id) this.detailError.set(problemMessage(p));
        },
      });
  }

  protected create(): void {
    this.createSubmitted.set(true);
    this.createError.set(null);
    this.serverErrors.set({});
    if (this.newForm.invalid) {
      this.newForm.markAllAsTouched();
      return;
    }
    const v = this.newForm.getRawValue();
    this.busy.set(true);
    this.api
      .createEmployee({
        firstName: v.firstName.trim(),
        surname: v.surname.trim(),
        email: v.email.trim(),
        password: v.password,
        phone: v.phone.trim() || null,
        role: v.role,
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (created) => {
          this.newForm.reset();
          this.createSubmitted.set(false);
          this.loadEmployees(created.id);
        },
        error: (p: ApiProblem) => {
          if (p.code === 'VALIDATION_FAILED' && p.fieldErrors?.length) {
            const errors: Partial<Record<NewField, string>> = {};
            for (const fe of p.fieldErrors) errors[fe.field as NewField] = fe.message;
            this.serverErrors.set(errors);
          } else if (p.code === 'CONFLICT') {
            this.serverErrors.set({ email: p.detail });
          } else {
            this.createError.set(problemMessage(p));
          }
        },
      });
  }

  // Role and status changes: reload, so the list and the selected employee show the server's state.
  private run(call: Observable<Employee>, done: string): void {
    this.busy.set(true);
    this.detailError.set(null);
    this.message.set(null);
    call.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (updated) => this.loadEmployees(updated.id, done),
      error: (p) => this.detailError.set(problemMessage(p)),
    });
  }

  private loadEmployees(selectId?: number, keepMessage?: string): void {
    this.api.getEmployees().subscribe({
      next: (page) => {
        this.employees.set(page.content);
        const match = page.content.find((e) => e.id === selectId);
        if (match) {
          this.select(match);
          if (keepMessage) this.message.set(keepMessage);
        }
      },
      error: (p) => this.listError.set(problemMessage(p)),
    });
  }
}
