import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
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
const dayBefore = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return isoDate(new Date(y, m - 1, d - 1));
};

const LIST_SIZE = 100;
const REASSIGNABLE: RequestStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS'];

// Which open requests the "Request priority" picker lists
type ListMode = 'range' | 'unassigned' | 'older';

// Wireframe 3 - Management: service activity overview (FR-012, FR-013) + request priority (FR-016).
@Component({
  selector: 'app-activity-page',
  imports: [ReactiveFormsModule, FormsModule, DatePipe, StatusBadge],
  templateUrl: './activity-page.html',
})
export class ActivityPage implements OnInit {
  private readonly api = inject(RequestApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  // Each pick cancels the previous request's load, so the panel always shows the last one picked
  private readonly selection = new Subject<number | null>();

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly statuses = ALL_STATUSES;
  protected readonly priorities = ALL_PRIORITIES;

  protected readonly categories = signal<Category[]>([]);
  protected readonly report = signal<ActivityReport | null>(null);
  protected readonly lastRefreshed = signal<Date | null>(null);
  protected readonly reportError = signal<string | null>(null);

  protected readonly openRequests = signal<RequestSummary[]>([]);
  protected readonly openTotal = signal(0);
  protected readonly listMode = signal<ListMode>('range');
  protected readonly unassignedCount = signal<number | null>(null);
  protected readonly olderCount = signal<number | null>(null);
  protected readonly olderBefore = signal<string | null>(null);
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
    this.selection
      .pipe(
        tap(() => {
          this.selected.set(null);
          this.handlers.set([]);
          this.assignTo = null;
        }),
        switchMap((id) =>
          id === null
            ? of(null)
            : this.api.getRequest(id).pipe(
                switchMap((d) =>
                  REASSIGNABLE.includes(d.status)
                    ? this.api.getHandlers(d.categoryId).pipe(map((h) => ({ d, h })))
                    : of({ d, h: [] as HandlerOption[] }),
                ),
                catchError((p) => {
                  this.priorityError.set(problemMessage(p));
                  return of(null);
                }),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((r) => {
        if (!r) return;
        this.selected.set(r.d);
        this.priority = r.d.priority ?? 'MEDIUM';
        this.handlers.set(r.h.filter((h) => h.id !== r.d.handlerId));
      });
    this.apply();
  }

  // Marker 1: API-17 GET /reports/activity - Manager role only, counts only.
  protected apply(): void {
    const f = this.filters.getRawValue();
    this.reportError.set(null);
    this.api
      .getActivity(f.from || null, f.to || null, Number(f.categoryId) || null, f.status || null)
      .subscribe({
        next: (r) => {
          this.report.set(r);
          this.lastRefreshed.set(new Date());
        },
        error: (p) => this.reportError.set(problemMessage(p)),
      });
    this.loadCounts();
    this.loadOpenRequests();
  }

  // The two buttons under the tiles: open requests the date range leaves out of the counts
  protected showList(mode: ListMode): void {
    this.listMode.set(this.listMode() === mode ? 'range' : mode);
    this.selectRequest(null);
    this.loadOpenRequests();
  }

  protected selectRequest(id: number | null): void {
    this.selectedId = id;
    this.priorityMessage.set(null);
    this.priorityError.set(null);
    this.selection.next(id);
  }

  protected canAssign(d: RequestDetail): boolean {
    return REASSIGNABLE.includes(d.status);
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

  // API-08 - a manager may assign or reassign any active handler who covers the category.
  protected assign(): void {
    const d = this.selected();
    if (!d || this.assignTo === null) return;
    this.api.assign(d.id, this.assignTo).subscribe({
      next: (updated) => {
        this.priorityMessage.set(`Assigned to ${updated.handlerName}.`);
        this.selection.next(updated.id);
        this.apply();
      },
      error: (p) => this.priorityError.set(problemMessage(p)),
    });
  }

  private loadCounts(): void {
    const f = this.filters.getRawValue();
    const categoryId = Number(f.categoryId) || null;
    const base = {
      status: null,
      open: true,
      categoryId,
      sort: 'createdAt,desc' as const,
      page: 0,
      size: 1,
    };
    this.olderBefore.set(f.from || null);
    forkJoin({
      unassigned: this.api.getRequests({ ...base, unassigned: true, from: null, to: null }),
      older: f.from
        ? this.api.getRequests({ ...base, from: null, to: dayBefore(f.from) })
        : of(null),
    }).subscribe({
      next: ({ unassigned, older }) => {
        this.unassignedCount.set(unassigned.totalElements);
        this.olderCount.set(older?.totalElements ?? 0);
      },
      error: (p) => this.reportError.set(problemMessage(p)),
    });
  }

  // FR-013: the picker follows the status, category and date filters (open requests only)
  private loadOpenRequests(): void {
    const f = this.filters.getRawValue();
    const categoryId = Number(f.categoryId) || null;
    const base = {
      status: f.status || null,
      open: true,
      categoryId,
      sort: 'createdAt,desc' as const,
      page: 0,
      size: LIST_SIZE,
    };
    const mode = this.listMode();
    const query =
      mode === 'unassigned'
        ? { ...base, unassigned: true, from: null, to: null }
        : mode === 'older' && f.from
          ? { ...base, from: null, to: dayBefore(f.from) }
          : { ...base, from: f.from || null, to: f.to || null };
    this.api.getRequests(query).subscribe({
      next: (page) => {
        this.openRequests.set(page.content);
        this.openTotal.set(page.totalElements);
      },
      error: (p) => this.priorityError.set(problemMessage(p)),
    });
  }
}
