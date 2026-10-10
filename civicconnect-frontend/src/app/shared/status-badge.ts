import { Component, computed, input } from '@angular/core';
import { RequestStatus, STATUS_LABELS } from '../core/models';

// Status is always shown as text, not only as colour (accessibility, PED 22.3).
@Component({
  selector: 'app-status-badge',
  template: `<span class="badge">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<RequestStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}
