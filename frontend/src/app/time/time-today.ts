import { DatePipe } from '@angular/common';
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

const ACTIONS: { type: EntryType; label: string; icon: string }[] = [
  { type: 'CLOCK_IN', label: 'Clock in', icon: 'login' },
  { type: 'BREAK_START', label: 'Start break', icon: 'coffee' },
  { type: 'BREAK_END', label: 'End break', icon: 'work' },
  { type: 'CLOCK_OUT', label: 'Clock out', icon: 'logout' },
];

/**
 * Current-day time clock (AGENT.md §44). Buttons for invalid transitions are
 * disabled here, but the server enforces the same sequence rules.
 */
@Component({
  selector: 'ops-time-today',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  template: `
    <header class="page-header">
      <div>
        <h1>Working time</h1>
        <p>{{ today()?.date | date: 'fullDate' }}</p>
      </div>
      <a mat-stroked-button routerLink="/time/month"><mat-icon>calendar_month</mat-icon> Monthly overview</a>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }

    <div class="grid-2">
      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title><h2>Time clock</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          <p class="state" aria-live="polite">
            Status: <strong>{{ stateLabel() }}</strong>
          </p>
          <div class="clock-buttons">
            @for (a of actions; track a.type) {
              <button
                mat-flat-button
                type="button"
                [disabled]="busy() || !allowed(a.type)"
                (click)="clock(a.type)"
              >
                <mat-icon>{{ a.icon }}</mat-icon> {{ a.label }}
              </button>
            }
          </div>
          <h3>Today's entries</h3>
          <ol class="entries">
            @for (e of today()?.entries ?? []; track e.id) {
              <li><time>{{ e.timestamp | date: 'HH:mm' }}</time> {{ humanize(e.type) }} <span class="muted">({{ humanize(e.source) }})</span></li>
            } @empty {
              <li class="muted">No entries yet.</li>
            }
          </ol>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title><h2>Today's account</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          @if (today()?.account; as a) {
            <dl class="dl">
              <dt>Target</dt><dd>{{ formatMinutes(a.targetMinutes) }}</dd>
              <dt>Worked</dt><dd>{{ formatMinutes(a.workedMinutes) }}</dd>
              <dt>Breaks</dt><dd>{{ formatMinutes(a.breakMinutes) }}</dd>
              @if (a.absenceMinutes) {
                <dt>Absence ({{ humanize(a.absenceType) }})</dt><dd>{{ formatMinutes(a.absenceMinutes) }}</dd>
              }
              <dt>Balance</dt><dd [class.negative]="a.balanceMinutes < 0"><strong>{{ formatBalance(a.balanceMinutes) }}</strong></dd>
            </dl>
            @if (a.statutoryBreakApplied) {
              <p class="info-banner"><mat-icon aria-hidden="true">info</mat-icon> The statutory minimum break was deducted.</p>
            }
          }
          <h3>Balance</h3>
          <p>This month: <strong>{{ formatBalance(balance().month) }}</strong> · This year: <strong>{{ formatBalance(balance().year) }}</strong></p>
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
    return state === 'WORKING' ? 'Working' : state === 'ON_BREAK' ? 'On break' : 'Not clocked in';
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
