import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { humanize } from '../core/format';

type Tone = 'draft' | 'pending' | 'approved' | 'rejected' | 'failed' | 'completed' | 'inactive';

const TONES: Record<string, { tone: Tone; icon: string }> = {
  DRAFT: { tone: 'draft', icon: 'edit_note' },
  SUBMITTED: { tone: 'pending', icon: 'hourglass_top' },
  IN_APPROVAL: { tone: 'pending', icon: 'hourglass_top' },
  CANCEL_REQUESTED: { tone: 'pending', icon: 'hourglass_top' },
  EXPENSES_SUBMITTED: { tone: 'pending', icon: 'hourglass_top' },
  CORRECTION_PENDING: { tone: 'pending', icon: 'hourglass_top' },
  PENDING: { tone: 'pending', icon: 'hourglass_top' },
  OPEN: { tone: 'pending', icon: 'radio_button_unchecked' },
  RUNNING: { tone: 'pending', icon: 'sync' },
  ACTIVE: { tone: 'pending', icon: 'play_circle' },
  APPROVED: { tone: 'approved', icon: 'check_circle' },
  AUTHORIZED: { tone: 'approved', icon: 'verified' },
  SUCCESS: { tone: 'approved', icon: 'check_circle' },
  UP: { tone: 'approved', icon: 'check_circle' },
  CALCULATED: { tone: 'approved', icon: 'check_circle' },
  PROCESSED: { tone: 'approved', icon: 'check_circle' },
  COMPLETED: { tone: 'completed', icon: 'task_alt' },
  SETTLED: { tone: 'completed', icon: 'paid' },
  CLOSED: { tone: 'completed', icon: 'lock' },
  REJECTED: { tone: 'rejected', icon: 'cancel' },
  RETURNED: { tone: 'rejected', icon: 'undo' },
  FAILED: { tone: 'failed', icon: 'error' },
  DOWN: { tone: 'failed', icon: 'error' },
  PARTIAL: { tone: 'failed', icon: 'warning' },
  CANCELLED: { tone: 'inactive', icon: 'block' },
  SKIPPED: { tone: 'inactive', icon: 'redo' },
  DISABLED: { tone: 'inactive', icon: 'block' },
};

/** Status as icon + text + colour; never colour alone (AGENT.md §89). */
@Component({
  selector: 'ops-status',
  imports: [MatIconModule],
  template: `<span class="status status-{{ style().tone }}">
    <mat-icon aria-hidden="true">{{ style().icon }}</mat-icon>{{ label() ?? text() }}
  </span>`,
})
export class StatusChip {
  readonly status = input.required<string>();
  readonly label = input<string>();

  protected readonly style = computed(() => TONES[this.status()] ?? { tone: 'draft' as Tone, icon: 'info' });
  protected readonly text = computed(() => humanize(this.status()));
}
