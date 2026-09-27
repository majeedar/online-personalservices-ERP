/** API contracts of the backend (/api/v1). Keep in sync with the Java response records. */

export type Role =
  | 'EMPLOYEE'
  | 'SUPERVISOR'
  | 'HR_ADMIN'
  | 'TRAVEL_OFFICE'
  | 'FINANCIAL_APPROVER'
  | 'TIME_ADMIN'
  | 'ERP_ADMIN'
  | 'SUPPORT'
  | 'AUDITOR';

export interface Session {
  employeeId: string;
  username: string;
  displayName: string;
  roles: Role[];
  /** Language saved in the profile; absent until the employee chooses one. */
  language?: 'en' | 'de' | null;
}

/** Uniform error body returned by every endpoint (AGENT.md §33). */
export interface ApiError {
  code: string;
  message: string;
  correlationId?: string;
  details?: { field: string; message: string }[];
}

export interface OrganisationUnitRef {
  id: string;
  code: string;
  name: string;
  costCentre?: string;
}

export interface Me {
  id: string;
  personnelNumber: string;
  firstName: string;
  lastName: string;
  displayName: string;
  email: string;
  username: string;
  organisationUnit?: OrganisationUnitRef;
  roles: Role[];
}

export type EmploymentType = 'ACADEMIC' | 'ADMINISTRATIVE' | 'TECHNICAL' | 'STUDENT_ASSISTANT' | 'OTHER';
export type EmploymentStatus = 'ACTIVE' | 'FUTURE' | 'ENDED' | 'SUSPENDED';

export interface Employment {
  id: string;
  startDate: string;
  endDate?: string;
  employmentType: EmploymentType;
  weeklyHours: number;
  fullTimeEquivalent: number;
  status: EmploymentStatus;
  current: boolean;
}

export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface WorkSchedule {
  id: string;
  validFrom: string;
  validTo?: string;
  weeklyTargetMinutes: number;
  days: { weekday: Weekday; targetMinutes: number; workingDay: boolean }[];
}

export interface RoleAssignment {
  role: Role;
  validFrom: string;
  validTo?: string;
  active: boolean;
}

// ------------------------------------------------------------------ shared

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface PersonRef {
  id: string;
  displayName: string;
}

export interface EmployeeSearchResult {
  id: string;
  displayName: string;
  organisationUnitName?: string;
}

export type ApprovalType = 'ABSENCE' | 'TRAVEL' | 'TIME_CORRECTION' | 'FINANCIAL' | 'HR_REVIEW';
export type Decision = 'APPROVE' | 'REJECT' | 'RETURN_FOR_CORRECTION' | 'FORWARD';

export interface DecisionView {
  approverId: string;
  approverName?: string;
  onBehalfOfId?: string;
  onBehalfOfName?: string;
  decision: Decision;
  comment?: string;
  decidedAt: string;
}

export interface StepHistory {
  stepNumber: number;
  stepType: string;
  assignedEmployeeId?: string;
  assignedEmployeeName?: string;
  assignedRole?: string;
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED';
  startedAt?: string;
  completedAt?: string;
  decisions: DecisionView[];
}

export interface InstanceHistory {
  instanceId: string;
  definitionCode: string;
  status: string;
  createdAt: string;
  completedAt?: string;
  steps: StepHistory[];
}

export interface Task {
  id: string;
  title: string;
  description?: string;
  dueDate?: string;
  status: string;
  createdAt: string;
  definitionCode: string;
  businessObjectType: string;
  businessObjectId: string;
  stepType: string;
  approvalType: ApprovalType;
  assignedEmployeeId?: string;
  assignedEmployeeName?: string;
  assignedRole?: string;
  requesterId: string;
  requesterName?: string;
  viaDelegationFrom?: string;
}

export interface Delegation {
  id: string;
  delegatorId: string;
  delegatorName?: string;
  delegateId: string;
  delegateName?: string;
  approvalType: ApprovalType;
  validFrom: string;
  validTo: string;
  active: boolean;
  effectiveToday: boolean;
  givenByMe: boolean;
}

export interface AppNotification {
  id: string;
  type: string;
  businessObjectType?: string;
  businessObjectId?: string;
  subject: string;
  message: string;
  createdAt: string;
  read: boolean;
}

