import { Component, inject, OnDestroy, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { EntryType, TimeToday as Today } from '../core/api/models';
import { formatBalance, formatMinutes, humanize } from '../core/format';
import { tr, marker } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

const ACTIONS: { type: EntryType; label: string; icon: string }[] = [
  { type: 'CLOCK_IN', label: marker('Clock in'), icon: 'login' },
  { type: 'BREAK_START', label: marker('Start break'), icon: 'coffee' },
  { type: 'BREAK_END', label: marker('End break'), icon: 'work' },
  { type: 'CLOCK_OUT', label: marker('Clock out'), icon: 'logout' },
];

/**
 * Current-day time clock (AGENT.md §44). Buttons for invalid transitions are
 * disabled here, but the server enforces the same sequence rules.
 */
@Component({
  selector: 'ops-time-today',
  imports: [I18N_PIPES, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Working time' | tr }}</h1>
        <p>{{ today()?.date | ldate: 'fullDate' }}</p>
      </div>
      <a mat-stroked-button routerLink="/time/month"><mat-icon>calendar_month</mat-icon> {{ 'Monthly overview' | tr }}</a>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }

    <div class="grid-2">
      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title><h2>{{ 'Time clock' | tr }}</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          <p class="state" aria-live="polite">
            {{ 'Status:' | tr }} <strong>{{ stateLabel() }}</strong>
          </p>
          <div class="clock-buttons">
            @for (a of actions; track a.type) {
              <button
                mat-flat-button
                type="button"
                [disabled]="busy() || !allowed(a.type)"
                (click)="clock(a.type)"
              >
                <mat-icon>{{ a.icon }}</mat-icon> {{ a.label | tr }}
              </button>
            }
          </div>
          <h3>{{ "Today's entries" | tr }}</h3>
          <ol class="entries">
            @for (e of today()?.entries ?? []; track e.id) {
              <li><time>{{ e.timestamp | ldate: 'HH:mm' }}</time> {{ humanize(e.type) }} <span class="muted">({{ humanize(e.source) }})</span></li>
            } @empty {
              <li class="muted">{{ 'No entries yet.' | tr }}</li>
            }
          </ol>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title><h2>{{ "Today's account" | tr }}</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          @if (today()?.account; as a) {
            <dl class="dl">
              <dt>{{ 'Target' | tr }}</dt><dd>{{ formatMinutes(a.targetMinutes) }}</dd>
              <dt>{{ 'Worked' | tr }}</dt><dd>{{ formatMinutes(a.workedMinutes) }}</dd>
              <dt>{{ 'Breaks' | tr }}</dt><dd>{{ formatMinutes(a.breakMinutes) }}</dd>
              @if (a.absenceMinutes) {
                <dt>{{ 'Absence' | tr }} ({{ humanize(a.absenceType) }})</dt><dd>{{ formatMinutes(a.absenceMinutes) }}</dd>
              }
              <dt>{{ 'Balance' | tr }}</dt><dd [class.negative]="a.balanceMinutes < 0"><strong>{{ formatBalance(a.balanceMinutes) }}</strong></dd>
            </dl>
            @if (a.statutoryBreakApplied) {
              <p class="info-banner"><mat-icon aria-hidden="true">info</mat-icon> {{ 'The statutory minimum break was deducted.' | tr }}</p>
            }
          }
          <h3>{{ 'Balance' | tr }}</h3>
          <p>{{ 'This month:' | tr }} <strong>{{ formatBalance(balance().month) }}</strong> {{ '· This year:' | tr }} <strong>{{ formatBalance(balance().year) }}</strong></p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .clock-buttons { display: flex; gap: 8px; flex-wrap: wrap; margin: 12px 0 16px; }
    .entries { padding-left: 20px; }
    .entries time { font-variant-numeric: tabular-nums; font-weight: 500; margin-right: 8px; }
    .dl { display: grid; grid-template-columns: 1fr auto; gap: 6px 16px; }
    .dl dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
    h3 { font: var(--mat-sys-title-small); margin: 16px 0 4px; }
  `,
})
export class TimeToday implements OnDestroy {
  private readonly api = inject(Api);

  protected readonly actions = ACTIONS;
  protected readonly today = signal<Today | null>(null);
  protected readonly balance = signal({ month: 0, year: 0 });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formatMinutes = formatMinutes;
  protected readonly formatBalance = formatBalance;
  protected readonly humanize = humanize;
  private readonly refresh = setInterval(() => void this.load(), 60_000);

  constructor() {
    void this.load();
  }

  ngOnDestroy(): void {
    clearInterval(this.refresh);
  }

  protected allowed(type: EntryType): boolean {
    return this.today()?.allowedActions.includes(type) ?? false;
  }

  protected stateLabel(): string {
    const state = this.today()?.state;
    return tr(state === 'WORKING' ? 'Working' : state === 'ON_BREAK' ? 'On break' : 'Not clocked in');
  }

  private async load(): Promise<void> {
    try {
      const [today, balance] = await Promise.all([this.api.timeToday(), this.api.timeBalance()]);
      this.today.set(today);
      this.balance.set({ month: balance.monthBalanceMinutes, year: balance.yearBalanceMinutes });
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  protected async clock(type: EntryType): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.today.set(await this.api.clock(type));
      const balance = await this.api.timeBalance();
      this.balance.set({ month: balance.monthBalanceMinutes, year: balance.yearBalanceMinutes });
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }
}
