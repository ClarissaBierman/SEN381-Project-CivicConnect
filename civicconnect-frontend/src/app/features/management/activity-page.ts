import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Category } from '../../category';
import {
  ActivityReport,
  ALL_PRIORITIES,
  ALL_STATUSES,
  HandlerOption,
  Priority,
  PRIORITY_LABELS,
  RequestDetail,
  RequestStatus,
  RequestSummary,
  STATUS_LABELS,
} from '../../core/models';
import { problemMessage } from '../../core/problem-message';
import { RequestApiService } from '../../core/request-api.service';
import { StatusBadge } from '../../shared/status-badge';

// yyyy-MM-dd in local time (toISOString would shift the date back a day in UTC+2)
const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const daysBack = (n: number) => new Date(Date.now() - n * 24 * 60 * 60 * 1000);

// Wireframe 3 - Management: service activity overview (FR-012, FR-013) + request priority (FR-016).
@Component({
  selector: 'app-activity-page',
  imports: [ReactiveFormsModule, FormsModule, DatePipe, StatusBadge],
  templateUrl: './activity-page.html',
})
export class ActivityPage implements OnInit {
  private readonly api = inject(RequestApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly statuses = ALL_STATUSES;
  protected readonly priorities = ALL_PRIORITIES;

  protected readonly categories = signal<Category[]>([]);
  protected readonly report = signal<ActivityReport | null>(null);
  protected readonly lastRefreshed = signal<Date | null>(null);
  protected readonly reportError = signal<string | null>(null);

  protected readonly openRequests = signal<RequestSummary[]>([]);
  protected readonly selected = signal<RequestDetail | null>(null);
  protected readonly handlers = signal<HandlerOption[]>([]);
  protected readonly priorityMessage = signal<string | null>(null);
  protected readonly priorityError = signal<string | null>(null);

  // Marker 2: FR-013 filters. Counts only recalculate on Apply, not on every keystroke.
  protected readonly filters = this.fb.nonNullable.group({
    from: isoDate(daysBack(30)),
    to: isoDate(new Date()),
    categoryId: 0,
    status: '' as RequestStatus | '',
  });

  protected selectedId: number | null = null;
  protected priority: Priority = 'MEDIUM';
  protected assignTo: number | null = null;

  ngOnInit(): void {
    this.api.getCategories(false).subscribe((c) => this.categories.set(c));
    this.apply();
    this.loadOpenRequests();
  }

  // Marker 1: API-17 GET /reports/activity - Manager role only, counts only.
  protected apply(): void {
    const f = this.filters.getRawValue();
    this.reportError.set(null);
    this.api.getActivity(f.from || null, f.to || null, Number(f.categoryId) || null, f.status || null).subscribe({
      next: (r) => {
        this.report.set(r);
        this.lastRefreshed.set(new Date());
      },
      error: (p) => this.reportError.set(problemMessage(p)),
    });
  }

  protected selectRequest(id: number | null): void {
    this.selectedId = id;
    this.priorityMessage.set(null);
    this.priorityError.set(null);
    if (id === null) {
      this.selected.set(null);
      return;
    }
    this.api.getRequest(id).subscribe({
      next: (d) => {
        this.selected.set(d);
        this.priority = d.priority ?? 'MEDIUM';
        this.assignTo = null;
        this.api.getHandlers(d.categoryId).subscribe((h) => this.handlers.set(h));
      },
      error: (p) => this.priorityError.set(problemMessage(p)),
    });
  }

  // Marker 5: FR-016 / API-12 - only Management sets priority.
  protected savePriority(): void {
    const d = this.selected();
    if (!d) return;
    this.api.setPriority(d.id, this.priority).subscribe({
      next: (updated) => {
        this.selected.set(updated);
        this.priorityMessage.set('Priority saved.');
        this.loadOpenRequests();
      },
      error: (p) => this.priorityError.set(problemMessage(p)),
    });
  }

  // API-08 - a manager may assign any handler who covers the category.
  protected assign(): void {
    const d = this.selected();
    if (!d || this.assignTo === null) return;
    this.api.assign(d.id, this.assignTo).subscribe({
      next: (updated) => {
        this.selected.set(updated);
        this.priorityMessage.set(`Assigned to ${updated.handlerName}.`);
        this.loadOpenRequests();
        this.apply();
      },
      error: (p) => this.priorityError.set(problemMessage(p)),
    });
  }

  private loadOpenRequests(): void {
    const base = { categoryId: null, from: null, to: null, sort: 'createdAt,desc' as const, page: 0, size: 100 };
    this.api.getRequests({ ...base, status: null }).subscribe((page) =>
      this.openRequests.set(page.content.filter((r) => ['NEW', 'ASSIGNED', 'IN_PROGRESS'].includes(r.status))),
    );
  }
}