export interface DocumentInfo {
  id: string;
  documentType: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

// ----------------------------------------------------------------- absence

export interface LeaveType {
  id: string;
  code: string;
  name: string;
  deductsEntitlement: boolean;
  requiresApproval: boolean;
  creditsWorkingTime: boolean;
  attachmentRequired: boolean;
}

export type AbsenceStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED';

/** Half days (ADR-018): a request may start in the afternoon and end at noon. */
export type DayPart = 'FULL' | 'MORNING' | 'AFTERNOON';

export interface AbsenceDay {
  date: string;
  kind: 'WORKING_DAY' | 'NON_WORKING_DAY' | 'HOLIDAY' | 'NO_SCHEDULE';
  dayPart: DayPart;
  plannedMinutes: number;
  creditedMinutes: number;
  entitlementDeduction: number;
}

export interface AbsenceSummary {
  id: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  startDayPart: DayPart;
  endDayPart: DayPart;
  status: AbsenceStatus;
  workingDays: number;
  deduction: number;
  submittedAt?: string;
}

export interface AbsenceDetail extends AbsenceSummary {
  employee: PersonRef;
  representative?: PersonRef;
  comment?: string;
  createdAt: string;
  days: AbsenceDay[];
  history: InstanceHistory[];
  documents: DocumentInfo[];
  actions: {
    edit: boolean;
    submit: boolean;
    cancel: boolean;
    decide: boolean;
    taskId?: string;
    decideAsDelegate: boolean;
  };
  /** Set when personal details were removed after the retention period. */
  anonymisedAt?: string;
}

export interface AbsenceInput {
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  startDayPart?: DayPart;
  endDayPart?: DayPart;
  representativeId?: string | null;
  comment?: string | null;
}

export interface AbsencePreview {
  days: AbsenceDay[];
  workingDays: number;
  deduction: number;
  currentBalance?: number;
  projectedBalance?: number;
  issues: { code: string; message: string }[];
}

export interface LeaveBalance {
  leaveTypeCode: string;
  leaveTypeName: string;
  year: number;
  baseDays: number;
  carryOverDays: number;
  additionalDays: number;
  usedDays: number;
  reservedDays: number;
  remainingDays: number;
  carryOverExpiry?: string;
}

export interface TeamAbsence {
  requestId: string;
  employee: PersonRef;
  startDate: string;
  endDate: string;
  startDayPart: DayPart;
  endDayPart: DayPart;
  status: AbsenceStatus;
  workingDays: number;
}

// ------------------------------------------------------------------ travel

export type TravelStatus =
  | 'DRAFT'
  | 'IN_APPROVAL'
  | 'AUTHORIZED'
  | 'REJECTED'
  | 'COMPLETED'
  | 'EXPENSES_SUBMITTED'
  | 'SETTLED'
  | 'CANCELLED';
export type TransportMode = 'TRAIN' | 'PUBLIC_TRANSPORT' | 'CAR' | 'FLIGHT' | 'BICYCLE' | 'OTHER';
export type ExpenseType =
  | 'TRAIN'
  | 'FLIGHT'
  | 'HOTEL'
  | 'TAXI'
  | 'LOCAL_TRANSPORT'
  | 'MILEAGE'
  | 'MEALS'
  | 'CONFERENCE_FEE'
  | 'OTHER';

export interface FundingSource {
  id: string;
  costCentre: string;
  projectCode?: string;
  fundCode: string;
  description: string;
}

export interface TravelSummary {
  id: string;
  purpose: string;
  destinationCity: string;
  destinationCountry: string;
  startDateTime: string;
  endDateTime: string;
  estimatedCost: number;
  currency: string;
  status: TravelStatus;
}

export interface TravelExpense {
  id: string;
  expenseType: ExpenseType;
  expenseDate: string;
  amount: number;
  currency: string;
  description?: string;
  receiptDocumentId?: string;
  receiptFileName?: string;
  status: string;
}

export interface TravelDetail extends TravelSummary {
  employeeId: string;
  employeeName?: string;
  transportMode: TransportMode;
  costCentre: string;
  projectCode?: string;
  comment?: string;
  fundings: { source?: FundingSource; percentage?: number; amount?: number }[];
  expenses: TravelExpense[];
  expenseTotal: number;
  settledAmount?: number;
  externalTravelReference?: string;
  settlementReference?: string;
  financePostingReference?: string;
  exports: { type: string; status: string; attempts: number; lastError?: string; processedAt?: string }[];
  history: InstanceHistory[];
  actions: {
    edit: boolean;
    submit: boolean;
    cancel: boolean;
    markCompleted: boolean;
    editExpenses: boolean;
    submitExpenses: boolean;
    decide: boolean;
    decisionStep?: string;
    taskId?: string;
  };
}

export interface TravelInput {
  purpose: string;
  destinationCity: string;
  destinationCountry: string;
  startDateTime: string;
  endDateTime: string;
  transportMode: TransportMode;
  estimatedCost: number;
  currency: string;
  costCentre: string;
  projectCode?: string | null;
  comment?: string | null;
  fundings: { fundingSourceId: string; percentage?: number | null; amount?: number | null }[];
}

// -------------------------------------------------------------------- time

export type EntryType = 'CLOCK_IN' | 'CLOCK_OUT' | 'BREAK_START' | 'BREAK_END';

export interface TimeEntry {
  id: string;
  timestamp: string;
  type: EntryType;
  source: string;
  voided: boolean;
  correctionRequestId?: string;
}

export interface TimeDay {
  date: string;
  targetMinutes: number;
  workedMinutes: number;
  breakMinutes: number;
  absenceMinutes: number;
  creditedMinutes: number;
  balanceMinutes: number;
  absenceType?: string;
  holidayName?: string;
  incomplete: boolean;
  statutoryBreakApplied: boolean;
  status: 'OPEN' | 'CALCULATED' | 'CORRECTION_PENDING' | 'CLOSED';
  future: boolean;
  /** False before the employee's first booking: such days do not count towards the balance. */
  accounted: boolean;
}

export interface TimeToday {
  date: string;
  state: 'OFF' | 'WORKING' | 'ON_BREAK' | null;
  allowedActions: EntryType[];
  entries: TimeEntry[];
  account: TimeDay;
}

export interface TimeMonth {
  year: number;
  month: number;
  days: TimeDay[];
  totals: { targetMinutes: number; workedMinutes: number; creditedMinutes: number; balanceMinutes: number };
}

export interface TimeCorrection {
  id: string;
  date: string;
  operation: 'ADD' | 'MODIFY' | 'DELETE';
  originalEntryId?: string;
  requestedTimestamp?: string;
  requestedType?: EntryType;
  reason: string;
  status: 'IN_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  createdAt: string;
  decidedAt?: string;
  history: InstanceHistory[];
  actionableTaskId?: string;
}

export interface MonthClosingStatus {
  month: string; // YYYY-MM
  closed: boolean;
  closedAt?: string;
  closedBy?: string;
  employees: number;
  pendingCorrections: number;
  closable: boolean;
  blockedReason?: string;
}

// ------------------------------------------------------------------- admin

export interface BatchRun {
  id: string;
  jobName: string;
  trigger: 'SCHEDULED' | 'MANUAL';
  startedAt: string;
  finishedAt?: string;
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';
  processedRecords: number;
  successfulRecords: number;
  failedRecords: number;
  startedBy?: string;
  correlationId?: string;
}

export interface BatchJobInfo {
  name: string;
  description: string;
  schedule: string;
  lastRun?: BatchRun;
}

export interface IntegrationRunInfo {
  id: string;
  interfaceName: string;
  trigger: string;
  startedAt: string;
  finishedAt?: string;
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';
  recordsRead: number;
  recordsWritten: number;
  recordsFailed: number;
  correlationId?: string;
}

export interface IntegrationErrorInfo {
  id: string;
  integrationRunId: string;
  externalReference?: string;
  errorCode: string;
  errorMessage: string;
  retryCount: number;
  retryable: boolean;
  createdAt: string;
  resolved: boolean;
  resolvedAt?: string;
}

export interface AuditEntry {
  id: string;
  timestamp: string;
  actorUsername?: string;
  action: string;
  entityType: string;
  entityId?: string;
  oldValue?: unknown;
  newValue?: unknown;
  correlationId?: string;
}

export interface SystemHealth {
  components: Record<string, string>;
  pendingExports: number;
  failedExports: number;
  openIntegrationErrors: number;
  failedBatchRunsLast24h: number;
  openWorkflowTasks: number;
  failedNotifications: number;
}

export interface ReportInfo {
  id: string;
  title: string;
}

export interface Report {
  title: string;
  columns: string[];
  rows: (string | number | null)[][];
}
