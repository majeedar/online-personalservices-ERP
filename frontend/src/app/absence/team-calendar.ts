import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { TeamAbsence } from '../core/api/models';
import { halfDaySuffix, isoDate, parseIsoDate } from '../core/format';
import { StatusChip } from '../shared/status-chip';
import { I18N_PIPES } from '../core/i18n/pipes';

interface Row {
  name: string;
  cells: { date: string; state: 'approved' | 'pending' | 'weekend' | 'free'; half: boolean }[];
}

/**
 * Team calendar for supervisors (AGENT.md §40). Shows who is absent; the leave
 * type is deliberately not shown (it can be health data). Cells carry a letter as
 * well as a colour, so the view does not rely on colour alone.
 */
@Component({
  selector: 'ops-team-calendar',
  imports: [I18N_PIPES, RouterLink, MatButtonModule, MatCardModule, MatIconModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Team calendar' | tr }}</h1>
        <p>{{ 'Absences of the people whose requests you approve, {from} – {to}.' | tr: { from: from() | ldate: 'mediumDate', to: to() | ldate: 'mediumDate' } }}</p>
      </div>
      <div class="actions">
        <button mat-stroked-button type="button" (click)="shift(-28)" [attr.aria-label]="'Previous four weeks' | tr"><mat-icon>chevron_left</mat-icon></button>
        <button mat-stroked-button type="button" (click)="shift(28)" [attr.aria-label]="'Next four weeks' | tr"><mat-icon>chevron_right</mat-icon></button>
      </div>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }

    <mat-card appearance="outlined">
      <mat-card-content>
        <p class="legend">
          <span class="cell approved">{{ 'A' | tr }}</span> {{ 'approved' | tr }}
          <span class="cell pending">{{ 'P' | tr }}</span> {{ 'pending' | tr }}
          <span class="cell weekend"></span> {{ 'weekend' | tr }}
          <span>{{ '½ half day' | tr }}</span>
        </p>
        <div class="table-scroll">
          <table class="calendar">
            <caption class="sr-only">{{ 'Team absences per day' | tr }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ 'Employee' | tr }}</th>
                @for (d of days(); track d) {
                  <th scope="col" [attr.aria-label]="d | ldate: 'fullDate'">{{ d | ldate: 'd' }}<br /><small>{{ d | ldate: 'EEEEE' }}</small></th>
                }
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.name) {
                <tr>
                  <th scope="row">{{ row.name }}</th>
                  @for (c of row.cells; track c.date) {
                    <td class="cell {{ c.state }}" [attr.aria-label]="c.state === 'approved' ? ((c.half ? 'absent half day' : 'absent') | tr) : c.state === 'pending' ? ((c.half ? 'absence pending half day' : 'absence pending') | tr) : null">
                      {{ c.state === 'approved' ? ('A' | tr) : c.state === 'pending' ? ('P' | tr) : '' }}{{ c.half && (c.state === 'approved' || c.state === 'pending') ? '½' : '' }}
                    </td>
                  }
                </tr>
              } @empty {
                <tr><td [attr.colspan]="days().length + 1" class="muted">{{ 'No absences in this period.' | tr }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </mat-card-content>
    </mat-card>

    <mat-card appearance="outlined" class="list">
      <mat-card-content>
        <table class="data">
          <caption>{{ 'Absences in the period' | tr }}</caption>
          <thead><tr><th scope="col">{{ 'Employee' | tr }}</th><th scope="col">{{ 'From' | tr }}</th><th scope="col">{{ 'To' | tr }}</th><th scope="col" class="num">{{ 'Working days' | tr }}</th><th scope="col">{{ 'Status' | tr }}</th></tr></thead>
          <tbody>
            @for (a of absences(); track a.requestId) {
              <tr>
                <td><a [routerLink]="['/absence', a.requestId]">{{ a.employee.displayName }}</a></td>
                <td>{{ a.startDate | ldate: 'mediumDate' }}{{ halfDaySuffix(a.startDayPart) }}</td>
                <td>{{ a.endDate | ldate: 'mediumDate' }}{{ halfDaySuffix(a.endDayPart) }}</td>
                <td class="num">{{ a.workingDays }}</td>
                <td><ops-status [status]="a.status" /></td>
              </tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .calendar { border-collapse: collapse; font-size: 12px; }
    .calendar th, .calendar td { border: 1px solid var(--mat-sys-outline-variant); padding: 2px 4px; text-align: center; min-width: 22px; }
    .calendar tbody th { text-align: left; white-space: nowrap; font-weight: 400; padding-right: 12px; }
    .cell.approved { background: var(--ops-status-approved); color: #fff; font-weight: 600; }
    .cell.pending { background: #f3d58a; color: #3d2a00; font-weight: 600; }
    .cell.weekend { background: var(--mat-sys-surface-container-high); }
    .legend { display: flex; gap: 8px; align-items: center; }
    .legend .cell { display: inline-block; width: 20px; height: 20px; text-align: center; border-radius: 3px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    .list { margin-top: 16px; }
  `,
})
export class TeamCalendar {
  private readonly api = inject(Api);

  protected readonly from = signal(new Date());
  protected readonly to = computed(() => new Date(this.from().getTime() + 27 * 86_400_000));
  protected readonly absences = signal<TeamAbsence[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly halfDaySuffix = halfDaySuffix;

  protected readonly days = computed(() => {
    const result: string[] = [];
    for (let i = 0; i < 28; i++) {
      result.push(isoDate(new Date(this.from().getFullYear(), this.from().getMonth(), this.from().getDate() + i)));
    }
    return result;
  });

  protected readonly rows = computed<Row[]>(() => {
    const byName = new Map<string, TeamAbsence[]>();
    this.absences().forEach((a) => byName.set(a.employee.displayName, [...(byName.get(a.employee.displayName) ?? []), a]));
    return [...byName.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, list]) => ({
        name,
        cells: this.days().map((date) => {
          const weekday = parseIsoDate(date).getDay();
          const hit = list.find((a) => a.startDate <= date && a.endDate >= date);
          // Weekends stay weekends: the calendar shows missing working days, not calendar spans.
          const state = weekday === 0 || weekday === 6 ? 'weekend'
            : hit ? (hit.status === 'APPROVED' || hit.status === 'CANCEL_REQUESTED' ? 'approved' : 'pending')
            : 'free';
          const half = !!hit && ((hit.startDate === date && hit.startDayPart !== 'FULL')
            || (hit.endDate === date && hit.endDayPart !== 'FULL'));
          return { date, state, half };
        }),
      }));
  });

  constructor() {
    void this.load();
  }

  protected shift(days: number): void {
    this.from.set(new Date(this.from().getTime() + days * 86_400_000));
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.absences.set(await this.api.teamAbsences(isoDate(this.from()), isoDate(this.to())));
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
