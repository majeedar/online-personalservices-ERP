import { Component, effect, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { DateAdapter, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { Api } from '../core/api/api.service';
import { describeError, serverMessage } from '../core/api/api-error';
import { AbsenceInput, AbsencePreview, DayPart, LeaveType, PersonRef } from '../core/api/models';
import { formatDays, formatMinutes, halfDaySuffix, humanize, isoDate, parseIsoDate } from '../core/format';
import { EmployeePicker } from '../shared/employee-picker';
import { locale } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

/**
 * New / edit absence request (AGENT.md §42). The server calculates the days and
 * validates every rule; the preview shows the result while the user types.
 */
@Component({
  selector: 'ops-absence-form',
  imports: [
    I18N_PIPES,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    EmployeePicker,
  ],
  providers: [provideNativeDateAdapter()],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ (id() ? 'Edit absence request' : 'New absence request') | tr }}</h1>
        <p>{{ 'Days are calculated from your work schedule and the public-holiday calendar.' | tr }}</p>
      </div>
    </header>

    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }

    <div class="grid-2">
      <mat-card appearance="outlined">
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save(true)" novalidate>
            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'Leave type' | tr }}</mat-label>
              <mat-select formControlName="leaveTypeId" required>
                @for (t of leaveTypes(); track t.id) {
                  <mat-option [value]="t.id">{{ t.name }}</mat-option>
                }
              </mat-select>
              @if (selectedType(); as t) {
                <mat-hint>
                  {{ (t.deductsEntitlement ? 'Deducted from your entitlement.' : 'Not deducted from your entitlement.') | tr }}
                  {{ (t.requiresApproval ? 'Needs approval.' : 'Takes effect immediately.') | tr }}
                </mat-hint>
              }
              @if (form.controls.leaveTypeId.hasError('required')) {
                <mat-error>{{ 'Choose a leave type.' | tr }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'Period' | tr }}</mat-label>
              <mat-date-range-input [rangePicker]="picker">
                <input matStartDate formControlName="start" [placeholder]="'Start date' | tr" required />
                <input matEndDate formControlName="end" [placeholder]="'End date' | tr" required />
              </mat-date-range-input>
              <mat-datepicker-toggle matIconSuffix [for]="picker" />
              <mat-date-range-picker #picker />
              @if (form.controls.start.invalid || form.controls.end.invalid) {
                <mat-error>{{ 'Choose a start and end date.' | tr }}</mat-error>
              }
            </mat-form-field>

            @if (singleDay()) {
              <mat-form-field appearance="outline" class="full">
                <mat-label>{{ 'Day' | tr }}</mat-label>
                <mat-select formControlName="startPart">
                  <mat-option value="FULL">{{ 'Full day' | tr }}</mat-option>
                  <mat-option value="MORNING">{{ 'Morning (half day)' | tr }}</mat-option>
                  <mat-option value="AFTERNOON">{{ 'Afternoon (half day)' | tr }}</mat-option>
                </mat-select>
              </mat-form-field>
            } @else if (form.controls.start.value && form.controls.end.value) {
              <div class="parts">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'First day' | tr }}</mat-label>
                  <mat-select formControlName="startPart">
                    <mat-option value="FULL">{{ 'Full day' | tr }}</mat-option>
                    <mat-option value="AFTERNOON">{{ 'From noon (half day)' | tr }}</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'Last day' | tr }}</mat-label>
                  <mat-select formControlName="endPart">
                    <mat-option value="FULL">{{ 'Full day' | tr }}</mat-option>
                    <mat-option value="MORNING">{{ 'Until noon (half day)' | tr }}</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>
            }

            @if (loaded()) {
              <ops-employee-picker
                [control]="representative"
                [initial]="initialRepresentative()"
                [label]="'Representative (optional)' | tr"
                [hint]="'Who covers for you while you are away' | tr"
              />
            }

            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'Comment (optional)' | tr }}</mat-label>
              <textarea matInput formControlName="comment" rows="3" maxlength="1000"></textarea>
            </mat-form-field>

            <div class="actions">
              <button mat-flat-button type="submit" [disabled]="saving()">{{ 'Save and submit' | tr }}</button>
              <button mat-stroked-button type="button" (click)="save(false)" [disabled]="saving()">{{ 'Save draft' | tr }}</button>
              <a mat-button [routerLink]="id() ? ['/absence', id()] : '/absence'">{{ 'Cancel' | tr }}</a>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined" aria-live="polite">
        <mat-card-header><mat-card-title><h2>{{ 'Calculation' | tr }}</h2></mat-card-title></mat-card-header>
        <mat-card-content>
          @if (preview(); as p) {
            <dl class="figures">
              <dt>{{ 'Calculated working days' | tr }}</dt>
              <dd>{{ p.workingDays }}</dd>
              @if (p.currentBalance !== null && p.currentBalance !== undefined) {
                <dt>{{ 'Current balance' | tr }}</dt>
                <dd>{{ formatDays(p.currentBalance) }}</dd>
                <dt>{{ 'Projected balance' | tr }}</dt>
                <dd [class.negative]="(p.projectedBalance ?? 0) < 0">{{ formatDays(p.projectedBalance) }}</dd>
              }
            </dl>
            @for (issue of p.issues; track issue.code) {
              <p class="error-banner" role="status"><mat-icon aria-hidden="true">warning</mat-icon> {{ serverMessage(issue.code, issue.message) }}</p>
            }
            <table class="data">
              <caption>{{ 'Days in the period' | tr }}</caption>
              <thead>
                <tr><th scope="col">{{ 'Date' | tr }}</th><th scope="col">{{ 'Counts as' | tr }}</th><th scope="col" class="num">{{ 'Planned' | tr }}</th></tr>
              </thead>
              <tbody>
                @for (d of p.days; track d.date) {
                  <tr [class.off]="d.kind !== 'WORKING_DAY'">
                    <th scope="row">{{ d.date | ldate: 'EEE, d MMM' }}</th>
                    <td>{{ humanize(d.kind) }}{{ halfDaySuffix(d.dayPart) }}</td>
                    <td class="num">{{ d.plannedMinutes ? formatMinutes(d.plannedMinutes) : '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <p class="muted">{{ 'Choose a leave type and a period to see the calculation.' | tr }}</p>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .figures { display: grid; grid-template-columns: 1fr auto; gap: 4px 16px; margin: 0 0 16px; }
    .figures dd { margin: 0; font-weight: 500; text-align: right; }
    .parts { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; }
    @media (max-width: 600px) { .parts { grid-template-columns: 1fr; } }
  `,
})
export class AbsenceForm implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly dateAdapter = inject(DateAdapter<Date>);

  constructor() {
    // Date picker labels and input format follow the interface language (ADR-019).
    effect(() => this.dateAdapter.setLocale(locale()));
  }

  /** Route parameter (edit mode). */
  readonly id = input<string>();

  protected readonly leaveTypes = signal<LeaveType[]>([]);
  protected readonly preview = signal<AbsencePreview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly loaded = signal(false);
  protected readonly initialRepresentative = signal<PersonRef | undefined>(undefined);
  protected readonly representative = new FormControl<string | null>(null);
  protected readonly formatDays = formatDays;
  protected readonly formatMinutes = formatMinutes;
  protected readonly humanize = humanize;
  protected readonly halfDaySuffix = halfDaySuffix;
  protected readonly serverMessage = serverMessage;

  protected readonly form = inject(FormBuilder).group({
    leaveTypeId: ['', Validators.required],
    start: [null as Date | null, Validators.required],
    end: [null as Date | null, Validators.required],
    startPart: ['FULL' as DayPart],
    endPart: ['FULL' as DayPart],
    comment: [''],
  });

  /** Start and end on the same date: one day-part choice (full, morning, afternoon). */
  protected singleDay(): boolean {
    const { start, end } = this.form.getRawValue();
    return !!start && !!end && isoDate(start) === isoDate(end);
  }

  protected selectedType(): LeaveType | undefined {
    return this.leaveTypes().find((t) => t.id === this.form.controls.leaveTypeId.value);
  }

  async ngOnInit(): Promise<void> {
    try {
      const types = await this.api.leaveTypes();
      this.leaveTypes.set(types);
      const id = this.id();
      if (id) {
        const r = await this.api.absence(id);
        this.form.setValue({
          leaveTypeId: r.leaveType.id,
          start: parseIsoDate(r.startDate),
          end: parseIsoDate(r.endDate),
          startPart: r.startDayPart ?? 'FULL',
          endPart: r.endDayPart ?? 'FULL',
          comment: r.comment ?? '',
        });
        this.representative.setValue(r.representative?.id ?? null);
        this.initialRepresentative.set(r.representative);
      } else {
        this.form.controls.leaveTypeId.setValue(types.find((t) => t.code === 'ANNUAL_LEAVE')?.id ?? '');
      }
    } catch (e) {
      this.error.set(describeError(e));
    }
    this.loaded.set(true);
    this.form.valueChanges.subscribe(() => this.dropHalvesThatNoLongerFit());
    this.form.valueChanges.pipe(debounceTime(300)).subscribe(() => void this.refreshPreview());
    this.representative.valueChanges.pipe(debounceTime(300)).subscribe(() => void this.refreshPreview());
    void this.refreshPreview();
  }

  private input(): AbsenceInput | null {
    const v = this.form.getRawValue();
    if (!v.leaveTypeId || !v.start || !v.end) {
      return null;
    }
    return {
      leaveTypeId: v.leaveTypeId,
      startDate: isoDate(v.start),
      endDate: isoDate(v.end),
      ...this.dayParts(v.startPart, v.endPart),
      representativeId: this.representative.value,
      comment: v.comment || null,
    };
  }

  /** Over several days only "from noon" (first day) and "until noon" (last day) are possible. */
  private dropHalvesThatNoLongerFit(): void {
    const { start, end, startPart, endPart } = this.form.controls;
    if (!start.value || !end.value || this.singleDay()) {
      return;
    }
    if (startPart.value === 'MORNING') {
      startPart.setValue('FULL', { emitEvent: false });
    }
    if (endPart.value === 'AFTERNOON') {
      endPart.setValue('FULL', { emitEvent: false });
    }
  }

  /** Maps the choices to what the server accepts; a choice that no longer fits the period means a full day. */
  private dayParts(start: DayPart | null, end: DayPart | null): Pick<AbsenceInput, 'startDayPart' | 'endDayPart'> {
    if (this.singleDay()) {
      return { startDayPart: start ?? 'FULL', endDayPart: start ?? 'FULL' };
    }
    return {
      startDayPart: start === 'AFTERNOON' ? 'AFTERNOON' : 'FULL',
      endDayPart: end === 'MORNING' ? 'MORNING' : 'FULL',
    };
  }

  private async refreshPreview(): Promise<void> {
    const input = this.input();
    if (!input) {
      this.preview.set(null);
      return;
    }
    try {
      this.preview.set(await this.api.previewAbsence(input, this.id()));
      this.error.set(null);
    } catch (e) {
      this.preview.set(null);
      this.error.set(describeError(e));
    }
  }

  protected async save(submit: boolean): Promise<void> {
    const input = this.input();
    if (this.form.invalid || !input) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const id = this.id();
      const saved = id ? await this.api.updateAbsence(id, input) : await this.api.createAbsence(input);
      if (submit) {
        await this.api.absenceAction(saved.id, 'submit');
      }
      await this.router.navigate(['/absence', saved.id]);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.saving.set(false);
    }
  }
}
