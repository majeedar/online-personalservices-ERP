import { PercentPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { catchError, forkJoin, of } from 'rxjs';
import { describeError } from '../core/api/api-error';
import { Employment, Me, RoleAssignment, WorkSchedule } from '../core/api/models';
import { formatMinutes, humanize } from '../core/format';
import { EmployeeApi } from './employee-api.service';
import { I18N_PIPES } from '../core/i18n/pipes';

@Component({
  selector: 'ops-profile',
  imports: [I18N_PIPES, MatCardModule, MatIconModule, MatProgressBarModule, PercentPipe],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly api = inject(EmployeeApi);

  protected readonly me = signal<Me | null>(null);
  protected readonly employments = signal<Employment[]>([]);
  protected readonly schedule = signal<WorkSchedule | null>(null);
  protected readonly roles = signal<RoleAssignment[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly formatMinutes = formatMinutes;
  protected readonly humanize = humanize;

  constructor() {
    forkJoin({
      me: this.api.me(),
      employments: this.api.employments(),
      schedule: this.api.workSchedule().pipe(catchError(() => of(null))),
      roles: this.api.roles(),
    }).subscribe({
      next: (r) => {
        this.me.set(r.me);
        this.employments.set(r.employments);
        this.schedule.set(r.schedule);
        this.roles.set(r.roles);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(describeError(e));
        this.loading.set(false);
      },
    });
  }
}
