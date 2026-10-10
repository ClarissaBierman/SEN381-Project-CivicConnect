import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

// Placeholder for screens in the screen map that are not built yet - shown honestly as Planned.
@Component({
  selector: 'app-planned-page',
  template: `
    <section class="panel">
      <h1>{{ title }}</h1>
      <p class="hint">Not built yet. {{ note }}</p>
    </section>
  `,
})
export class PlannedPage {
  private readonly data = inject(ActivatedRoute).snapshot.data;
  protected readonly title = this.data['title'] as string;
  protected readonly note = this.data['note'] as string;
}
