import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { ExpenseType, TravelDetail as Detail } from '../core/api/models';
import { humanize } from '../core/format';
import { askDecision } from '../shared/decision-dialog';
import { StatusChip } from '../shared/status-chip';
import { WorkflowTimeline } from '../shared/workflow-timeline';

const EXPENSE_TYPES: ExpenseType[] = ['TRAIN', 'FLIGHT', 'HOTEL', 'TAXI', 'LOCAL_TRANSPORT', 'MILEAGE', 'MEALS', 'CONFERENCE_FEE', 'OTHER'];

/** Trip details: approvals, ERP exports, expenses and settlement (AGENT.md §43). */
@Component({
  selector: 'ops-travel-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    StatusChip,
    WorkflowTimeline,
  ],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }
    @if (trip(); as t) {
      <header class="page-header">
        <div>
          <h1>{{ t.destinationCity }} ({{ t.destinationCountry }}) · {{ t.startDateTime | date: 'mediumDate' }}</h1>
          <p>{{ t.employeeName }} · {{ t.purpose }} · <ops-status [status]="t.status" /></p>
        </div>
        <div class="actions">
          @if (t.actions.decide) {
            <button mat-flat-button type="button" (click)="decide()" [disabled]="busy()">
              <mat-icon>check</mat-icon> {{ decisionLabel(t.actions.decisionStep) }}
            </button>
            <button mat-stroked-button type="button" (click)="reject('return')" [disabled]="busy()">Return</button>
            @if (t.actions.decisionStep !== 'TRAVEL_OFFICE_REVIEW') {
              <button mat-stroked-button type="button" (click)="reject('reject')" [disabled]="busy()">Reject</button>
            }
          }
          @if (t.actions.edit) {
            <a mat-stroked-button [routerLink]="['/travel', t.id, 'edit']"><mat-icon>edit</mat-icon> Edit</a>
          }
          @if (t.actions.submit) {
            <button mat-flat-button type="button" (click)="act('submit')" [disabled]="busy()">Submit</button>
          }
          @if (t.actions.markCompleted) {
            <button mat-flat-button type="button" (click)="act('mark-completed')" [disabled]="busy()">Mark trip completed</button>
          }
          @if (t.actions.submitExpenses) {
            <button mat-flat-button type="button" (click)="act('submit-expenses')" [disabled]="busy()">Submit expense claim</button>
          }
          @if (t.actions.cancel) {
            <button mat-button type="button" (click)="cancel()" [disabled]="busy()">Cancel trip</button>
          }
        </div>
      </header>

      <div class="grid-2">
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>Trip</h2></mat-card-title></mat-card-header>
          <mat-card-content>
            <dl class="dl">
              <dt>Dates</dt><dd>{{ t.startDateTime | date: 'medium' }} – {{ t.endDateTime | date: 'medium' }}</dd>
              <dt>Transport</dt><dd>{{ humanize(t.transportMode) }}</dd>
              <dt>Estimated cost</dt><dd>{{ t.estimatedCost | currency: t.currency }}</dd>
              <dt>Cost centre</dt><dd>{{ t.costCentre }} {{ t.projectCode ?? '' }}</dd>
              @for (f of t.fundings; track $index) {
                <dt>Funding</dt>
                <dd>{{ f.source?.costCentre }} {{ f.source?.projectCode ?? '' }}: {{ f.percentage !== null && f.percentage !== undefined ? f.percentage + ' %' : (f.amount | currency: t.currency) }}</dd>
              }
              <dt>Travel ERP no.</dt><dd>{{ t.externalTravelReference ?? '—' }}</dd>
              @if (t.settledAmount !== null && t.settledAmount !== undefined) {
                <dt>Settled</dt><dd>{{ t.settledAmount | currency: t.currency }}</dd>
                <dt>Settlement no.</dt><dd>{{ t.settlementReference ?? 'pending' }}</dd>
                <dt>Finance document</dt><dd>{{ t.financePostingReference ?? 'pending' }}</dd>
              }
            </dl>
            @if (t.exports.length) {
              <h3>Exports to university systems</h3>
              <table class="data">
                <thead><tr><th scope="col">Export</th><th scope="col">Status</th><th scope="col" class="num">Attempts</th></tr></thead>
                <tbody>
                  @for (x of t.exports; track x.type) {
                    <tr>
                      <td>{{ humanize(x.type) }}</td>
                      <td><ops-status [status]="x.status" /> @if (x.lastError) { <small class="muted">{{ x.lastError }}</small> }</td>
                      <td class="num">{{ x.attempts }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </mat-card-content>
        </mat-card>
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>Approval</h2></mat-card-title></mat-card-header>
          <mat-card-content><ops-workflow-timeline [history]="t.history" /></mat-card-content>
        </mat-card>
      </div>

      @if (t.expenses.length || t.actions.editExpenses) {
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title><h2>Expenses</h2></mat-card-title></mat-card-header>
          <mat-card-content>
            <table class="data">
              <thead>
                <tr><th scope="col">Date</th><th scope="col">Type</th><th scope="col">Description</th><th scope="col" class="num">Amount</th><th scope="col">Receipt</th><th scope="col"><span class="sr-only">Actions</span></th></tr>
              </thead>
              <tbody>
                @for (x of t.expenses; track x.id) {
                  <tr>
                    <td>{{ x.expenseDate | date: 'mediumDate' }}</td>
                    <td>{{ humanize(x.expenseType) }}</td>
                    <td>{{ x.description }}</td>
                    <td class="num">{{ x.amount | currency: x.currency }}</td>
                    <td>
                      @if (x.receiptDocumentId) {
                        <a [href]="'/api/v1/travel/' + t.id + '/documents/' + x.receiptDocumentId">{{ x.receiptFileName }}</a>
                      } @else if (t.actions.editExpenses) {
                        <label class="upload">Upload <input type="file" accept="application/pdf,image/png,image/jpeg" (change)="upload(x.id, $event)" /></label>
                      } @else {
                        —
                      }
                    </td>
                    <td>
                      @if (t.actions.editExpenses) {
                        <button mat-icon-button type="button" (click)="removeExpense(x.id)" aria-label="Remove expense"><mat-icon>delete</mat-icon></button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
              <tfoot><tr><th scope="row" colspan="3">Total</th><td class="num">{{ t.expenseTotal | currency: t.currency }}</td><td colspan="2"></td></tr></tfoot>
            </table>

            @if (t.actions.editExpenses) {
              <form [formGroup]="expense" (ngSubmit)="addExpense()" class="expense-form" novalidate>
                <mat-form-field appearance="outline">
                  <mat-label>Type</mat-label>
                  <mat-select formControlName="expenseType">
                    @for (e of expenseTypes; track e) {
                      <mat-option [value]="e">{{ humanize(e) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Date</mat-label>
                  <input matInput type="date" formControlName="expenseDate" required />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Amount ({{ t.currency }})</mat-label>
                  <input matInput type="number" min="0.01" step="0.01" formControlName="amount" required />
                </mat-form-field>
                <mat-form-field appearance="outline" class="grow">
                  <mat-label>Description</mat-label>
                  <input matInput formControlName="description" />
                </mat-form-field>
                <button mat-stroked-button type="submit"><mat-icon>add</mat-icon> Add expense</button>
              </form>
              <p class="muted">Receipts are required for train, flight, hotel, taxi and conference fees.</p>
            }
          </mat-card-content>
        </mat-card>
      }
    }
  `,
  styles: `
    .dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; }
    .dl dt { color: var(--mat-sys-on-surface-variant); }
    .dl dd { margin: 0; }
    h3 { font: var(--mat-sys-title-small); margin: 16px 0 4px; }
    .expense-form { display: flex; gap: 8px; flex-wrap: wrap; align-items: flex-start; margin-top: 16px; }
    .expense-form mat-form-field { width: 170px; }
    .expense-form .grow { flex: 1 1 220px; }
    .upload { color: var(--mat-sys-primary); cursor: pointer; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    mat-card + mat-card { margin-top: 16px; }
  `,
})
export class TravelDetail implements OnInit {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);

  readonly id = input.required<string>();

  protected readonly trip = signal<Detail | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expenseTypes = EXPENSE_TYPES;
  protected readonly humanize = humanize;

  protected readonly expense = inject(FormBuilder).nonNullable.group({
    expenseType: ['HOTEL' as ExpenseType],
    expenseDate: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    description: [''],
  });

  ngOnInit(): void {
    void this.run(() => this.api.trip(this.id())).finally(() => this.loading.set(false));
  }

  private async run(call: () => Promise<Detail>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.trip.set(await call());
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }

  protected decisionLabel(step?: string): string {
    return step === 'FINANCIAL_APPROVAL' ? 'Financial approval' : step === 'TRAVEL_OFFICE_REVIEW' ? 'Accept and settle' : 'Approve';
  }

  protected act(action: 'submit' | 'mark-completed' | 'submit-expenses'): Promise<void> {
    return this.run(() => this.api.tripAction(this.id(), action));
  }

  protected async decide(): Promise<void> {
    const step = this.trip()?.actions.decisionStep;
    const action = step === 'FINANCIAL_APPROVAL' ? 'financial-approve' : step === 'TRAVEL_OFFICE_REVIEW' ? 'settle' : 'approve';
    const result = await askDecision(this.dialog, {
      title: `${this.decisionLabel(step)}?`,
      message: step === 'TRAVEL_OFFICE_REVIEW' ? 'The settlement is exported to the travel ERP and posted in finance.' : undefined,
      confirmLabel: this.decisionLabel(step),
      commentRequired: false,
    });
    if (result) {
      await this.run(() => this.api.tripAction(this.id(), action, result.comment));
    }
  }

  protected async reject(action: 'reject' | 'return'): Promise<void> {
    const result = await askDecision(this.dialog, {
      title: action === 'reject' ? 'Reject this trip?' : 'Return for correction?',
      confirmLabel: action === 'reject' ? 'Reject' : 'Return',
      commentRequired: true,
      destructive: action === 'reject',
    });
    if (result) {
      await this.run(() => this.api.tripAction(this.id(), action, result.comment));
    }
  }

  protected async cancel(): Promise<void> {
    const result = await askDecision(this.dialog, { title: 'Cancel this trip?', confirmLabel: 'Cancel trip', commentRequired: false, destructive: true });
    if (result) {
      await this.run(() => this.api.tripAction(this.id(), 'cancel'));
    }
  }

  protected async addExpense(): Promise<void> {
    if (this.expense.invalid) {
      this.expense.markAllAsTouched();
      return;
    }
    const v = this.expense.getRawValue();
    await this.run(() => this.api.addExpense(this.id(), { ...v, amount: Number(v.amount) }));
    if (!this.error()) {
      this.expense.reset({ expenseType: 'HOTEL', expenseDate: v.expenseDate, amount: 0, description: '' });
    }
  }

  protected removeExpense(expenseId: string): Promise<void> {
    return this.run(() => this.api.removeExpense(this.id(), expenseId));
  }

  protected upload(expenseId: string, event: Event): Promise<void> | void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      return this.run(() => this.api.uploadReceipt(this.id(), expenseId, file));
    }
  }
}
