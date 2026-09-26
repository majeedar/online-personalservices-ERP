import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { Api } from '../core/api/api.service';
import { EmployeeSearchResult, PersonRef } from '../core/api/models';
import { marker } from '../core/i18n/i18n';
import { I18N_PIPES } from '../core/i18n/pipes';

/**
 * Staff directory search (name and unit only). Writes the selected employee's ID
 * into {@link control}; an initial selection can be shown via {@link initial}.
 */
@Component({
  selector: 'ops-employee-picker',
  imports: [I18N_PIPES, ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline" class="full">
      <mat-label>{{ label() | tr }}</mat-label>
      <input
        matInput
        [formControl]="search"
        [matAutocomplete]="auto"
        autocomplete="off"
        [placeholder]="'Type at least 2 letters' | tr"
      />
      <mat-autocomplete #auto="matAutocomplete" [displayWith]="display" (optionSelected)="select($event.option.value)">
        @for (e of results(); track e.id) {
          <mat-option [value]="e">
            {{ e.displayName }} <small class="muted">· {{ e.organisationUnitName }}</small>
          </mat-option>
        }
      </mat-autocomplete>
      @if (hint()) {
        <mat-hint>{{ hint() }}</mat-hint>
      }
    </mat-form-field>
  `,
  styles: `.full { width: 100%; } .muted { color: var(--mat-sys-on-surface-variant); }`,
})
export class EmployeePicker implements OnInit {
  private readonly api = inject(Api);

  readonly control = input.required<FormControl<string | null>>();
  readonly label = input<string>(marker('Employee'));
  readonly hint = input<string>();
  readonly initial = input<PersonRef | undefined>();

  protected readonly search = new FormControl<string | EmployeeSearchResult>('', { nonNullable: true });
  protected readonly results = signal<EmployeeSearchResult[]>([]);

  ngOnInit(): void {
    const initial = this.initial();
    if (initial) {
      this.search.setValue({ id: initial.id, displayName: initial.displayName });
    }
    this.search.valueChanges.pipe(debounceTime(250), distinctUntilChanged()).subscribe(async (value) => {
      if (typeof value !== 'string') {
        return;
      }
      if (value.trim() === '') {
        this.control().setValue(null);
      }
      this.results.set(value.trim().length >= 2 ? await this.api.searchEmployees(value.trim()) : []);
    });
  }

  protected display = (e: EmployeeSearchResult | string | null): string =>
    typeof e === 'string' ? e : (e?.displayName ?? '');

  protected select(e: EmployeeSearchResult): void {
    this.control().setValue(e.id);
    this.control().markAsDirty();
  }
}
