import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { Category } from '../../category';
import {
  ALL_STATUSES,
  AuditEntry,
  HandlerOption,
  Page,
  PRIORITY_LABELS,
  RequestDetail,
  RequestStatus,
  RequestSummary,
  STATUS_LABELS,
} from '../../core/models';
import { problemMessage } from '../../core/problem-message';
import { RequestApiService } from '../../core/request-api.service';
import { SessionService } from '../../core/session.service';
import { StatusBadge } from '../../shared/status-badge';

const PAGE_SIZE = 20;

// Wireframe 2 - Service Handler: request queue (FR-005, FR-006) and request detail (FR-007 to FR-011).
// Also used by Management for "Requests" (all categories, can assign any handler - API-08).
@Component({
  selector: 'app-request-queue-page',
  imports: [ReactiveFormsModule, FormsModule, DatePipe, StatusBadge],
  templateUrl: './request-queue-page.html',
})
export class RequestQueuePage implements OnInit {
  private readonly api = inject(RequestApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly session = inject(SessionService);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly statuses = ALL_STATUSES;
  protected readonly isManager = computed(() => this.session.user()?.role === 'MANAGER');

  protected readonly categories = signal<Category[]>([]);
  protected readonly result = signal<Page<RequestSummary> | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly detail = signal<RequestDetail | null>(null);
  protected readonly audit = signal<AuditEntry[]>([]);
  protected readonly handlers = signal<HandlerOption[]>([]);
  protected readonly detailError = signal<string | null>(null);
  protected readonly busy = signal(false);

  // Marker 2: filters (FR-006). Paging happens in the database (ASR-004, 2 s target).
  protected readonly filters = this.fb.nonNullable.group({
    status: '' as RequestStatus | '',
    categoryId: 0,
    from: '',
    to: '',
    sort: 'createdAt,desc' as 'createdAt,desc' | 'createdAt,asc',
  });

  protected toStatus: RequestStatus | null = null;
  protected note = '';
  protected assignTo: number | null = null;

  protected readonly isReadOnly = computed(() => {
    const s = this.detail()?.status;
    return s === 'CLOSED' || s === 'REJECTED'; // FR-011
  });

  ngOnInit(): void {
    this.api.getCategories(false).subscribe((c) => this.categories.set(c));
    this.filters.valueChanges.pipe(debounceTime(200), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageIndex.set(0);
      this.loadList();
    });
    this.loadList();
  }

  protected goToPage(delta: number): void {
    this.pageIndex.update((p) => p + delta);
    this.loadList();
  }

  protected open(id: number): void {
    this.detailError.set(null);
    this.note = '';
    // Detail + audit are two calls (API-06, API-11) - the trade-off noted for the DTO decision.
    forkJoin({ detail: this.api.getRequest(id), audit: this.api.getAudit(id) }).subscribe({
      next: ({ detail, audit }) => {
        this.detail.set(detail);
        this.audit.set(audit);
        this.toStatus = detail.allowedTransitions[0] ?? null;
        this.assignTo = null;
        if (this.isManager() && detail.handlerId === null) {
          this.api.getHandlers(detail.categoryId).subscribe((h) => this.handlers.set(h));
        }
      },
      error: (p) => this.detailError.set(problemMessage(p)),
    });
  }

  // Marker 5: FR-009 / API-09 - only allowedTransitions are offered; the server still returns 422 for illegal moves.
  protected updateStatus(): void {
    const d = this.detail();
    if (!d || !this.toStatus) return;
    this.run(this.api.changeStatus(d.id, this.toStatus, d.version));
  }

  // Marker 7: FR-010 / API-10 - append-only.
  protected addNote(): void {
    const d = this.detail();
    if (!d || !this.note.trim()) return;
    this.run(this.api.addComment(d.id, this.note));
  }

  // Marker 9: FR-008 / API-08 - a second handler accepting at the same time gets 409 ALREADY_ASSIGNED.
  protected accept(): void {
    const d = this.detail();
    if (!d) return;
    this.run(this.api.assign(d.id, d.version, this.isManager() ? (this.assignTo ?? undefined) : undefined));
  }

  protected refresh(): void {
    const d = this.detail();
    if (d) this.open(d.id);
    this.loadList();
  }

  protected labelsFor(statuses: RequestStatus[]): string {
    return statuses.map((s) => STATUS_LABELS[s]).join(', ');
  }

  // Every write reloads detail + audit so the screen shows the new version number (DEC-006).
  private run(call: Observable<unknown>): void {
    const id = this.detail()!.id;
    this.busy.set(true);
    this.detailError.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.open(id);
        this.loadList();
      },
      error: (p) => {
        this.busy.set(false);
        this.detailError.set(problemMessage(p)); // marker 6: e.g. VERSION_CONFLICT message
      },
    });
  }

  private loadList(): void {
    const f = this.filters.getRawValue();
    // Marker 1: FR-005 - the server only returns requests in this handler's categories (API-07).
    this.api
      .getRequests({
        status: f.status || null,
        categoryId: Number(f.categoryId) || null,
        from: f.from || null,
        to: f.to || null,
        sort: f.sort,
        page: this.pageIndex(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (page) => this.result.set(page),
        error: (p) => this.detailError.set(problemMessage(p)),
      });
  }
}
