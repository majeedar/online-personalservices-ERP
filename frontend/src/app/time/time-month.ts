import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { TimeCorrection, TimeMonth as Month } from '../core/api/models';
import { formatBalance, formatMinutes, humanize } from '../core/format';
import { StatusChip } from '../shared/status-chip';
import { CorrectionDialog } from './correction-dialog';
import { I18N_PIPES } from '../core/i18n/pipes';

/** Monthly overview with daily balances and corrections (AGENT.md §44). */
@Component({
  selector: 'ops-time-month',
  imports: [I18N_PIPES, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Monthly overview' | tr }}</h1>
        <p>{{ monthStart() | ldate: 'MMMM y' }}</p>
      </div>
      <div class="actions">
        <button mat-stroked-button type="button" (click)="shift(-1)" [attr.aria-label]="'Previous month' | tr"><mat-icon>chevron_left</mat-icon></button>
        <button mat-stroked-button type="button" (click)="shift(1)" [attr.aria-label]="'Next month' | tr"><mat-icon>chevron_right</mat-icon></button>
        <a mat-stroked-button routerLink="/time"><mat-icon>schedule</mat-icon> {{ 'Today' | tr }}</a>
      </div>
    </header>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" [attr.aria-label]="'Loading' | tr" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    @if (month(); as m) {
      @if (closed()) {
        <p class="info-banner" role="status"><mat-icon aria-hidden="true">lock</mat-icon> {{ 'This month is closed. Its accounts are final; ask a time administrator if something must still be corrected.' | tr }}</p>
      }
      <section class="cards" [attr.aria-label]="'Month totals' | tr">
        <div class="total"><span class="muted">{{ 'Target' | tr }}</span><strong>{{ formatMinutes(m.totals.targetMinutes) }}</strong></div>
        <div class="total"><span class="muted">{{ 'Worked' | tr }}</span><strong>{{ formatMinutes(m.totals.workedMinutes) }}</strong></div>
        <div class="total"><span class="muted">{{ 'Credited' | tr }}</span><strong>{{ formatMinutes(m.totals.creditedMinutes) }}</strong></div>
        <div class="total"><span class="muted">{{ 'Balance' | tr }}</span><strong [class.negative]="m.totals.balanceMinutes < 0">{{ formatBalance(m.totals.balanceMinutes) }}</strong></div>
      </section>
      <mat-card appearance="outlined">
        <mat-card-content class="table-scroll">
          <table class="data">
            <caption>{{ 'Daily accounts' | tr }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ 'Day' | tr }}</th>
                <th scope="col" class="num">{{ 'Target' | tr }}</th>
                <th scope="col" class="num">{{ 'Worked' | tr }}</th>
                <th scope="col" class="num">{{ 'Breaks' | tr }}</th>
                <th scope="col">{{ 'Absence / holiday' | tr }}</th>
                <th scope="col" class="num">{{ 'Balance' | tr }}</th>
                <th scope="col">{{ 'Status' | tr }}</th>
                <th scope="col"><span class="sr-only">{{ 'Actions' | tr }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (d of m.days; track d.date) {
                <tr [class.off]="d.future || (d.targetMinutes === 0 && !d.workedMinutes)">
                  <th scope="row">{{ d.date | ldate: 'EEE d' }}</th>
                  <td class="num">{{ d.targetMinutes ? formatMinutes(d.targetMinutes) : '—' }}</td>
                  <td class="num">{{ d.workedMinutes ? formatMinutes(d.workedMinutes) : '—' }}</td>
                  <td class="num">{{ d.breakMinutes ? formatMinutes(d.breakMinutes) : '—' }}</td>
                  <td>{{ d.holidayName ?? (d.absenceType ? humanize(d.absenceType) : '') }}</td>
                  <td class="num" [class.negative]="d.balanceMinutes < 0">{{ d.accounted ? formatBalance(d.balanceMinutes) : '' }}</td>
                  <td>
                    @if (d.incomplete && !d.future) {
                      <span class="status status-failed">{{ 'Missing entry' | tr }}</span>
                    } @else if (!d.future) {
                      <ops-status [status]="d.status" />
                    }
                  </td>
                  <td>
                    @if (!d.future && d.status !== 'CORRECTION_PENDING' && d.status !== 'CLOSED') {
                      <button mat-button type="button" (click)="correct(d.date)">{{ 'Correct' | tr }}</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </mat-card-content>
      </mat-card>
    }

    <mat-card appearance="outlined" class="corrections">
      <mat-card-header><mat-card-title><h2>{{ 'My correction requests' | tr }}</h2></mat-card-title></mat-card-header>
      <mat-card-content>
        <table class="data">
          <thead><tr><th scope="col">{{ 'Day' | tr }}</th><th scope="col">{{ 'Correction' | tr }}</th><th scope="col">{{ 'Reason' | tr }}</th><th scope="col">{{ 'Status' | tr }}</th></tr></thead>
          <tbody>
            @for (c of corrections(); track c.id) {
              <tr>
                <td><a [routerLink]="['/time/corrections', c.id]">{{ c.date | ldate: 'mediumDate' }}</a></td>
                <td>{{ humanize(c.operation) }} {{ c.requestedType ? humanize(c.requestedType) : '' }} {{ c.requestedTimestamp ? (c.requestedTimestamp | ldate: 'HH:mm') : '' }}</td>
                <td>{{ c.reason }}</td>
                <td><ops-status [status]="c.status" /></td>
              </tr>
            } @empty {
              <tr><td colspan="4" class="muted">{{ 'None.' | tr }}</td></tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .total { display: flex; flex-direction: column; padding: 12px 16px; border: 1px solid var(--mat-sys-outline-variant); border-radius: 12px; }
    .total strong { font: var(--mat-sys-title-large); font-variant-numeric: tabular-nums; }
    .corrections { margin-top: 16px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
})
export class TimeMonth {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);

  protected readonly monthStart = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  protected readonly month = signal<Month | null>(null);
  protected readonly corrections = signal<TimeCorrection[]>([]);
  protected readonly closed = computed(() => this.month()?.days.some((d) => d.status === 'CLOSED') ?? false);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly formatMinutes = formatMinutes;
  protected readonly formatBalance = formatBalance;
  protected readonly humanize = humanize;

  constructor() {
    void this.load();
  }

  protected shift(months: number): void {
    const d = this.monthStart();
    this.monthStart.set(new Date(d.getFullYear(), d.getMonth() + months, 1));
    void this.load();
  }

  protected async correct(date: string): Promise<void> {
    const ref = this.dialog.open(CorrectionDialog, { data: { date }, width: '560px' });
    if (await firstValueFrom(ref.afterClosed())) {
      await this.load();
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const d = this.monthStart();
      const [month, corrections] = await Promise.all([
        this.api.timeMonth(d.getFullYear(), d.getMonth() + 1),
        this.api.corrections(),
      ]);
      this.month.set(month);
      this.corrections.set(corrections);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.loading.set(false);
    }
  }
}
