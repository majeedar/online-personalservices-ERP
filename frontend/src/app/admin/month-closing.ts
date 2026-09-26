import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { MonthClosingStatus } from '../core/api/models';
import { parseIsoDate } from '../core/format';
import { askDecision } from '../shared/decision-dialog';
import { StatusChip } from '../shared/status-chip';

/**
 * Monthly closing of time accounts (TIME_ADMIN, HR_ADMIN). A closed month's
 * daily accounts are frozen; corrections for it are rejected until it is reopened.
 */
@Component({
  selector: 'ops-month-closing',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>Month closing</h1>
        <p>Closing freezes all time accounts of a past month. Pending corrections must be decided first.</p>
      </div>
    </header>
    @if (busy()) {
      <mat-progress-bar mode="indeterminate" aria-label="Working" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }
    @if (message(); as m) {
      <p class="info-banner" role="status">{{ m }}</p>
    }
    <mat-card appearance="outlined">
      <mat-card-content class="table-scroll">
        <table class="data">
          <caption>Last 12 months</caption>
          <thead>
            <tr>
              <th scope="col">Month</th>
              <th scope="col">Status</th>
              <th scope="col">Closed</th>
              <th scope="col" class="num">Pending corrections</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (m of months(); track m.month) {
              <tr>
                <th scope="row">{{ asDate(m.month) | date: 'MMMM y' }}</th>
                <td>
                  <ops-status [status]="m.closed ? 'COMPLETED' : 'OPEN'" [label]="m.closed ? 'Closed' : 'Open'" />
                </td>
                <td>
                  @if (m.closed) {
                    {{ m.closedAt | date: 'medium' }} by {{ m.closedBy }} · {{ m.employees }} employees
                  } @else {
                    <span class="muted">{{ m.blockedReason }}</span>
                  }
                </td>
                <td class="num" [class.negative]="m.pendingCorrections > 0">{{ m.pendingCorrections }}</td>
                <td>
                  @if (m.closed) {
                    <button mat-stroked-button type="button" (click)="reopen(m)" [disabled]="busy()">Reopen</button>
                  } @else if (m.closable) {
                    <button mat-flat-button type="button" (click)="close(m)" [disabled]="busy()">
                      <mat-icon>lock</mat-icon> Close month
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
})
export class MonthClosing {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);

  protected readonly months = signal<MonthClosingStatus[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected asDate(month: string): Date {
    return parseIsoDate(`${month}-01`);
  }

  protected async close(m: MonthClosingStatus): Promise<void> {
    const result = await askDecision(this.dialog, {
      title: `Close ${m.month}?`,
      message: 'All time accounts of this month are recalculated one last time and frozen.',
      confirmLabel: 'Close month',
      commentRequired: false,
    });
    if (result) {
      await this.run(() => this.api.closeMonth(m.month), `${m.month} closed.`);
    }
  }

  protected async reopen(m: MonthClosingStatus): Promise<void> {
    const result = await askDecision(this.dialog, {
      title: `Reopen ${m.month}?`,
      message: 'Corrections become possible again and the accounts are recalculated. The reopening is audited.',
      confirmLabel: 'Reopen',
      commentRequired: true,
      destructive: true,
    });
    if (result) {
      await this.run(() => this.api.reopenMonth(m.month, result.comment ?? ''), `${m.month} reopened.`);
    }
  }

  private async run(action: () => Promise<unknown>, success: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    try {
      await action();
      this.message.set(success);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
    await this.load();
  }

  private async load(): Promise<void> {
    try {
      this.months.set(await this.api.monthClosings());
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
