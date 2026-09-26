import { Role } from './api/models';

export type NavSection = 'Self-service' | 'Team' | 'Administration';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  section: NavSection;
  /** The item is shown if the user has any of these roles. */
  roles: Role[];
  /** Set to true when the module's phase is delivered (AGENT.md §73). */
  available: boolean;
}

export const ALL_STAFF: Role[] = [
  'EMPLOYEE',
  'SUPERVISOR',
  'HR_ADMIN',
  'TRAVEL_OFFICE',
  'FINANCIAL_APPROVER',
  'TIME_ADMIN',
  'ERP_ADMIN',
  'SUPPORT',
  'AUDITOR',
];
export const APPROVERS: Role[] = ['SUPERVISOR', 'FINANCIAL_APPROVER', 'TRAVEL_OFFICE', 'TIME_ADMIN', 'HR_ADMIN', 'EMPLOYEE'];
export const OPERATORS: Role[] = ['ERP_ADMIN', 'SUPPORT'];
export const REPORT_READERS: Role[] = [
  'HR_ADMIN',
  'TIME_ADMIN',
  'TRAVEL_OFFICE',
  'FINANCIAL_APPROVER',
  'ERP_ADMIN',
  'SUPPORT',
  'AUDITOR',
];

/**
 * Role-aware menu (AGENT.md §40). "My Tasks" is open to every employee because
 * delegation can make anyone an approver. Configuration is externalised in
 * application.yml / environment variables (§48) and has no screen.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', route: '/dashboard', section: 'Self-service', roles: ALL_STAFF, available: true },
  { label: 'My Profile', icon: 'badge', route: '/profile', section: 'Self-service', roles: ALL_STAFF, available: true },
  { label: 'Absence', icon: 'beach_access', route: '/absence', section: 'Self-service', roles: ['EMPLOYEE'], available: true },
  { label: 'Travel', icon: 'flight_takeoff', route: '/travel', section: 'Self-service', roles: ['EMPLOYEE'], available: true },
  { label: 'Working Time', icon: 'schedule', route: '/time', section: 'Self-service', roles: ['EMPLOYEE'], available: true },
  { label: 'My Tasks', icon: 'task_alt', route: '/tasks', section: 'Self-service', roles: APPROVERS, available: true },
  { label: 'Notifications', icon: 'notifications', route: '/notifications', section: 'Self-service', roles: ALL_STAFF, available: true },
  { label: 'Team Calendar', icon: 'calendar_month', route: '/team/calendar', section: 'Team', roles: ['SUPERVISOR'], available: true },
  { label: 'Delegations', icon: 'swap_horiz', route: '/delegations', section: 'Team', roles: ['SUPERVISOR', 'FINANCIAL_APPROVER', 'HR_ADMIN'], available: true },
  { label: 'Month Closing', icon: 'lock_clock', route: '/admin/month-closing', section: 'Administration', roles: ['TIME_ADMIN', 'HR_ADMIN'], available: true },
  { label: 'Reports', icon: 'summarize', route: '/reports', section: 'Administration', roles: REPORT_READERS, available: true },
  { label: 'Batch Jobs', icon: 'sync', route: '/admin/batch', section: 'Administration', roles: [...OPERATORS, 'AUDITOR'], available: true },
  { label: 'Integration Monitor', icon: 'hub', route: '/admin/integrations', section: 'Administration', roles: [...OPERATORS, 'AUDITOR'], available: true },
  { label: 'Audit', icon: 'policy', route: '/admin/audit', section: 'Administration', roles: [...OPERATORS, 'AUDITOR'], available: true },
];

export function visibleNavItems(roles: readonly Role[], items: NavItem[] = NAV_ITEMS): NavItem[] {
  return items.filter((item) => item.available && item.roles.some((r) => roles.includes(r)));
}

export function groupBySection(items: NavItem[]): { section: NavSection; items: NavItem[] }[] {
  const order: NavSection[] = ['Self-service', 'Team', 'Administration'];
  return order
    .map((section) => ({ section, items: items.filter((i) => i.section === section) }))
    .filter((group) => group.items.length > 0);
}
