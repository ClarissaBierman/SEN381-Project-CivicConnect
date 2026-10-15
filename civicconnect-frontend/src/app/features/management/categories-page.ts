import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Category } from '../../category';
import { ManagementApiService } from '../../core/management-api.service';
import { problemMessage } from '../../core/problem-message';
import { RequestApiService } from '../../core/request-api.service';

// FR-015 / API-13 to API-15: the Management-controlled category list. Categories are deactivated,
// never deleted, because existing requests still use them.
@Component({
  selector: 'app-categories-page',
  imports: [FormsModule],
  template: `
    <div class="two-col wide-left">
      <section class="panel">
        <h1>Categories</h1>
        @if (error()) {
          <p class="error-box" role="alert">{{ error() }}</p>
        }
        @if (message()) {
          <p class="confirm-box" role="status">{{ message() }}</p>
        }
        <table>
          <thead>
            <tr><th>Category</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            @for (c of categories(); track c.catId) {
              <tr>
                <td>
                  @if (editingId() === c.catId) {
                    <label class="sr-only" [for]="'name-' + c.catId">New name</label>
                    <input [id]="'name-' + c.catId" [(ngModel)]="editName" maxlength="100" />
                  } @else {
                    {{ c.catName }}
                  }
                </td>
                <td>{{ c.active ? 'Active' : 'Inactive' }}</td>
                <td>
                  <div class="inline-row">
                    @if (editingId() === c.catId) {
                      <button type="button" class="btn" [disabled]="busy() || !editName.trim()" (click)="saveName(c)">Save</button>
                      <button type="button" class="link-btn" (click)="editingId.set(null)">Cancel</button>
                    } @else {
                      <button type="button" class="link-btn" (click)="startEdit(c)">Rename</button>
                      <button type="button" class="link-btn" [disabled]="busy()" (click)="toggleActive(c)">
                        {{ c.active ? 'Deactivate' : 'Activate' }}
                      </button>
                    }
                  </div>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="3" class="hint">No categories loaded.</td></tr>
            }
          </tbody>
        </table>
        <small class="hint">Inactive categories cannot be chosen for new requests; existing requests keep them.</small>
      </section>

      <section class="panel">
        <h2>Add category</h2>
        <form (ngSubmit)="add()">
          <div class="field">
            <label for="newCategory">Name</label>
            <input id="newCategory" name="newCategory" [(ngModel)]="newName" maxlength="100" />
          </div>
          <button type="submit" class="btn primary" [disabled]="busy() || !newName.trim()">Add</button>
        </form>
      </section>
    </div>
  `,
})
export class CategoriesPage implements OnInit {
  private readonly requests = inject(RequestApiService);
  private readonly api = inject(ManagementApiService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly editingId = signal<number | null>(null);

  protected newName = '';
  protected editName = '';

  ngOnInit(): void {
    this.load();
  }

  protected startEdit(c: Category): void {
    this.editingId.set(c.catId ?? null);
    this.editName = c.catName;
  }

  protected saveName(c: Category): void {
    this.run(this.api.updateCategory(c.catId!, { catName: this.editName.trim() }), `Renamed to "${this.editName.trim()}".`);
  }

  protected toggleActive(c: Category): void {
    this.run(
      this.api.updateCategory(c.catId!, { active: !c.active }),
      `${c.catName} ${c.active ? 'deactivated' : 'activated'}.`,
    );
  }

  protected add(): void {
    const name = this.newName.trim();
    this.run(this.api.createCategory(name), `Added "${name}".`, () => (this.newName = ''));
  }

  private run(call: Observable<unknown>, done: string, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    call.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.message.set(done);
        this.editingId.set(null);
        after?.();
        this.load();
      },
      error: (p) => this.error.set(problemMessage(p)),
    });
  }

  private load(): void {
    this.requests.getCategories(false).subscribe({
      next: (data) => this.categories.set(data),
      error: (p) => this.error.set(problemMessage(p)),
    });
  }
}
