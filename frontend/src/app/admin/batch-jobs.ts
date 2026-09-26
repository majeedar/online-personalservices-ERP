import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { BatchJobInfo, BatchRun } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { StatusChip } from '../shared/status-chip';

/** Batch jobs and their history (AGENT.md §27, §28 "Admin UI must display batch history"). */
@Component({
  selector: 'ops-batch-jobs',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>Batch jobs</h1>
        <p>Scheduled jobs are idempotent and can also be started manually.</p>
      </div>
    </header>
    @if (running()) {
      <mat-progress-bar mode="indeterminate" aria-label="Job running" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    @if (message(); as m) {
      <p class="info-banner" role="status">{{ m }}</p>
    }
    <mat-card appearance="outlined">
      <mat-card-content class="table-scroll">
        <table class="data">
          <caption>Jobs</caption>
          <thead><tr><th scope="col">Job</th><th scope="col">Schedule</th><th scope="col">Last run</th><th scope="col"></th></tr></thead>
          <tbody>
            @for (j of jobs(); track j.name) {
              <tr>
                <td><strong>{{ j.name }}</strong><div class="muted">{{ j.description }}</div></td>
                <td><code>{{ j.schedule || 'manual' }}</code></td>
                <td>
                  @if (j.lastRun; as r) {
                    <ops-status [status]="r.status" /> {{ r.startedAt | date: 'short' }} · {{ r.successfulRecords }}/{{ r.processedRecords }}
                  } @else {
                    <span class="muted">never</span>
                  }
                </td>
                <td>
                  @if (canRun()) {
                    <button mat-stroked-button type="button" (click)="run(j.name)" [disabled]="running()"><mat-icon>play_arrow</mat-icon> Run now</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>

    <mat-card appearance="outlined" class="history">
      <mat-card-header><mat-card-title><h2>History</h2></mat-card-title></mat-card-header>
      <mat-card-content class="table-scroll">
        <table class="data">
          <thead>
            <tr><th scope="col">Started</th><th scope="col">Job</th><th scope="col">Trigger</th><th scope="col">Status</th><th scope="col" class="num">Processed</th><th scope="col" class="num">Failed</th><th scope="col"></th></tr>
          </thead>
          <tbody>
            @for (r of runs(); track r.id) {
              <tr>
                <td>{{ r.startedAt | date: 'medium' }}</td>
                <td>{{ r.jobName }}</td>
                <td>{{ r.trigger === 'MANUAL' ? 'manual (' + r.startedBy + ')' : 'scheduled' }}</td>
                <td><ops-status [status]="r.status" /></td>
                <td class="num">{{ r.processedRecords }}</td>
                <td class="num">{{ r.failedRecords }}</td>
                <td>
                  @if (r.failedRecords > 0) {
                    <button mat-button type="button" (click)="showErrors(r)">{{ selected() === r.id ? 'Hide' : 'Errors' }}</button>
                  }
                </td>
              </tr>
              @if (selected() === r.id) {
                <tr>
                  <td colspan="7">
                    <ul class="errors">
                      @for (e of errors(); track $index) {
                        <li><strong>{{ e.recordReference ?? '—' }}</strong> {{ e.errorCode }}: {{ e.errorMessage }}</li>
                      }
                    </ul>
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `.history { margin-top: 16px; } .errors { margin: 0; padding-left: 20px; }`,
})
export class BatchJobs {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  protected readonly jobs = signal<BatchJobInfo[]>([]);
  protected readonly runs = signal<BatchRun[]>([]);
  protected readonly errors = signal<{ recordReference?: string; errorCode: string; errorMessage: string }[]>([]);
  protected readonly selected = signal<string | null>(null);
  protected readonly running = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly canRun = computed(() => this.auth.hasAnyRole('ERP_ADMIN'));

  constructor() {
    void this.load();
  }

  protected async run(name: string): Promise<void> {
    this.running.set(true);
    this.error.set(null);
    try {
      const r = await this.api.runJob(name);
      this.message.set(`${name}: ${r.status} — ${r.successfulRecords} of ${r.processedRecords} record(s) processed.`);
      await this.load();
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.running.set(false);
    }
  }

  protected async showErrors(r: BatchRun): Promise<void> {
    if (this.selected() === r.id) {
      this.selected.set(null);
      return;
    }
    this.errors.set(await this.api.batchErrors(r.id));
    this.selected.set(r.id);
  }

  private async load(): Promise<void> {
    try {
      const [jobs, runs] = await Promise.all([this.api.batchJobs(), this.api.batchRuns()]);
      this.jobs.set(jobs);
      this.runs.set(runs.items);
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
