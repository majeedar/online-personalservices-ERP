import { KeyValuePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { IntegrationErrorInfo, IntegrationRunInfo, SystemHealth } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { humanize } from '../core/format';
import { StatusChip } from '../shared/status-chip';
import { I18N_PIPES } from '../core/i18n/pipes';

const SYSTEMS: Record<string, string> = {
  'Personnel ERP': 'PERSONNEL_ERP',
  'Finance ERP': 'FINANCE_ERP',
  'Travel ERP': 'TRAVEL_ERP',
};

/** Integration runs, errors with retry, and system health (AGENT.md §26, §63, §83). */
@Component({
  selector: 'ops-integration-monitor',
  imports: [I18N_PIPES, KeyValuePipe, MatButtonModule, MatCardModule, MatIconModule, MatSlideToggleModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Integration monitor' | tr }}</h1>
        <p>{{ 'Exports to the university\'s systems are retried automatically; failures stay here until resolved.' | tr }}</p>
      </div>
      <button mat-stroked-button type="button" (click)="load()"><mat-icon>refresh</mat-icon> {{ 'Refresh' | tr }}</button>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }

    @if (health(); as h) {
      <section class="cards" [attr.aria-label]="'System health' | tr">
        @for (c of h.components | keyvalue; track c.key) {
          <div class="tile">
            <span class="muted">{{ c.key }}</span>
            <ops-status [status]="c.value" />
            @if (isAdmin() && systemCode(c.key); as code) {
              <mat-slide-toggle [checked]="c.value === 'DOWN'" (change)="outage(code, $event.checked)">{{ 'Simulate outage' | tr }}</mat-slide-toggle>
            }
          </div>
        }
        <div class="tile"><span class="muted">{{ 'Pending exports' | tr }}</span><strong>{{ h.pendingExports }}</strong></div>
        <div class="tile"><span class="muted">{{ 'Open integration errors' | tr }}</span><strong [class.negative]="h.openIntegrationErrors > 0">{{ h.openIntegrationErrors }}</strong></div>
        <div class="tile"><span class="muted">{{ 'Failed batch runs (24 h)' | tr }}</span><strong [class.negative]="h.failedBatchRunsLast24h > 0">{{ h.failedBatchRunsLast24h }}</strong></div>
        <div class="tile"><span class="muted">{{ 'Open approval tasks' | tr }}</span><strong>{{ h.openWorkflowTasks }}</strong></div>
      </section>
    }

    <mat-card appearance="outlined">
      <mat-card-header><mat-card-title><h2>{{ 'Open errors' | tr }}</h2></mat-card-title></mat-card-header>
      <mat-card-content class="table-scroll">
        <table class="data">
          <thead><tr><th scope="col">{{ 'Time' | tr }}</th><th scope="col">{{ 'Reference' | tr }}</th><th scope="col">{{ 'Error' | tr }}</th><th scope="col" class="num">{{ 'Retries' | tr }}</th><th scope="col"></th></tr></thead>
          <tbody>
            @for (e of errors(); track e.id) {
              <tr>
                <td>{{ e.createdAt | ldate: 'medium' }}</td>
                <td>{{ e.externalReference ?? '—' }}</td>
                <td><strong>{{ e.errorCode }}</strong><div class="muted">{{ e.errorMessage }}</div></td>
                <td class="num">{{ e.retryCount }}</td>
                <td class="actions">
                  @if (e.retryable && canRetry()) {
                    <button mat-flat-button type="button" (click)="retry(e)"><mat-icon>replay</mat-icon> {{ 'Retry' | tr }}</button>
                  }
                  @if (isAdmin()) {
                    <button mat-button type="button" (click)="resolve(e)">{{ 'Mark resolved' | tr }}</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="muted">{{ 'No open errors.' | tr }}</td></tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>

    <mat-card appearance="outlined" class="runs">
      <mat-card-header><mat-card-title><h2>{{ 'Integration runs' | tr }}</h2></mat-card-title></mat-card-header>
      <mat-card-content class="table-scroll">
        <table class="data">
          <thead><tr><th scope="col">{{ 'Started' | tr }}</th><th scope="col">{{ 'Interface' | tr }}</th><th scope="col">{{ 'Trigger' | tr }}</th><th scope="col">{{ 'Status' | tr }}</th><th scope="col" class="num">{{ 'Read' | tr }}</th><th scope="col" class="num">{{ 'Written' | tr }}</th><th scope="col" class="num">{{ 'Failed' | tr }}</th></tr></thead>
          <tbody>
            @for (r of runs(); track r.id) {
              <tr>
                <td>{{ r.startedAt | ldate: 'medium' }}</td>
                <td>{{ humanize(r.interfaceName) }}</td>
                <td>{{ humanize(r.trigger) }}</td>
                <td><ops-status [status]="r.status" /></td>
                <td class="num">{{ r.recordsRead }}</td>
                <td class="num">{{ r.recordsWritten }}</td>
                <td class="num">{{ r.recordsFailed }}</td>
              </tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .tile { display: flex; flex-direction: column; gap: 6px; padding: 12px 16px; border: 1px solid var(--mat-sys-outline-variant); border-radius: 12px; }
    .tile strong { font: var(--mat-sys-title-large); }
    .runs { margin-top: 16px; }
  `,
})
export class IntegrationMonitor {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  protected readonly health = signal<SystemHealth | null>(null);
  protected readonly errors = signal<IntegrationErrorInfo[]>([]);
  protected readonly runs = signal<IntegrationRunInfo[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly humanize = humanize;
  protected readonly isAdmin = computed(() => this.auth.hasAnyRole('ERP_ADMIN'));
  protected readonly canRetry = computed(() => this.auth.hasAnyRole('ERP_ADMIN', 'SUPPORT'));

  constructor() {
    void this.load();
  }

  protected systemCode(label: string): string | undefined {
    return SYSTEMS[label];
  }

  async load(): Promise<void> {
    try {
      const [health, errors, runs] = await Promise.all([
        this.api.systemHealth(),
        this.api.integrationErrors(true),
        this.api.integrationRuns(),
      ]);
      this.health.set(health);
      this.errors.set(errors.items);
      this.runs.set(runs.items);
      this.error.set(null);
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  protected async outage(system: string, down: boolean): Promise<void> {
    await this.run(() => this.api.simulateOutage(system, down));
  }

  protected async retry(e: IntegrationErrorInfo): Promise<void> {
    await this.run(() => this.api.retryIntegrationError(e.id));
  }

  protected async resolve(e: IntegrationErrorInfo): Promise<void> {
    await this.run(() => this.api.resolveIntegrationError(e.id));
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    try {
      await action();
    } catch (e) {
      this.error.set(describeError(e));
    }
    await this.load();
  }
}
