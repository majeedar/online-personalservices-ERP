import { DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { AbsenceDetail as Detail } from '../core/api/models';
import { formatMinutes, halfDaySuffix, humanize } from '../core/format';
import { askDecision } from '../shared/decision-dialog';
import { StatusChip } from '../shared/status-chip';
import { WorkflowTimeline } from '../shared/workflow-timeline';

/** Request details, days, approval timeline and actions (AGENT.md §42 "request details", "approval page"). */
@Component({
  selector: 'ops-absence-detail',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip, WorkflowTimeline],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }
    @if (request(); as r) {
      <header class="page-header">
        <div>
          <h1>{{ r.leaveType.name }} · {{ r.startDate | date: 'mediumDate' }}{{ halfDaySuffix(r.startDayPart) }}@if (r.endDate !== r.startDate) { – {{ r.endDate | date: 'mediumDate' }}{{ halfDaySuffix(r.endDayPart) }}}</h1>
          <p>{{ r.employee.displayName }} · <ops-status [status]="r.status" /></p>
        </div>
        <div class="actions">
          @if (r.actions.decide) {
            <button mat-flat-button type="button" (click)="decide('approve')" [disabled]="busy()">
              <mat-icon>check</mat-icon> Approve
            </button>
            <button mat-stroked-button type="button" (click)="decide('return')" [disabled]="busy()">Return</button>
            <button mat-stroked-button type="button" (click)="decide('reject')" [disabled]="busy()">Reject</button>
          }
          @if (r.actions.edit) {
            <a mat-stroked-button [routerLink]="['/absence', r.id, 'edit']"><mat-icon>edit</mat-icon> Edit</a>
          }
          @if (r.actions.submit) {
            <button mat-flat-button type="button" (click)="act('submit')" [disabled]="busy()">Submit</button>
          }
          @if (r.actions.cancel) {
            <button mat-button type="button" (click)="cancel()" [disabled]="busy()">
              {{ r.status === 'APPROVED' ? 'Request cancellation' : 'Cancel request' }}
            </button>
          }
        </div>
      </header>

      @if (r.anonymisedAt) {
        <p class="info-banner"><mat-icon aria-hidden="true">auto_delete</mat-icon> Comments, representative and attachments were removed on {{ r.anonymisedAt | date: 'mediumDate' }} after the retention period.</p>
      }
      @if (r.actions.decideAsDelegate) {
        <p class="info-banner"><mat-icon aria-hidden="true">swap_horiz</mat-icon> You decide as a delegate; your decision is recorded on behalf of the assigned approver.</p>
      }

      <div class="grid-2">
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>Details</h2></mat-card-title></mat-card-header>
          <mat-card-content>
            <dl class="dl">
              <dt>Working days</dt><dd>{{ r.workingDays }}</dd>
              <dt>Deducted from entitlement</dt><dd>{{ r.deduction }} day(s)</dd>
              <dt>Representative</dt><dd>{{ r.representative?.displayName ?? '—' }}</dd>
              <dt>Comment</dt><dd>{{ r.comment || '—' }}</dd>
              <dt>Submitted</dt><dd>{{ r.submittedAt ? (r.submittedAt | date: 'medium') : '—' }}</dd>
            </dl>
            <h3>Attachments</h3>
            @for (d of r.documents; track d.id) {
              <p><a [href]="'/api/v1/absences/' + r.id + '/documents/' + d.id">{{ d.fileName }}</a></p>
            } @empty {
              <p class="muted">No attachments.</p>
            }
            @if (r.actions.edit) {
              <label class="upload">
                <mat-icon aria-hidden="true">attach_file</mat-icon> Attach PDF/image
                <input type="file" accept="application/pdf,image/png,image/jpeg" (change)="upload($event)" />
              </label>
            }
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>Approval</h2></mat-card-title></mat-card-header>
          <mat-card-content><ops-workflow-timeline [history]="r.history" /></mat-card-content>
        </mat-card>
      </div>

      <mat-card appearance="outlined">
        <mat-card-content>
          <table class="data">
            <caption>Calculated days</caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Counts as</th>
                <th scope="col" class="num">Planned</th>
                <th scope="col" class="num">Credited</th>
                <th scope="col" class="num">Deducted</th>
              </tr>
            </thead>
            <tbody>
              @for (d of r.days; track d.date) {
                <tr [class.off]="d.kind !== 'WORKING_DAY'">
                  <th scope="row">{{ d.date | date: 'EEE, d MMM y' }}</th>
                  <td>{{ humanize(d.kind) }}{{ halfDaySuffix(d.dayPart) }}</td>
                  <td class="num">{{ d.plannedMinutes ? formatMinutes(d.plannedMinutes) : '—' }}</td>
                  <td class="num">{{ d.creditedMinutes ? formatMinutes(d.creditedMinutes) : '—' }}</td>
                  <td class="num">{{ d.entitlementDeduction || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: `
    .dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; }
    .dl dt { color: var(--mat-sys-on-surface-variant); }
    .dl dd { margin: 0; }
    h3 { font: var(--mat-sys-title-small); margin: 16px 0 4px; }
    .upload { display: inline-flex; gap: 6px; align-items: center; cursor: pointer; color: var(--mat-sys-primary); }
    .upload input { margin-left: 8px; }
  `,
})
export class AbsenceDetail implements OnInit {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);

  readonly id = input.required<string>();

  protected readonly request = signal<Detail | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formatMinutes = formatMinutes;
  protected readonly humanize = humanize;
  protected readonly halfDaySuffix = halfDaySuffix;

  ngOnInit(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.request.set(await this.api.absence(this.id()));
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected async act(action: 'submit' | 'approve' | 'reject' | 'return' | 'cancel', comment?: string) {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.request.set(await this.api.absenceAction(this.id(), action, comment));
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }

  protected async decide(action: 'approve' | 'reject' | 'return'): Promise<void> {
    const labels = { approve: 'Approve', reject: 'Reject', return: 'Return for correction' };
    const result = await askDecision(this.dialog, {
      title: `${labels[action]} this request?`,
      confirmLabel: labels[action],
      commentRequired: action !== 'approve',
      destructive: action === 'reject',
    });
    if (result) {
      await this.act(action, result.comment);
    }
  }

  protected async cancel(): Promise<void> {
    const approved = this.request()?.status === 'APPROVED';
    const result = await askDecision(this.dialog, {
      title: approved ? 'Request cancellation?' : 'Cancel this request?',
      message: approved ? 'Your approver has to confirm the cancellation of an approved absence.' : undefined,
      confirmLabel: approved ? 'Request cancellation' : 'Cancel request',
      commentRequired: false,
      destructive: true,
    });
    if (result) {
      await this.act('cancel', result.comment);
    }
  }

  protected async upload(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) {
      return;
    }
    try {
      await this.api.uploadAbsenceDocument(this.id(), file);
      await this.load();
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
