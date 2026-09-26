import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { InstanceHistory } from '../core/api/models';
import { humanize } from '../core/format';
import { StatusChip } from './status-chip';

/** Approval history of a request: steps, assignees, decisions (AGENT.md §88 "workflow timeline"). */
@Component({
  selector: 'ops-workflow-timeline',
  imports: [DatePipe, MatIconModule, StatusChip],
  template: `
    @if (history().length === 0) {
      <p class="muted">No approval steps yet.</p>
    }
    @for (run of history(); track run.instanceId) {
      <section class="run" [attr.aria-label]="humanize(run.definitionCode)">
        <h3>
          {{ humanize(run.definitionCode) }}
          <ops-status [status]="run.status" />
          <span class="muted small">started {{ run.createdAt | date: 'short' }}</span>
        </h3>
        <ol class="steps">
          @for (step of run.steps; track step.stepNumber) {
            <li>
              <div class="step-head">
                <strong>{{ humanize(step.stepType) }}</strong>
                <ops-status [status]="step.status" />
              </div>
              <div class="muted small">
                Assigned to {{ step.assignedEmployeeName ?? (step.assignedRole ? humanize(step.assignedRole) : '—') }}
                @if (step.assignedEmployeeName && step.assignedRole) {
                  (or any {{ humanize(step.assignedRole) }})
                }
              </div>
              @for (d of step.decisions; track d.decidedAt) {
                <div class="decision">
                  <mat-icon aria-hidden="true">{{ d.decision === 'APPROVE' ? 'thumb_up' : d.decision === 'FORWARD' ? 'forward' : 'thumb_down' }}</mat-icon>
                  <span>
                    <strong>{{ humanize(d.decision) }}</strong> by {{ d.approverName }}
                    @if (d.onBehalfOfName) {
                      <em>on behalf of {{ d.onBehalfOfName }}</em>
                    }
                    · {{ d.decidedAt | date: 'short' }}
                    @if (d.comment) {
                      <q>{{ d.comment }}</q>
                    }
                  </span>
                </div>
              }
            </li>
          }
        </ol>
      </section>
    }
  `,
  styles: `
    h3 { font: var(--mat-sys-title-small); display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 8px 0; }
    .steps { margin: 0; padding-left: 20px; }
    .steps li { margin-bottom: 12px; }
    .step-head { display: flex; gap: 12px; align-items: center; }
    .decision { display: flex; gap: 8px; align-items: flex-start; margin-top: 4px; }
    .decision mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }
    q { display: block; font-style: italic; color: var(--mat-sys-on-surface-variant); }
    .muted { color: var(--mat-sys-on-surface-variant); }
    .small { font: var(--mat-sys-body-small); }
    .run + .run { border-top: 1px solid var(--mat-sys-outline-variant); margin-top: 8px; padding-top: 8px; }
  `,
})
export class WorkflowTimeline {
  readonly history = input.required<InstanceHistory[]>();
  protected readonly humanize = humanize;
}
