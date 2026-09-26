import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import {
  AbsenceDetail,
  AbsenceInput,
  AbsencePreview,
  AbsenceSummary,
  AppNotification,
  ApprovalType,
  AuditEntry,
  BatchJobInfo,
  BatchRun,
  Decision,
  Delegation,
  EmployeeSearchResult,
  EntryType,
  ExpenseType,
  FundingSource,
  IntegrationErrorInfo,
  IntegrationRunInfo,
  LeaveBalance,
  LeaveType,
  Page,
  Report,
  ReportInfo,
  SystemHealth,
  Task,
  TeamAbsence,
  TimeCorrection,
  TimeEntry,
  TimeMonth,
  TimeToday,
  TravelDetail,
  TravelInput,
  TravelSummary,
} from './models';

/** Typed client for the /api/v1 endpoints. Every call returns a Promise. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  private get<T>(url: string, params?: Record<string, string | number | boolean | undefined>): Promise<T> {
    let p = new HttpParams();
    Object.entries(params ?? {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') {
        p = p.set(k, String(v));
      }
    });
    return firstValueFrom(this.http.get<T>(url, { params: p }));
  }

  private post<T>(url: string, body: unknown = {}): Promise<T> {
    return firstValueFrom(this.http.post<T>(url, body));
  }

  private put<T>(url: string, body: unknown): Promise<T> {
    return firstValueFrom(this.http.put<T>(url, body));
  }

  private upload<T>(url: string, file: File): Promise<T> {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(this.http.post<T>(url, form));
  }

  // ------------------------------------------------------------ directory

  searchEmployees(q: string): Promise<EmployeeSearchResult[]> {
    return this.get('/api/v1/employees/search', { q });
  }

  // -------------------------------------------------------------- absence

  leaveTypes(): Promise<LeaveType[]> {
    return this.get('/api/v1/leave-types');
  }

  leaveBalances(year?: number): Promise<LeaveBalance[]> {
    return this.get('/api/v1/leave-balances', { year });
  }

  absences(): Promise<AbsenceSummary[]> {
    return this.get('/api/v1/absences');
  }

  absence(id: string): Promise<AbsenceDetail> {
    return this.get(`/api/v1/absences/${id}`);
  }

  previewAbsence(input: AbsenceInput, excludeRequestId?: string): Promise<AbsencePreview> {
    const query = excludeRequestId ? `?excludeRequestId=${excludeRequestId}` : '';
    return this.post(`/api/v1/absences/preview${query}`, input);
  }

  createAbsence(input: AbsenceInput): Promise<AbsenceDetail> {
    return this.post('/api/v1/absences', input);
  }

  updateAbsence(id: string, input: AbsenceInput): Promise<AbsenceDetail> {
    return this.put(`/api/v1/absences/${id}`, input);
  }

  absenceAction(id: string, action: 'submit' | 'approve' | 'reject' | 'return' | 'cancel', comment?: string) {
    return this.post<AbsenceDetail>(`/api/v1/absences/${id}/${action}`, comment ? { comment } : {});
  }

  uploadAbsenceDocument(id: string, file: File) {
    return this.upload(`/api/v1/absences/${id}/documents`, file);
  }

  teamAbsences(from?: string, to?: string): Promise<TeamAbsence[]> {
    return this.get('/api/v1/team/absences', { from, to });
  }

  // --------------------------------------------------------------- travel

  fundingSources(): Promise<FundingSource[]> {
    return this.get('/api/v1/funding-sources');
  }

  trips(): Promise<TravelSummary[]> {
    return this.get('/api/v1/travel');
  }

  trip(id: string): Promise<TravelDetail> {
    return this.get(`/api/v1/travel/${id}`);
  }

  createTrip(input: TravelInput): Promise<TravelDetail> {
    return this.post('/api/v1/travel', input);
  }

  updateTrip(id: string, input: TravelInput): Promise<TravelDetail> {
    return this.put(`/api/v1/travel/${id}`, input);
  }

  tripAction(
    id: string,
    action:
      | 'submit'
      | 'approve'
      | 'financial-approve'
      | 'reject'
      | 'return'
      | 'cancel'
      | 'mark-completed'
      | 'submit-expenses'
      | 'settle',
    comment?: string,
  ): Promise<TravelDetail> {
    return this.post(`/api/v1/travel/${id}/${action}`, comment ? { comment } : {});
  }

  addExpense(
    id: string,
    expense: { expenseType: ExpenseType; expenseDate: string; amount: number; description?: string },
  ): Promise<TravelDetail> {
    return this.post(`/api/v1/travel/${id}/expenses`, expense);
  }

  removeExpense(id: string, expenseId: string): Promise<TravelDetail> {
    return firstValueFrom(this.http.delete<TravelDetail>(`/api/v1/travel/${id}/expenses/${expenseId}`));
  }

  uploadReceipt(id: string, expenseId: string, file: File): Promise<TravelDetail> {
    return this.upload(`/api/v1/travel/${id}/expenses/${expenseId}/receipt`, file);
  }

  // ----------------------------------------------------------------- time

  timeToday(): Promise<TimeToday> {
    return this.get('/api/v1/time/today');
  }

  clock(action: EntryType): Promise<TimeToday> {
    const path = { CLOCK_IN: 'clock-in', CLOCK_OUT: 'clock-out', BREAK_START: 'break-start', BREAK_END: 'break-end' };
    return this.post(`/api/v1/time/${path[action]}`);
  }

  timeMonth(year: number, month: number): Promise<TimeMonth> {
    return this.get(`/api/v1/time/month/${year}/${month}`);
  }

  timeBalance(): Promise<{ monthBalanceMinutes: number; yearBalanceMinutes: number }> {
    return this.get('/api/v1/time/balance');
  }

  timeEntries(date: string): Promise<TimeEntry[]> {
    return this.get('/api/v1/time/entries', { date });
  }

  corrections(): Promise<TimeCorrection[]> {
    return this.get('/api/v1/time/corrections');
  }

  correction(id: string): Promise<TimeCorrection> {
    return this.get(`/api/v1/time/corrections/${id}`);
  }

  requestCorrection(body: {
    date: string;
    operation: 'ADD' | 'MODIFY' | 'DELETE';
    originalEntryId?: string | null;
    requestedTime?: string | null;
    requestedType?: EntryType | null;
    reason: string;
  }): Promise<TimeCorrection> {
    return this.post('/api/v1/time/corrections', body);
  }

  correctionAction(id: string, action: 'approve' | 'reject', comment?: string): Promise<TimeCorrection> {
    return this.post(`/api/v1/time/corrections/${id}/${action}`, comment ? { comment } : {});
  }

  // ---------------------------------------------------- tasks, delegation

  tasks(): Promise<Task[]> {
    return this.get('/api/v1/tasks');
  }

  completeTask(id: string, decision: Decision, comment?: string, forwardTo?: string) {
    return this.post(`/api/v1/tasks/${id}/complete`, { decision, comment, forwardTo });
  }

  delegations(): Promise<Delegation[]> {
    return this.get('/api/v1/delegations');
  }

  createDelegation(body: { delegateId: string; approvalType: ApprovalType; validFrom: string; validTo: string }) {
    return this.post<Delegation>('/api/v1/delegations', body);
  }

  revokeDelegation(id: string) {
    return this.post(`/api/v1/delegations/${id}/revoke`);
  }

  // -------------------------------------------------------- notifications

  notifications(limit = 30): Promise<AppNotification[]> {
    return this.get('/api/v1/notifications', { limit });
  }

  unreadCount$(): Observable<{ unread: number }> {
    return this.http.get<{ unread: number }>('/api/v1/notifications/unread-count');
  }

  markRead(id: string) {
    return this.post(`/api/v1/notifications/${id}/read`);
  }

  markAllRead() {
    return this.post('/api/v1/notifications/read-all');
  }

  // ---------------------------------------------------------------- admin

  batchJobs(): Promise<BatchJobInfo[]> {
    return this.get('/api/v1/admin/batch-jobs');
  }

  batchRuns(job?: string, page = 0): Promise<Page<BatchRun>> {
    return this.get('/api/v1/admin/batch-runs', { job, page });
  }

  batchErrors(runId: string): Promise<{ recordReference?: string; errorCode: string; errorMessage: string }[]> {
    return this.get(`/api/v1/admin/batch-runs/${runId}/errors`);
  }

  runJob(name: string): Promise<BatchRun> {
    return this.post(`/api/v1/admin/jobs/${name}/run`);
  }

  integrationRuns(page = 0): Promise<Page<IntegrationRunInfo>> {
    return this.get('/api/v1/admin/integration-runs', { page });
  }

  integrationErrors(openOnly = true, page = 0): Promise<Page<IntegrationErrorInfo>> {
    return this.get('/api/v1/admin/integration-errors', { openOnly, page });
  }

  retryIntegrationError(id: string): Promise<IntegrationErrorInfo> {
    return this.post(`/api/v1/admin/integration-errors/${id}/retry`);
  }

  resolveIntegrationError(id: string): Promise<IntegrationErrorInfo> {
    return this.post(`/api/v1/admin/integration-errors/${id}/resolve`);
  }

  simulateOutage(system: string, down: boolean): Promise<Record<string, string>> {
    return this.post(`/api/v1/admin/external-systems/${system}/outage?down=${down}`);
  }

  audit(entityType?: string, entityId?: string, page = 0): Promise<Page<AuditEntry>> {
    return this.get('/api/v1/admin/audit', { entityType, entityId, page });
  }

  systemHealth(): Promise<SystemHealth> {
    return this.get('/api/v1/admin/system-health');
  }

  // -------------------------------------------------------------- reports

  reports(): Promise<ReportInfo[]> {
    return this.get('/api/v1/reports');
  }

  report(id: string, params: { year?: number; month?: string }): Promise<Report> {
    return this.get(`/api/v1/reports/${id}`, params);
  }
}
