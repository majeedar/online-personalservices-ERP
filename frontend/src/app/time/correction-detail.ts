import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { TimeCorrection } from '../core/api/models';
import { humanize } from '../core/format';
import { askDecision } from '../shared/decision-dialog';
import { StatusChip } from '../shared/status-chip';
import { WorkflowTimeline } from '../shared/workflow-timeline';
import { tr } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

/** A time correction, for the employee and for the approving supervisor / time admin. */
@Component({
  selector: 'ops-correction-detail',
  imports: [I18N_PIPES, MatButtonModule, MatCardModule, MatIconModule, StatusChip, WorkflowTimeline],
  template: `
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    @if (correction(); as c) {
      <header class="page-header">
        <div>
          <h1>{{ 'Time correction' | tr }} · {{ c.date | ldate: 'fullDate' }}</h1>
          <p><ops-status [status]="c.status" /></p>
        </div>
        @if (c.actionableTaskId) {
          <div class="actions">
            <button mat-flat-button type="button" (click)="decide('approve')" [disabled]="busy()"><mat-icon>check</mat-icon> {{ 'Approve and apply' | tr }}</button>
            <button mat-stroked-button type="button" (click)="decide('reject')" [disabled]="busy()">{{ 'Reject' | tr }}</button>
          </div>
        }
      </header>
      <div class="grid-2">
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>{{ 'Requested change' | tr }}</h2></mat-card-title></mat-card-header>
          <mat-card-content>
            <dl class="dl">
              <dt>{{ 'Correction' | tr }}</dt><dd>{{ humanize(c.operation) }}</dd>
              @if (c.requestedType) {
                <dt>{{ 'Entry' | tr }}</dt><dd>{{ '{type} at {time}' | tr: { type: humanize(c.requestedType), time: c.requestedTimestamp | ldate: 'HH:mm' } }}</dd>
              }
              <dt>{{ 'Reason' | tr }}</dt><dd>{{ c.reason }}</dd>
              <dt>{{ 'Requested' | tr }}</dt><dd>{{ c.createdAt | ldate: 'medium' }}</dd>
            </dl>
          </mat-card-content>
        </mat-card>
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>{{ 'Approval' | tr }}</h2></mat-card-title></mat-card-header>
          <mat-card-content><ops-workflow-timeline [history]="c.history" /></mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: `
    .dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; }
    .dl dt { color: var(--mat-sys-on-surface-variant); }
    .dl dd { margin: 0; }
  `,
})
export class CorrectionDetail implements OnInit {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);

  readonly id = input.required<string>();
  protected readonly correction = signal<TimeCorrection | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly humanize = humanize;

  async ngOnInit(): Promise<void> {
    try {
      this.correction.set(await this.api.correction(this.id()));
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  protected async decide(action: 'approve' | 'reject'): Promise<void> {
    const result = await askDecision(this.dialog, {
      title: tr(action === 'approve' ? 'Approve and apply this correction?' : 'Reject this correction?'),
      confirmLabel: tr(action === 'approve' ? 'Approve' : 'Reject'),
      commentRequired: action === 'reject',
      destructive: action === 'reject',
    });
    if (!result) {
      return;
    }
    this.busy.set(true);
    try {
      this.correction.set(await this.api.correctionAction(this.id(), action, result.comment));
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }
}
