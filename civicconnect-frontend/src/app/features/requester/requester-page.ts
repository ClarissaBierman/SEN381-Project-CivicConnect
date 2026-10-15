import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import { Category } from '../../category';
import { ApiProblem, CreatedRequest, Page, RequestDetail, RequestSummary } from '../../core/models';
import { problemMessage } from '../../core/problem-message';
import { RequestApiService } from '../../core/request-api.service';
import { StatusBadge } from '../../shared/status-badge';

const PAGE_SIZE = 10;

type Field = 'categoryId' | 'description' | 'location' | 'attachmentUrl';

const FIELD_LABELS: Record<Field, string> = {
  categoryId: 'Category',
  description: 'Description',
  location: 'Location',
  attachmentUrl: 'Attachment link',
};

// Wireframe 1 - Requester: submit a request (FR-001) and view my requests (FR-002 to FR-004).
@Component({
  selector: 'app-requester-page',
  imports: [ReactiveFormsModule, DatePipe, StatusBadge],
  templateUrl: './requester-page.html',
})
export class RequesterPage implements OnInit {
  private readonly api = inject(RequestApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  // Each click cancels the previous request's load, so only the last one clicked is shown
  private readonly selection = new Subject<number>();

  protected readonly categories = signal<Category[]>([]);
  protected readonly myRequests = signal<Page<RequestSummary> | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly detailError = signal<string | null>(null);
  protected readonly selected = signal<RequestDetail | null>(null);
  protected readonly confirmation = signal<CreatedRequest | null>(null);
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});
  protected readonly formError = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly saving = signal(false);

  // Angular validation is for usability only - the server re-validates (marker 3).
  protected readonly form = this.fb.group({
    categoryId: this.fb.control<number | null>(null, Validators.required),
    description: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(2000)]),
    location: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(255)]),
    attachmentUrl: this.fb.nonNullable.control('', Validators.pattern(/^https?:\/\/\S+$/)),
  });

  ngOnInit(): void {
    // Marker 2: dropdown comes from API-13 (active categories only), no free text.
    this.api.getCategories().subscribe((c) => this.categories.set(c));
    this.selection
      .pipe(
        tap(() => this.detailError.set(null)),
        switchMap((id) =>
          this.api.getRequest(id).pipe(
            catchError((p) => {
              this.detailError.set(problemMessage(p));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((detail) => this.selected.set(detail));
    this.loadMine();
  }

  protected goToPage(delta: number): void {
    this.pageIndex.update((p) => p + delta);
    this.loadMine();
  }

  protected errorFor(field: Field): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const control = this.form.controls[field];
    if (!(control.touched || this.submitted())) return null;
    if (control.hasError('required')) return `${FIELD_LABELS[field]} is required`;
    if (control.hasError('maxlength')) {
      return `${FIELD_LABELS[field]} must be ${control.getError('maxlength').requiredLength} characters or fewer`;
    }
    if (control.hasError('pattern')) return 'Enter a link starting with https://';
    return null;
  }

  protected clearServerError(field: Field): void {
    if (this.serverErrors()[field]) this.serverErrors.update(({ [field]: _, ...rest }) => rest);
  }

  protected submit(): void {
    this.submitted.set(true);
    this.serverErrors.set({});
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .submitRequest({
        categoryId: v.categoryId!,
        description: v.description.trim(),
        location: v.location.trim(),
        attachmentUrl: v.attachmentUrl.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (created) => {
          this.confirmation.set(created); // marker 7
          this.cancel();
          this.pageIndex.set(0);
          this.loadMine();
          this.select(created.id);
        },
        error: (p: ApiProblem) => this.showProblem(p),
      });
  }

  protected cancel(): void {
    this.form.reset();
    this.submitted.set(false);
    this.serverErrors.set({});
    this.formError.set(null);
  }

  protected select(id: number): void {
    // Marker 5: API-06 - another client's request returns 404.
    this.selection.next(id);
  }

  private loadMine(): void {
    // Marker 4: API-05 - own requests only, newest first, paged.
    this.api.getMyRequests(this.pageIndex(), PAGE_SIZE).subscribe({
      next: (page) => {
        this.myRequests.set(page);
        if (!this.selected() && page.content.length) this.select(page.content[0].id);
      },
      error: (p) => this.detailError.set(problemMessage(p)),
    });
  }

  private showProblem(p: ApiProblem): void {
    if (p.code === 'VALIDATION_FAILED' && p.fieldErrors?.length) {
      // 400 fieldErrors are shown next to the matching field.
      const errors: Partial<Record<Field, string>> = {};
      for (const fe of p.fieldErrors) errors[fe.field as Field] = fe.message;
      this.serverErrors.set(errors);
    } else {
      this.formError.set(problemMessage(p));
    }
  }
}
