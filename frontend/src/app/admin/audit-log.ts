import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { AuditEntry } from '../core/api/models';
import { humanize } from '../core/format';

/** Read-only audit log (AGENT.md §20); records cannot be edited through any API. */
@Component({
  selector: 'ops-audit-log',
  imports: [DatePipe, JsonPipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule],
  template: `
    <header class="page-header">
      <div>
        <h1>Audit log</h1>
        <p>Append-only; enforced by the database.</p>
      </div>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    <form [formGroup]="filter" (ngSubmit)="search(0)" class="filter">
      <mat-form-field appearance="outline">
        <mat-label>Entity type</mat-label>
        <input matInput formControlName="entityType" placeholder="AbsenceRequest" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Entity ID</mat-label>
        <input matInput formControlName="entityId" />
      </mat-form-field>
      <button mat-stroked-button type="submit"><mat-icon>search</mat-icon> Filter</button>
    </form>
    <mat-card appearance="outlined">
      <mat-card-content class="table-scroll">
        <table class="data">
          <caption>{{ total() }} record(s)</caption>
          <thead><tr><th scope="col">Time</th><th scope="col">Actor</th><th scope="col">Action</th><th scope="col">Entity</th><th scope="col">Change</th></tr></thead>
          <tbody>
            @for (a of entries(); track a.id) {
              <tr>
                <td>{{ a.timestamp | date: 'medium' }}</td>
                <td>{{ a.actorUsername }}</td>
                <td>{{ humanize(a.action) }}</td>
                <td>{{ a.entityType }}<div class="muted small">{{ a.entityId }}</div></td>
                <td>
                  @if (a.oldValue || a.newValue) {
                    <details>
                      <summary>Details</summary>
                      @if (a.oldValue) {
                        <pre>before: {{ a.oldValue | json }}</pre>
                      }
                      @if (a.newValue) {
                        <pre>after: {{ a.newValue | json }}</pre>
                      }
                      <small class="muted">Correlation {{ a.correlationId }}</small>
                    </details>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
        <div class="actions pager">
          <button mat-button type="button" [disabled]="page() === 0" (click)="search(page() - 1)">Previous</button>
          <span>Page {{ page() + 1 }}</span>
          <button mat-button type="button" [disabled]="(page() + 1) * 50 >= total()" (click)="search(page() + 1)">Next</button>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .filter { display: flex; gap: 8px; flex-wrap: wrap; align-items: flex-start; }
    pre { white-space: pre-wrap; font-size: 12px; margin: 4px 0; }
    .small { font: var(--mat-sys-body-small); }
    .pager { justify-content: flex-end; margin-top: 8px; }
  `,
})
export class AuditLog {
  private readonly api = inject(Api);

  protected readonly entries = signal<AuditEntry[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly humanize = humanize;
  protected readonly filter = inject(FormBuilder).nonNullable.group({ entityType: [''], entityId: [''] });

  constructor() {
    void this.search(0);
  }

  protected async search(page: number): Promise<void> {
    const f = this.filter.getRawValue();
    try {
      const result = await this.api.audit(f.entityType || undefined, f.entityId || undefined, page);
      this.entries.set(result.items);
      this.total.set(result.total);
      this.page.set(page);
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
