import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Employment, Me, RoleAssignment, WorkSchedule } from '../core/api/models';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);

  me(): Observable<Me> {
    return this.http.get<Me>('/api/v1/me');
  }

  employments(): Observable<Employment[]> {
    return this.http.get<Employment[]>('/api/v1/me/employments');
  }

  workSchedule(): Observable<WorkSchedule> {
    return this.http.get<WorkSchedule>('/api/v1/me/work-schedule');
  }

  roles(): Observable<RoleAssignment[]> {
    return this.http.get<RoleAssignment[]>('/api/v1/me/roles');
  }
}
