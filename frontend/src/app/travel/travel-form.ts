import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { FundingSource, TransportMode, TravelInput } from '../core/api/models';
import { humanize } from '../core/format';
import { I18N_PIPES } from '../core/i18n/pipes';

const TRANSPORT: TransportMode[] = ['TRAIN', 'PUBLIC_TRANSPORT', 'CAR', 'FLIGHT', 'BICYCLE', 'OTHER'];

/** New / edit travel request (AGENT.md §43), including split funding (§14.3). */
@Component({
  selector: 'ops-travel-form',
  imports: [I18N_PIPES, ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ (id() ? 'Edit travel request' : 'New travel request') | tr }}</h1>
        <p>{{ 'The cost centre is checked in the finance system when you submit.' | tr }}</p>
      </div>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert"><mat-icon aria-hidden="true">error</mat-icon> {{ e }}</p>
    }
    <mat-card appearance="outlined">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="save(true)" novalidate>
          <mat-form-field appearance="outline" class="full">
            <mat-label>{{ 'Purpose' | tr }}</mat-label>
            <input matInput formControlName="purpose" required maxlength="500" />
            @if (form.controls.purpose.invalid) {
              <mat-error>{{ 'Describe the purpose of the trip.' | tr }}</mat-error>
            }
          </mat-form-field>
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Destination city' | tr }}</mat-label>
              <input matInput formControlName="destinationCity" required />
              @if (form.controls.destinationCity.invalid) {
                <mat-error>{{ 'Destination is required.' | tr }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Country (ISO code)' | tr }}</mat-label>
              <input matInput formControlName="destinationCountry" required maxlength="2" placeholder="DE" />
              @if (form.controls.destinationCountry.invalid) {
                <mat-error>{{ 'Two-letter country code, e.g. DE.' | tr }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Transport' | tr }}</mat-label>
              <mat-select formControlName="transportMode">
                @for (t of transport; track t) {
                  <mat-option [value]="t">{{ humanize(t) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Start' | tr }}</mat-label>
              <input matInput type="datetime-local" formControlName="start" required />
              @if (form.controls.start.invalid) {
                <mat-error>{{ 'Start is required.' | tr }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'End' | tr }}</mat-label>
              <input matInput type="datetime-local" formControlName="end" required />
              @if (form.controls.end.invalid) {
                <mat-error>{{ 'End is required.' | tr }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Estimated cost' | tr }}</mat-label>
              <input matInput type="number" min="0" step="0.01" formControlName="estimatedCost" required />
              <span matTextSuffix>&nbsp;{{ form.controls.currency.value }}</span>
              @if (form.controls.estimatedCost.invalid) {
                <mat-error>{{ 'Enter the estimated cost (0 or more).' | tr }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Currency' | tr }}</mat-label>
              <mat-select formControlName="currency">
                @for (c of currencies; track c) {
                  <mat-option [value]="c">{{ c }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Cost centre' | tr }}</mat-label>
              <input matInput formControlName="costCentre" required placeholder="CC-2200" />
              @if (form.controls.costCentre.invalid) {
                <mat-error>{{ 'Cost centre is required.' | tr }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'Project code (optional)' | tr }}</mat-label>
              <input matInput formControlName="projectCode" />
            </mat-form-field>
          </div>

          <fieldset>
            <legend>{{ 'Split funding (optional)' | tr }}</legend>
            <p class="muted">{{ 'Give every share as a percentage (total 100) or as an amount.' | tr }}</p>
            @for (f of fundings.controls; track $index; let i = $index) {
              <div class="funding" [formGroup]="f">
                <mat-form-field appearance="outline" class="grow">
                  <mat-label>{{ 'Funding source' | tr }}</mat-label>
                  <mat-select formControlName="fundingSourceId" required>
                    @for (s of sources(); track s.id) {
                      <mat-option [value]="s.id" data-i18n-source="master-data">{{ s.costCentre }} {{ s.projectCode ?? '' }} – {{ s.description }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>%</mat-label>
                  <input matInput type="number" min="0" max="100" formControlName="percentage" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'or amount' | tr }}</mat-label>
                  <input matInput type="number" min="0" formControlName="amount" />
                </mat-form-field>
                <button mat-icon-button type="button" (click)="fundings.removeAt(i)" [attr.aria-label]="'Remove funding share' | tr"><mat-icon>delete</mat-icon></button>
              </div>
            }
            @if (fundings.length > 0) {
              <p [class.negative]="percentTotal() !== 0 && percentTotal() !== 100">{{ 'Percentage total: {total} %' | tr: { total: percentTotal() } }}</p>
            }
            <button mat-stroked-button type="button" (click)="addFunding()"><mat-icon>add</mat-icon> {{ 'Add funding share' | tr }}</button>
          </fieldset>

          <mat-form-field appearance="outline" class="full">
            <mat-label>{{ 'Comment (optional)' | tr }}</mat-label>
            <textarea matInput formControlName="comment" rows="2" maxlength="1000"></textarea>
          </mat-form-field>

          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="saving()">{{ 'Save and submit' | tr }}</button>
            <button mat-stroked-button type="button" (click)="save(false)" [disabled]="saving()">{{ 'Save draft' | tr }}</button>
            <a mat-button [routerLink]="id() ? ['/travel', id()] : '/travel'">{{ 'Cancel' | tr }}</a>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    fieldset { border: 1px solid var(--mat-sys-outline-variant); border-radius: 8px; padding: 12px 16px; margin: 0 0 16px; }
    legend { font: var(--mat-sys-title-small); padding: 0 4px; }
    .funding { display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap; }
    .funding .grow { flex: 1 1 280px; }
    .funding mat-form-field:not(.grow) { width: 120px; }
  `,
})
export class TravelForm implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly id = input<string>();

  protected readonly transport = TRANSPORT;
  protected readonly currencies = ['EUR', 'USD', 'GBP', 'CHF'];
  protected readonly humanize = humanize;
  protected readonly sources = signal<FundingSource[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly fundings: FormArray<FormGroup> = this.fb.array<FormGroup>([]);
  protected readonly form = this.fb.nonNullable.group({
    purpose: ['', Validators.required],
    destinationCity: ['', Validators.required],
    destinationCountry: ['DE', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    transportMode: ['TRAIN' as TransportMode],
    start: ['', Validators.required],
    end: ['', Validators.required],
    estimatedCost: [0, [Validators.required, Validators.min(0)]],
    currency: ['EUR'],
    costCentre: ['', Validators.required],
    projectCode: [''],
    comment: [''],
  });

  private readonly fundingValues = toSignal(this.fundings.valueChanges, { initialValue: [] });
  protected readonly percentTotal = computed(() =>
    (this.fundingValues() as { percentage?: number | null }[]).reduce((sum, f) => sum + (Number(f.percentage) || 0), 0),
  );

  async ngOnInit(): Promise<void> {
    try {
      this.sources.set(await this.api.fundingSources());
      const id = this.id();
      if (id) {
        const t = await this.api.trip(id);
        this.form.setValue({
          purpose: t.purpose,
          destinationCity: t.destinationCity,
          destinationCountry: t.destinationCountry,
          transportMode: t.transportMode,
          start: toLocalInput(t.startDateTime),
          end: toLocalInput(t.endDateTime),
          estimatedCost: t.estimatedCost,
          currency: t.currency,
          costCentre: t.costCentre,
          projectCode: t.projectCode ?? '',
          comment: t.comment ?? '',
        });
        t.fundings.forEach((f) => this.addFunding(f.source?.id, f.percentage, f.amount));
      }
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  protected addFunding(sourceId?: string, percentage?: number | null, amount?: number | null): void {
    this.fundings.push(
      this.fb.group({
        fundingSourceId: [sourceId ?? '', Validators.required],
        percentage: [percentage ?? null],
        amount: [amount ?? null],
      }),
    );
  }

  protected async save(submit: boolean): Promise<void> {
    if (this.form.invalid || this.fundings.invalid) {
      this.form.markAllAsTouched();
      this.fundings.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: TravelInput = {
      purpose: v.purpose,
      destinationCity: v.destinationCity,
      destinationCountry: v.destinationCountry.toUpperCase(),
      transportMode: v.transportMode,
      startDateTime: new Date(v.start).toISOString(),
      endDateTime: new Date(v.end).toISOString(),
      estimatedCost: Number(v.estimatedCost),
      currency: v.currency,
      costCentre: v.costCentre.trim().toUpperCase(),
      projectCode: v.projectCode || null,
      comment: v.comment || null,
      fundings: (this.fundings.value as { fundingSourceId: string; percentage?: number; amount?: number }[]).map((f) => ({
        fundingSourceId: f.fundingSourceId,
        percentage: f.percentage === null || f.percentage === undefined || (f.percentage as unknown) === '' ? null : Number(f.percentage),
        amount: f.amount === null || f.amount === undefined || (f.amount as unknown) === '' ? null : Number(f.amount),
      })),
    };
    this.saving.set(true);
    this.error.set(null);
    try {
      const id = this.id();
      const saved = id ? await this.api.updateTrip(id, body) : await this.api.createTrip(body);
      if (submit) {
        await this.api.tripAction(saved.id, 'submit');
      }
      await this.router.navigate(['/travel', saved.id]);
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.saving.set(false);
    }
  }
}

/** ISO instant -> value for <input type="datetime-local"> in local time. */
function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
