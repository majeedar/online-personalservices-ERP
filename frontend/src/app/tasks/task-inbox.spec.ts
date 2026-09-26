import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TaskInbox } from './task-inbox';

describe('TaskInbox', () => {
  let fixture: ComponentFixture<TaskInbox>;
  let http: HttpTestingController;
  let el: HTMLElement;
  let dialogResult: { comment?: string } | undefined;

  const task = {
    id: 'task-1',
    title: 'Approve annual leave – Erika Mustermann',
    description: 'Erika Mustermann: Annual leave, 15.02.2027 – 19.02.2027 (5 working day(s))',
    dueDate: '2099-01-01',
    status: 'OPEN',
    createdAt: '2026-09-21T06:00:00Z',
    definitionCode: 'ABSENCE_APPROVAL',
    businessObjectType: 'AbsenceRequest',
    businessObjectId: 'req-1',
    stepType: 'SUPERVISOR_APPROVAL',
    approvalType: 'ABSENCE',
    assignedEmployeeId: 'sup-1',
    assignedEmployeeName: 'Stefan Beispiel',
    requesterId: 'emp-1',
    requesterName: 'Erika Mustermann',
  };

  beforeEach(async () => {
    dialogResult = {};
    await TestBed.configureTestingModule({
      imports: [TaskInbox],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogResult) }) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskInbox);
    el = fixture.nativeElement;
    http.expectOne('/api/v1/tasks').flush([task]);
    await fixture.whenStable();
  });

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;
  }

  it('lists open tasks with a link to the request', () => {
    const link = el.querySelector('a[href="/absence/req-1"]');
    expect(link?.textContent).toContain('Approve annual leave');
    expect(el.textContent).toContain('Erika Mustermann');
  });

  it('approves a task after confirmation and reloads the inbox', async () => {
    button('Approve').click();
    await new Promise((r) => setTimeout(r));

    const complete = http.expectOne('/api/v1/tasks/task-1/complete');
    expect(complete.request.body.decision).toBe('APPROVE');
    complete.flush({ instanceId: 'wf-1', instanceStatus: 'APPROVED' });
    await new Promise((r) => setTimeout(r));
    http.expectOne('/api/v1/tasks').flush([]);
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();

    expect(el.textContent).toContain('Nothing to do');
  });

  it('does nothing when the confirmation is cancelled', async () => {
    dialogResult = undefined;
    button('Reject').click();
    await new Promise((r) => setTimeout(r));
    http.expectNone('/api/v1/tasks/task-1/complete');
  });
});
