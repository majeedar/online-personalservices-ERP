import { KeyValuePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import {
  AbsenceSummary,
  AppNotification,
  AuditEntry,
  LeaveBalance,
  Me,
  SystemHealth,
  TeamAbsence,
  TravelSummary,
} from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { formatBalance, formatDays, humanize, isoDate } from '../core/format';
import { EmployeeApi } from '../profile/employee-api.service';
import { StatusChip } from '../shared/status-chip';
import { tr } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

/** Role-aware dashboard (AGENT.md §12, §41). Each block loads independently. */
@Component({
  selector: 'ops-dashboard',
  imports: [I18N_PIPES, KeyValuePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(Api);
  private readonly employeeApi = inject(EmployeeApi);
  private readonly auth = inject(AuthService);

  protected readonly me = signal<Me | null>(null);
  protected readonly balance = signal<LeaveBalance | null>(null);
  protected readonly timeBalance = signal<{ month: number; year: number } | null>(null);
  protected readonly absences = signal<AbsenceSummary[]>([]);
  protected readonly trips = signal<TravelSummary[]>([]);
  protected readonly taskCount = signal<number | null>(null);
  protected readonly notifications = signal<AppNotification[]>([]);
  protected readonly team = signal<TeamAbsence[]>([]);
  protected readonly health = signal<SystemHealth | null>(null);
  protected readonly audit = signal<AuditEntry[]>([]);
  protected readonly pendingApprovals = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(true);

  protected readonly isEmployee = computed(() => this.auth.hasAnyRole('EMPLOYEE'));
  protected readonly isSupervisor = computed(() => this.auth.hasAnyRole('SUPERVISOR'));
  protected readonly isHr = computed(() => this.auth.hasAnyRole('HR_ADMIN'));
  protected readonly isOperator = computed(() => this.auth.hasAnyRole('ERP_ADMIN', 'SUPPORT'));

  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    return tr(hour < 11 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening');
  });
  protected readonly openRequests = computed(
    () =>
      this.absences().filter((a) => a.status === 'IN_APPROVAL' || a.status === 'CANCEL_REQUESTED').length +
      this.trips().filter((t) => t.status === 'IN_APPROVAL' || t.status === 'EXPENSES_SUBMITTED').length,
  );
  protected readonly upcoming = computed(() => {
    const today = isoDate(new Date());
    return this.absences()
      .filter((a) => a.status === 'APPROVED' && a.endDate >= today)
      .sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
  });

  protected readonly formatDays = formatDays;
  protected readonly formatBalance = formatBalance;
  protected readonly humanize = humanize;

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    const safe = async <T>(call: () => Promise<T>, set: (v: T) => void) => {
      try {
        set(await call());
      } catch (e) {
        this.error.set(describeError(e));
      }
    };
    const blocks: Promise<void>[] = [
      safe(() => new Promise<Me>((resolve, reject) => this.employeeApi.me().subscribe({ next: resolve, error: reject })), (m) => this.me.set(m)),
      safe(() => this.api.tasks(), (t) => this.taskCount.set(t.length)),
      safe(() => this.api.notifications(5), (n) => this.notifications.set(n)),
    ];
    if (this.isEmployee()) {
      blocks.push(
        safe(() => this.api.leaveBalances(), (b) => this.balance.set(b.find((x) => x.leaveTypeCode === 'ANNUAL_LEAVE') ?? null)),
        safe(() => this.api.timeBalance(), (b) => this.timeBalance.set({ month: b.monthBalanceMinutes, year: b.yearBalanceMinutes })),
        safe(() => this.api.absences(), (a) => this.absences.set(a)),
        safe(() => this.api.trips(), (t) => this.trips.set(t)),
      );
    }
    if (this.isSupervisor()) {
      const from = new Date();
      const to = new Date(from.getTime() + 14 * 86_400_000);
      blocks.push(safe(() => this.api.teamAbsences(isoDate(from), isoDate(to)), (t) => this.team.set(t)));
    }
    if (this.isHr()) {
      blocks.push(safe(() => this.api.report('pending-approvals', {}), (r) => this.pendingApprovals.set(r.rows.length)));
    }
    if (this.isOperator()) {
      blocks.push(
        safe(() => this.api.systemHealth(), (h) => this.health.set(h)),
        safe(() => this.api.audit(undefined, undefined, 0), (a) => this.audit.set(a.items.slice(0, 6))),
      );
    }
    await Promise.all(blocks);
    this.loading.set(false);
  }
}
