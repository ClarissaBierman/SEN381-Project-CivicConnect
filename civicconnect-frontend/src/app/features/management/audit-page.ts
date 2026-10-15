import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ManagementApiService } from '../../core/management-api.service';
import { ALL_AUDIT_RESOURCES, AUDIT_RESOURCE_LABELS, AuditRecord, AuditResource, Page } from '../../core/models';
import { problemMessage } from '../../core/problem-message';

const PAGE_SIZE = 20;

// NFR-006 / DEC-007: the audit trail, one table at a time. Read-only - rows are written by the
// database trigger. The server leaves client personal data out of /audit/clients (ASR-005).
@Component({
  selector: 'app-audit-page',
  imports: [ReactiveFormsModule, DatePipe, JsonPipe],
  template: `
    <div class="two-col wide-left">
      <section class="panel">
        <form class="filters" [formGroup]="filters" (ngSubmit)="search()" aria-label="Audit filters">
          <h1>Audit trail</h1>
          <label>
            <span class="sr-only">Table</span>
            <select formControlName="resource">
              @for (r of resources; track r) {
                <option [value]="r">{{ resourceLabels[r] }}</option>
              }
            </select>
          </label>
          <label>
            <span class="sr-only">Operation</span>
            <select formControlName="operation">
              <option value="">Operation: All</option>
              <option value="INSERT">Created</option>
              <option value="UPDATE">Updated</option>
              <option value="DELETE">Deleted</option>
            </select>
          </label>
          <label>Record ID <input formControlName="recordId" size="8" /></label>
          <button type="submit" class="btn">Search</button>
        </form>

        @if (error()) {
          <p class="error-box" role="alert">{{ error() }}</p>
        }

        <table>
          <thead>
            <tr><th>When</th><th>Operation</th><th>Record</th><th>Changed by</th><th>Fields changed</th></tr>
          </thead>
          <tbody>
            @for (a of result()?.content ?? []; track a.audId) {
              <tr class="clickable" [class.selected]="selected()?.audId === a.audId" (click)="selected.set(a)">
                <td><button type="button" class="link-btn row-link" [attr.aria-pressed]="selected()?.audId === a.audId">{{ a.changedAt | date: 'dd/MM/yyyy HH:mm' }}</button></td>
                <td>{{ operationLabels[a.operation] }}</td>
                <td>{{ a.recordId }}</td>
                <td>{{ actor(a) }}</td>
                <td>{{ a.changedFields.length ? a.changedFields.join(', ') : '-' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="hint">No audit entries match these filters.</td></tr>
            }
          </tbody>
        </table>

        @if (result(); as page) {
          <div class="pager">
            <button type="button" class="link-btn" [disabled]="page.page === 0" (click)="goToPage(-1)" aria-label="Previous page">◀</button>
            Page {{ page.page + 1 }} of {{ page.totalPages }}
            <button type="button" class="link-btn" [disabled]="page.page + 1 >= page.totalPages" (click)="goToPage(1)" aria-label="Next page">▶</button>
            <span class="hint">({{ page.totalElements }} entries)</span>
          </div>
        }
      </section>

      <section class="panel">
        @if (selected(); as a) {
          <h2>{{ resourceLabels[a.resource] }} #{{ a.recordId }}</h2>
          <p class="hint">{{ operationLabels[a.operation] }} {{ a.changedAt | date: 'dd/MM/yyyy HH:mm:ss' }} by {{ actor(a) }}</p>
          @if (a.oldData) {
            <h3>Before</h3>
            <pre class="description">{{ a.oldData | json }}</pre>
          }
          @if (a.newData) {
            <h3>After</h3>
            <pre class="description">{{ a.newData | json }}</pre>
          }
          @if (a.resource === 'clients') {
            <small class="hint">Client personal details are not shown in the audit trail (ASR-005, POPIA).</small>
          }
        } @else {
          <p class="hint">Select an entry to see the record before and after the change.</p>
        }
      </section>
    </div>
  `,
})
export class AuditPage implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly resources = ALL_AUDIT_RESOURCES;
  protected readonly resourceLabels = AUDIT_RESOURCE_LABELS;
  protected readonly operationLabels: Record<AuditRecord['operation'], string> = {
    INSERT: 'Created',
    UPDATE: 'Updated',
    DELETE: 'Deleted',
  };

  protected readonly result = signal<Page<AuditRecord> | null>(null);
  protected readonly selected = signal<AuditRecord | null>(null);
  protected readonly error = signal<string | null>(null);
  private readonly pageIndex = signal(0);

  protected readonly filters = this.fb.nonNullable.group({
    resource: 'requests' as AuditResource,
    operation: '',
    recordId: '',
  });

  ngOnInit(): void {
    this.load();
  }

  protected search(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected goToPage(delta: number): void {
    this.pageIndex.update((p) => p + delta);
    this.load();
  }

  protected actor(a: AuditRecord): string {
    if (a.empId !== null) return `Employee #${a.empId}`;
    if (a.clientId !== null) return `Requester #${a.clientId}`;
    return 'System';
  }

  private load(): void {
    const f = this.filters.getRawValue();
    this.error.set(null);
    this.selected.set(null);
    this.api
      .getAudit(f.resource, f.recordId.trim() || null, f.operation || null, this.pageIndex(), PAGE_SIZE)
      .subscribe({
        next: (page) => this.result.set(page),
        error: (p) => {
          this.result.set(null);
          this.error.set(problemMessage(p));
        },
      });
  }
}
