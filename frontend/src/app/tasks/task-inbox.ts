import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { Decision, Task } from '../core/api/models';
import { humanize } from '../core/format';
import { askDecision } from '../shared/decision-dialog';
import { tr } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

/** Generic task inbox (AGENT.md §17): every approval waiting for the user, incl. delegated ones. */
@Component({
  selector: 'ops-task-inbox',
  imports: [I18N_PIPES, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'My tasks' | tr }}</h1>
        <p>{{ 'Approvals assigned to you, to one of your roles, or delegated to you.' | tr }}</p>
      </div>
      <a mat-stroked-button routerLink="/delegations"><mat-icon>swap_horiz</mat-icon> {{ 'Delegations' | tr }}</a>
    </header>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" [attr.aria-label]="'Loading' | tr" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    <mat-card appearance="outlined">
      <mat-card-content class="table-scroll">
        <table class="data">
          <caption>{{ '{count} open task(s)' | tr: { count: tasks().length } }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ 'Task' | tr }}</th>
              <th scope="col">{{ 'Requested by' | tr }}</th>
              <th scope="col">{{ 'Since' | tr }}</th>
              <th scope="col">{{ 'Due' | tr }}</th>
              <th scope="col">{{ 'Actions' | tr }}</th>
            </tr>
          </thead>
          <tbody>
            @for (t of tasks(); track t.id) {
              <tr>
                <td>
                  <a [routerLink]="link(t)" data-i18n-source="server">{{ t.title }}</a>
                  <div class="muted small" data-i18n-source="server">{{ t.description }}</div>
                  @if (t.viaDelegationFrom) {
                    <div class="small"><mat-icon class="inline" aria-hidden="true">swap_horiz</mat-icon> {{ 'Delegated to you by {name}' | tr: { name: t.assignedEmployeeName } }}</div>
                  } @else if (!t.assignedEmployeeId) {
                    <div class="small muted">{{ 'For all {role}' | tr: { role: humanize(t.assignedRole) } }}</div>
                  }
                </td>
                <td>{{ t.requesterName }}</td>
                <td>{{ t.createdAt | ldate: 'mediumDate' }}</td>
                <td [class.negative]="overdue(t)">{{ t.dueDate | ldate: 'mediumDate' }}{{ overdue(t) ? ' (overdue)' : '' }}</td>
                <td class="actions">
                  <button mat-flat-button type="button" (click)="decide(t, 'APPROVE')" [disabled]="busy()">{{ 'Approve' | tr }}</button>
                  <button mat-stroked-button type="button" (click)="decide(t, 'RETURN_FOR_CORRECTION')" [disabled]="busy()">{{ 'Return' | tr }}</button>
                  <button mat-stroked-button type="button" (click)="decide(t, 'REJECT')" [disabled]="busy()">{{ 'Reject' | tr }}</button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="muted">{{ 'Nothing to do. 🎉' | tr }}</td></tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .small { font: var(--mat-sys-body-small); }
    .inline { font-size: 16px; width: 16px; height: 16px; vertical-align: middle; }
  `,
})
export class TaskInbox {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);

  protected readonly tasks = signal<Task[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly humanize = humanize;
  private readonly today = new Date().toISOString().slice(0, 10);

  constructor() {
    void this.load();
  }

  protected link(t: Task): string[] {
    switch (t.businessObjectType) {
      case 'AbsenceRequest':
        return ['/absence', t.businessObjectId];
      case 'TravelRequest':
        return ['/travel', t.businessObjectId];
      case 'TimeCorrectionRequest':
        return ['/time/corrections', t.businessObjectId];
      default:
        return ['/tasks'];
    }
  }

  protected overdue(t: Task): boolean {
    return !!t.dueDate && t.dueDate < this.today;
  }

  protected async decide(t: Task, decision: Decision): Promise<void> {
    const label = tr(decision === 'APPROVE' ? 'Approve' : decision === 'REJECT' ? 'Reject' : 'Return');
    const result = await askDecision(this.dialog, {
      title: `${label}: ${t.title}?`,
      message: t.description,
      confirmLabel: label,
      commentRequired: decision !== 'APPROVE',
      destructive: decision === 'REJECT',
    });
    if (!result) {
      return;
    }
    this.busy.set(true);
    try {
      await this.api.completeTask(t.id, decision, result.comment);
      await this.load();
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.tasks.set(await this.api.tasks());
      this.error.set(null);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.loading.set(false);
    }
  }
}
