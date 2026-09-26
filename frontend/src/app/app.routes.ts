import { Routes } from '@angular/router';
import { Role } from './core/api/models';
import { authGuard } from './core/auth/auth.guard';
import { OPERATORS, REPORT_READERS } from './core/navigation';
import { marker } from './core/i18n/i18n';

// Titles are translated and suffixed by I18nTitleStrategy.
const roles = (...r: Role[]) => ({ roles: r });

export const routes: Routes = [
  {
    path: 'login',
    title: marker('Sign in'),
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: marker('Dashboard'), loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard) },
      { path: 'profile', title: marker('My profile'), loadComponent: () => import('./profile/profile').then((m) => m.Profile) },

      { path: 'absence', title: marker('Absence'), data: roles('EMPLOYEE'), loadComponent: () => import('./absence/absence-list').then((m) => m.AbsenceList) },
      { path: 'absence/new', title: marker('New absence'), data: roles('EMPLOYEE'), loadComponent: () => import('./absence/absence-form').then((m) => m.AbsenceForm) },
      { path: 'absence/:id/edit', title: marker('Edit absence'), data: roles('EMPLOYEE'), loadComponent: () => import('./absence/absence-form').then((m) => m.AbsenceForm) },
      // Detail pages are also opened by approvers; the API decides who may see what.
      { path: 'absence/:id', title: marker('Absence request'), loadComponent: () => import('./absence/absence-detail').then((m) => m.AbsenceDetail) },
      { path: 'team/calendar', title: marker('Team calendar'), data: roles('SUPERVISOR'), loadComponent: () => import('./absence/team-calendar').then((m) => m.TeamCalendar) },

      { path: 'travel', title: marker('Travel'), data: roles('EMPLOYEE'), loadComponent: () => import('./travel/travel-list').then((m) => m.TravelList) },
      { path: 'travel/new', title: marker('New travel request'), data: roles('EMPLOYEE'), loadComponent: () => import('./travel/travel-form').then((m) => m.TravelForm) },
      { path: 'travel/:id/edit', title: marker('Edit travel request'), data: roles('EMPLOYEE'), loadComponent: () => import('./travel/travel-form').then((m) => m.TravelForm) },
      { path: 'travel/:id', title: marker('Travel request'), loadComponent: () => import('./travel/travel-detail').then((m) => m.TravelDetail) },

      { path: 'time', title: marker('Working time'), data: roles('EMPLOYEE'), loadComponent: () => import('./time/time-today').then((m) => m.TimeToday) },
      { path: 'time/month', title: marker('Monthly overview'), data: roles('EMPLOYEE'), loadComponent: () => import('./time/time-month').then((m) => m.TimeMonth) },
      { path: 'time/corrections/:id', title: marker('Time correction'), loadComponent: () => import('./time/correction-detail').then((m) => m.CorrectionDetail) },

      { path: 'tasks', title: marker('My tasks'), loadComponent: () => import('./tasks/task-inbox').then((m) => m.TaskInbox) },
      { path: 'delegations', title: marker('Delegations'), loadComponent: () => import('./tasks/delegations').then((m) => m.Delegations) },
      { path: 'notifications', title: marker('Notifications'), loadComponent: () => import('./notifications/notifications').then((m) => m.Notifications) },

      { path: 'reports', title: marker('Reports'), data: roles(...REPORT_READERS), loadComponent: () => import('./admin/reports').then((m) => m.Reports) },
      { path: 'admin/month-closing', title: marker('Month closing'), data: roles('TIME_ADMIN', 'HR_ADMIN'), loadComponent: () => import('./admin/month-closing').then((m) => m.MonthClosing) },
      { path: 'admin/batch', title: marker('Batch jobs'), data: roles(...OPERATORS, 'AUDITOR'), loadComponent: () => import('./admin/batch-jobs').then((m) => m.BatchJobs) },
      { path: 'admin/integrations', title: marker('Integration monitor'), data: roles(...OPERATORS, 'AUDITOR'), loadComponent: () => import('./admin/integration-monitor').then((m) => m.IntegrationMonitor) },
      { path: 'admin/audit', title: marker('Audit log'), data: roles(...OPERATORS, 'AUDITOR'), loadComponent: () => import('./admin/audit-log').then((m) => m.AuditLog) },
    ],
  },
  { path: '**', redirectTo: '' },
];
