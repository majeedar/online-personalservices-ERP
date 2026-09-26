import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { TravelSummary } from '../core/api/models';
import { StatusChip } from '../shared/status-chip';
import { I18N_PIPES } from '../core/i18n/pipes';

@Component({
  selector: 'ops-travel-list',
  imports: [I18N_PIPES, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Business travel' | tr }}</h1>
        <p>{{ 'Request trips, then claim expenses once you are back.' | tr }}</p>
      </div>
      <a mat-flat-button routerLink="/travel/new"><mat-icon>add</mat-icon> {{ 'New travel request' | tr }}</a>
    </header>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" [attr.aria-label]="'Loading' | tr" />
    }
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    <mat-card appearance="outlined">
      <mat-card-content>
        @if (trips().length === 0 && !loading()) {
          <p class="muted">{{ 'No travel requests yet.' | tr }}</p>
        } @else {
          <div class="table-scroll">
            <table class="data">
              <caption>{{ 'Your trips' | tr }}</caption>
              <thead>
                <tr>
                  <th scope="col">{{ 'Destination' | tr }}</th>
                  <th scope="col">{{ 'Purpose' | tr }}</th>
                  <th scope="col">{{ 'Dates' | tr }}</th>
                  <th scope="col" class="num">{{ 'Estimated cost' | tr }}</th>
                  <th scope="col">{{ 'Status' | tr }}</th>
                </tr>
              </thead>
              <tbody>
                @for (t of trips(); track t.id) {
                  <tr>
                    <td><a [routerLink]="['/travel', t.id]">{{ t.destinationCity }} ({{ t.destinationCountry }})</a></td>
                    <td>{{ t.purpose }}</td>
                    <td>{{ t.startDateTime | ldate: 'mediumDate' }} – {{ t.endDateTime | ldate: 'mediumDate' }}</td>
                    <td class="num">{{ t.estimatedCost | lcurrency: t.currency }}</td>
                    <td><ops-status [status]="t.status" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
})
export class TravelList {
  private readonly api = inject(Api);
  protected readonly trips = signal<TravelSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api
      .trips()
      .then((t) => this.trips.set(t))
      .catch((e) => this.error.set(describeError(e)))
      .finally(() => this.loading.set(false));
  }
}
