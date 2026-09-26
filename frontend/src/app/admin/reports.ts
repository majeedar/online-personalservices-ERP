import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { Report, ReportInfo } from '../core/api/models';

/** Simple reports with CSV export (AGENT.md §81); the list depends on the user's roles. */
@Component({
  selector: 'ops-reports',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatListModule],
  template: `
    <header class="page-header">
      <div>
        <h1>Reports</h1>
        <p>Only reports your roles permit are listed.</p>
      </div>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    <div class="layout">
      <mat-card appearance="outlined">
        <mat-nav-list aria-label="Reports">
          @for (r of available(); track r.id) {
            <a mat-list-item href="#" (click)="$event.preventDefault(); select(r)" [activated]="selected()?.id === r.id">{{ r.title }}</a>
          } @empty {
            <p class="muted pad">No reports available for your roles.</p>
          }
        </mat-nav-list>
      </mat-card>
      <mat-card appearance="outlined">
        <mat-card-content>
          @if (selected(); as r) {
            <div class="actions params">
              @if (r.id === 'leave-usage') {
                <mat-form-field appearance="outline"><mat-label>Year</mat-label><input matInput type="number" [(ngModel)]="year" /></mat-form-field>
              }
              @if (r.id === 'working-time') {
                <mat-form-field appearance="outline"><mat-label>Month</mat-label><input matInput type="month" [(ngModel)]="month" /></mat-form-field>
              }
              <button mat-stroked-button type="button" (click)="select(r)"><mat-icon>refresh</mat-icon> Run</button>
              <a mat-stroked-button [href]="csvUrl(r)"><mat-icon>download</mat-icon> CSV</a>
            </div>
            @if (report(); as rep) {
              <div class="table-scroll">
                <table class="data">
                  <caption>{{ rep.title }}</caption>
                  <thead><tr>@for (c of rep.columns; track c) {<th scope="col">{{ c }}</th>}</tr></thead>
                  <tbody>
                    @for (row of rep.rows; track $index) {
                      <tr>@for (cell of row; track $index) {<td>{{ cell }}</td>}</tr>
                    } @empty {
                      <tr><td [attr.colspan]="rep.columns.length" class="muted">No data.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          } @else {
            <p class="muted">Choose a report.</p>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: minmax(220px, 280px) 1fr; gap: 16px; }
    @media (max-width: 800px) { .layout { grid-template-columns: 1fr; } }
    .pad { padding: 16px; }
    .params mat-form-field { width: 180px; }
  `,
})
export class Reports {
  private readonly api = inject(Api);

  protected readonly available = signal<ReportInfo[]>([]);
  protected readonly selected = signal<ReportInfo | null>(null);
  protected readonly report = signal<Report | null>(null);
  protected readonly error = signal<string | null>(null);
  protected year = new Date().getFullYear();
  protected month = new Date().toISOString().slice(0, 7);

  constructor() {
    this.api
      .reports()
      .then((r) => this.available.set(r))
      .catch((e) => this.error.set(describeError(e)));
  }

  protected async select(r: ReportInfo): Promise<void> {
    this.selected.set(r);
    this.report.set(null);
    try {
      this.report.set(await this.api.report(r.id, this.params(r)));
      this.error.set(null);
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  protected csvUrl(r: ReportInfo): string {
    const p = new URLSearchParams({ format: 'csv' });
    Object.entries(this.params(r)).forEach(([k, v]) => v !== undefined && p.set(k, String(v)));
    return `/api/v1/reports/${r.id}?${p.toString()}`;
  }

  private params(r: ReportInfo): { year?: number; month?: string } {
    return r.id === 'leave-usage' ? { year: this.year } : r.id === 'working-time' ? { month: this.month } : {};
  }
}
