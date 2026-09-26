import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { ApprovalType, Delegation } from '../core/api/models';
import { humanize } from '../core/format';
import { EmployeePicker } from '../shared/employee-picker';
import { StatusChip } from '../shared/status-chip';
import { tr } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

/** Delegate one's approvals for a period, e.g. during leave (AGENT.md §18). */
@Component({
  selector: 'ops-delegations',
  imports: [I18N_PIPES, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, EmployeePicker, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Delegations' | tr }}</h1>
        <p>{{ 'While a delegation is active, the delegate sees and decides your open approvals of that type.' | tr }}</p>
      </div>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    <div class="grid-2">
      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title><h2>{{ 'New delegation' | tr }}</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="create()" novalidate>
            <ops-employee-picker [control]="delegate" [label]="'Delegate' | tr" />
            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'Approvals' | tr }}</mat-label>
              <mat-select formControlName="approvalType">
                @for (t of types; track t) {
                  <mat-option [value]="t">{{ humanize(t) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>{{ 'From' | tr }}</mat-label>
                <input matInput type="date" formControlName="validFrom" required />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>{{ 'To' | tr }}</mat-label>
                <input matInput type="date" formControlName="validTo" required />
              </mat-form-field>
            </div>
            <button mat-flat-button type="submit">{{ 'Delegate' | tr }}</button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title><h2>{{ 'Current and past delegations' | tr }}</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          <table class="data">
            <thead><tr><th scope="col">{{ 'From → to' | tr }}</th><th scope="col">{{ 'Type' | tr }}</th><th scope="col">{{ 'Period' | tr }}</th><th scope="col">{{ 'Status' | tr }}</th><th scope="col"></th></tr></thead>
            <tbody>
              @for (d of delegations(); track d.id) {
                <tr>
                  <td>{{ d.delegatorName }} → {{ d.delegateName }}</td>
                  <td>{{ humanize(d.approvalType) }}</td>
                  <td>{{ d.validFrom | ldate: 'mediumDate' }} – {{ d.validTo | ldate: 'mediumDate' }}</td>
                  <td><ops-status [status]="!d.active ? 'CANCELLED' : d.effectiveToday ? 'ACTIVE' : 'PENDING'" [label]="(!d.active ? 'Revoked' : d.effectiveToday ? 'Active today' : 'Scheduled') | tr" /></td>
                  <td>
                    @if (d.active && d.givenByMe) {
                      <button mat-button type="button" (click)="revoke(d)">{{ 'Revoke' | tr }}</button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="muted">{{ 'No delegations.' | tr }}</td></tr>
              }
            </tbody>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
})
export class Delegations {
  private readonly api = inject(Api);

  protected readonly types: ApprovalType[] = ['ABSENCE', 'TRAVEL', 'TIME_CORRECTION', 'FINANCIAL', 'HR_REVIEW'];
  protected readonly delegations = signal<Delegation[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly humanize = humanize;
  protected readonly delegate = new FormControl<string | null>(null, Validators.required);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    approvalType: ['ABSENCE' as ApprovalType],
    validFrom: ['', Validators.required],
    validTo: ['', Validators.required],
  });

  constructor() {
    void this.load();
  }

  protected async create(): Promise<void> {
    if (this.form.invalid || !this.delegate.value) {
      this.form.markAllAsTouched();
      this.error.set(tr('Choose a delegate and a period.'));
      return;
    }
    try {
      await this.api.createDelegation({ delegateId: this.delegate.value, ...this.form.getRawValue() });
      this.error.set(null);
      await this.load();
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  protected async revoke(d: Delegation): Promise<void> {
    try {
      await this.api.revokeDelegation(d.id);
      await this.load();
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  private async load(): Promise<void> {
    try {
      this.delegations.set(await this.api.delegations());
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
