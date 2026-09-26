import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { AbsenceSummary, LeaveBalance } from '../core/api/models';
import { formatDays, halfDaySuffix } from '../core/format';
import { StatusChip } from '../shared/status-chip';

@Component({
  selector: 'ops-absence-list',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>Absence</h1>
        <p>Leave requests and your balance for {{ year }}.</p>
      </div>
      <a mat-flat-button routerLink="/absence/new"><mat-icon>add</mat-icon> New request</a>
    </header>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }

    <section class="cards" aria-label="Leave balance">
      @for (b of balances(); track b.leaveTypeCode) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-icon mat-card-avatar aria-hidden="true">beach_access</mat-icon>
            <mat-card-title>{{ b.leaveTypeName }} {{ b.year }}</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="figure">{{ formatDays(b.remainingDays) }}</p>
            <p class="muted">remaining of {{ b.baseDays + b.carryOverDays + b.additionalDays }}</p>
            <p>Used {{ b.usedDays }} · Pending {{ b.reservedDays }}</p>
            @if (b.carryOverDays > 0) {
              <p class="muted">Carry-over {{ b.carryOverDays }} (expires {{ b.carryOverExpiry | date: 'mediumDate' }})</p>
            }
          </mat-card-content>
        </mat-card>
      }
    </section>

    <mat-card appearance="outlined">
      <mat-card-content>
        @if (requests().length === 0 && !loading()) {
          <p class="muted">You have no absence requests yet.</p>
        } @else {
          <div class="table-scroll">
            <table class="data">
              <caption>Your absence requests</caption>
              <thead>
                <tr>
                  <th scope="col">Type</th>
                  <th scope="col">From</th>
                  <th scope="col">To</th>
                  <th scope="col" class="num">Working days</th>
                  <th scope="col" class="num">Deducted</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                @for (r of requests(); track r.id) {
                  <tr>
                    <td><a [routerLink]="['/absence', r.id]">{{ r.leaveType.name }}</a></td>
                    <td>{{ r.startDate | date: 'mediumDate' }}{{ halfDaySuffix(r.startDayPart) }}</td>
                    <td>{{ r.endDate | date: 'mediumDate' }}{{ halfDaySuffix(r.endDayPart) }}</td>
                    <td class="num">{{ r.workingDays }}</td>
                    <td class="num">{{ r.deduction }}</td>
                    <td><ops-status [status]="r.status" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
})
export class AbsenceList {
  private readonly api = inject(Api);

  protected readonly year = new Date().getFullYear();
  protected readonly requests = signal<AbsenceSummary[]>([]);
  protected readonly balances = signal<LeaveBalance[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly formatDays = formatDays;
  protected readonly halfDaySuffix = halfDaySuffix;

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const [requests, balances] = await Promise.all([this.api.absences(), this.api.leaveBalances()]);
      this.requests.set(requests);
      this.balances.set(balances);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.loading.set(false);
    }
  }
}
