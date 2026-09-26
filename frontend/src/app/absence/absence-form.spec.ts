import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AbsenceForm } from './absence-form';

describe('AbsenceForm', () => {
  let fixture: ComponentFixture<AbsenceForm>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const leaveTypes = [
    { id: 'lt-1', code: 'ANNUAL_LEAVE', name: 'Annual leave', deductsEntitlement: true, requiresApproval: true, creditsWorkingTime: true, attachmentRequired: false },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AbsenceForm],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AbsenceForm);
    el = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/leave-types').flush(leaveTypes);
    await fixture.whenStable();
  });

  it('does not submit without a period and shows a validation message', async () => {
    const submit = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Save and submit'))!;
    submit.click();
    await fixture.whenStable();

    expect(el.textContent).toContain('Choose a start and end date.');
    http.expectNone('/api/v1/absences');
  });

  it('shows the server calculation with projected balance and rule violations', async () => {
    const component = fixture.componentInstance as unknown as { form: { patchValue: (v: object) => void } };
    component.form.patchValue({ start: new Date(2027, 2, 4), end: new Date(2027, 2, 8) });
    await new Promise((r) => setTimeout(r, 350));

    const preview = http.expectOne((r) => r.url === '/api/v1/absences/preview');
    expect(preview.request.body.startDate).toBe('2027-03-04');
    preview.flush({
      days: [
        { date: '2027-03-04', kind: 'WORKING_DAY', plannedMinutes: 480, creditedMinutes: 480, entitlementDeduction: 1 },
        { date: '2027-03-05', kind: 'NON_WORKING_DAY', plannedMinutes: 0, creditedMinutes: 0, entitlementDeduction: 0 },
      ],
      workingDays: 1,
      deduction: 1,
      currentBalance: 24,
      projectedBalance: 23,
      issues: [{ code: 'ABSENCE_OVERLAP', message: 'The requested absence overlaps with an existing request.' }],
    });
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();

    expect(el.textContent).toContain('Calculated working days');
    expect(el.textContent).toContain('23 days');
    expect(el.textContent).toContain('overlaps with an existing request');
  });

  it('sends a half day for a single-day request and shows it in the calculation', async () => {
    const component = fixture.componentInstance as unknown as { form: { patchValue: (v: object) => void } };
    component.form.patchValue({ start: new Date(2027, 2, 1), end: new Date(2027, 2, 1), startPart: 'AFTERNOON' });
    await new Promise((r) => setTimeout(r, 350));
    fixture.detectChanges();

    expect(el.textContent).toContain('Day');
    const preview = http.expectOne((r) => r.url === '/api/v1/absences/preview');
    expect(preview.request.body.startDayPart).toBe('AFTERNOON');
    expect(preview.request.body.endDayPart).toBe('AFTERNOON');
    preview.flush({
      days: [{ date: '2027-03-01', kind: 'WORKING_DAY', dayPart: 'AFTERNOON', plannedMinutes: 240, creditedMinutes: 240, entitlementDeduction: 0.5 }],
      workingDays: 0.5,
      deduction: 0.5,
      issues: [],
    });
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();

    expect(el.textContent).toContain('Working day (afternoon)');
  });

  it('drops a morning start when the period spans several days', async () => {
    const component = fixture.componentInstance as unknown as { form: { patchValue: (v: object) => void } };
    component.form.patchValue({ start: new Date(2027, 2, 1), end: new Date(2027, 2, 3), startPart: 'MORNING', endPart: 'MORNING' });
    await new Promise((r) => setTimeout(r, 350));

    const preview = http.expectOne((r) => r.url === '/api/v1/absences/preview');
    expect(preview.request.body.startDayPart).toBe('FULL');
    expect(preview.request.body.endDayPart).toBe('MORNING');
    preview.flush({ days: [], workingDays: 0, deduction: 0, issues: [] });
  });
});
