import { Component, inject, OnInit, signal } from '@angular/core';
import { Category } from '../../category';
import { CategoryService } from '../../category.service';

// The original IB-1 prototype screen (moved here from app.html). Calls the real backend
// (GET /api/v1/categories via CategoryService, with the login token).
@Component({
  selector: 'app-categories-page',
  template: `
    <section class="panel">
      <h1>Categories</h1>
      @if (error()) {
        <p class="error-box" role="alert">{{ error() }}</p>
      }
      <table>
        <thead>
          <tr><th>Category</th><th>Status</th></tr>
        </thead>
        <tbody>
          @for (category of categories(); track category.catId) {
            <tr>
              <td>{{ category.catName }}</td>
              <td>{{ category.active ? 'Active' : 'Inactive' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="2" class="hint">No categories loaded.</td></tr>
          }
        </tbody>
      </table>
      <p class="hint">Add / rename / deactivate (API-14, API-15) - Planned. Categories are deactivated, never deleted.</p>
    </section>
  `,
})
export class CategoriesPage implements OnInit {
  private readonly categoryService = inject(CategoryService);
  protected readonly categories = signal<Category[]>([]);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.categoryService.getAll().subscribe({
      next: (data) => this.categories.set(data),
      error: () => this.error.set('Could not load categories - is the backend running on port 8080?'),
    });
  }
}
