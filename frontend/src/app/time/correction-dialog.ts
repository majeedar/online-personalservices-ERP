import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { EntryType, TimeEntry } from '../core/api/models';
import { humanize } from '../core/format';
import { I18N_PIPES } from '../core/i18n/pipes';

/** Request a time correction for one day (AGENT.md §15.5): add a missing entry, change or remove one. */
@Component({
  selector: 'ops-correction-dialog',
  imports: [I18N_PIPES, ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ 'Correct {date}' | tr: { date: data.date | ldate: 'fullDate' } }}</h2>
    <mat-dialog-content>
      @if (error(); as e) {
        <p class="error-banner" role="alert">{{ e }}</p>
      }
      <p class="muted">{{ 'Recorded entries' | tr }}:
        @for (e of entries(); track e.id) {
          <span class="entry">{{ e.timestamp | ldate: 'HH:mm' }} {{ humanize(e.type) }}</span>
        } @empty {
          {{ 'none' | tr }}
        }
      </p>
      <form [formGroup]="form" novalidate>
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'Correction' | tr }}</mat-label>
          <mat-select formControlName="operation">
            <mat-option value="ADD">{{ 'Add a missing entry (e.g. forgotten clock-out)' | tr }}</mat-option>
            <mat-option value="MODIFY" [disabled]="entries().length === 0">{{ 'Change an entry' | tr }}</mat-option>
            <mat-option value="DELETE" [disabled]="entries().length === 0">{{ 'Remove an entry' | tr }}</mat-option>
          </mat-select>
        </mat-form-field>
        @if (form.controls.operation.value !== 'ADD') {
          <mat-form-field appearance="outline" class="full">
            <mat-label>{{ 'Entry' | tr }}</mat-label>
            <mat-select formControlName="originalEntryId">
              @for (e of entries(); track e.id) {
                <mat-option [value]="e.id">{{ e.timestamp | ldate: 'HH:mm' }} {{ humanize(e.type) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        @if (form.controls.operation.value !== 'DELETE') {
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Type' | tr }}</mat-label>
              <mat-select formControlName="requestedType">
                @for (t of types; track t) {
                  <mat-option [value]="t">{{ humanize(t) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Time' | tr }}</mat-label>
              <input matInput type="time" formControlName="requestedTime" />
            </mat-form-field>
          </div>
        }
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'Reason' | tr }}</mat-label>
          <textarea matInput formControlName="reason" rows="2" required maxlength="1000"></textarea>
          @if (form.controls.reason.invalid) {
            <mat-error>{{ 'Please give a reason.' | tr }}</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'Cancel' | tr }}</button>
      <button mat-flat-button type="button" (click)="submit()" [disabled]="busy()">{{ 'Request correction' | tr }}</button>
    </mat-dialog-actions>
  `,
  styles: `.entry { margin-right: 12px; white-space: nowrap; }`,
})
export class CorrectionDialog implements OnInit {
  protected readonly data = inject<{ date: string }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<CorrectionDialog, boolean>);
  private readonly api = inject(Api);

  protected readonly types: EntryType[] = ['CLOCK_IN', 'BREAK_START', 'BREAK_END', 'CLOCK_OUT'];
  protected readonly entries = signal<TimeEntry[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly humanize = humanize;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    operation: ['ADD' as 'ADD' | 'MODIFY' | 'DELETE'],
    originalEntryId: [''],
    requestedType: ['CLOCK_OUT' as EntryType],
    requestedTime: ['17:00'],
    reason: ['', Validators.required],
  });

  async ngOnInit(): Promise<void> {
    const all = await this.api.timeEntries(this.data.date);
    this.entries.set(all.filter((e) => !e.voided));
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    try {
      await this.api.requestCorrection({
        date: this.data.date,
        operation: v.operation,
        originalEntryId: v.operation === 'ADD' ? null : v.originalEntryId,
        requestedTime: v.operation === 'DELETE' ? null : v.requestedTime,
        requestedType: v.operation === 'DELETE' ? null : v.requestedType,
        reason: v.reason,
      });
      this.ref.close(true);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }
}
