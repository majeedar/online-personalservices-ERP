import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { MonthClosing } from './month-closing';

describe('MonthClosing', () => {
  let fixture: ComponentFixture<MonthClosing>;
  let http: HttpTestingController;
  let el: HTMLElement;
  let dialogResult: { comment?: string } | undefined;

  const months = [
    { month: '2026-09', closed: false, employees: 0, pendingCorrections: 0, closable: false, blockedReason: 'Only past months can be closed.' },
    { month: '2026-08', closed: false, employees: 0, pendingCorrections: 0, closable: true },
    { month: '2026-07', closed: false, employees: 0, pendingCorrections: 2, closable: false, blockedReason: '2 time correction(s) in 2026-07 are still waiting for a decision.' },
    { month: '2026-06', closed: true, closedAt: '2026-07-10T02:00:00Z', closedBy: 'scheduler', employees: 18, pendingCorrections: 0, closable: false },
  ];

  const tick = () => new Promise((r) => setTimeout(r));

  beforeEach(async () => {
    dialogResult = {};
    await TestBed.configureTestingModule({
      imports: [MonthClosing],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogResult) }) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MonthClosing);
    el = fixture.nativeElement;
    http.expectOne('/api/v1/time/closings').flush(months);
    await tick();
    await fixture.whenStable();
  });

  function buttons(text: string): HTMLButtonElement[] {
    return Array.from(el.querySelectorAll('button')).filter((b) => b.textContent?.includes(text)) as HTMLButtonElement[];
  }

  it('offers closing only for closable months and explains blocked ones', () => {
    expect(buttons('Close month')).toHaveLength(1);
    expect(buttons('Reopen')).toHaveLength(1);
    expect(el.textContent).toContain('still waiting for a decision');
    expect(el.textContent).toContain('by scheduler');
  });

  it('closes a month after confirmation and reloads', async () => {
    buttons('Close month')[0].click();
    await tick();
    http.expectOne('/api/v1/time/closings/2026-08/close').flush({ month: '2026-08', status: 'CLOSED' });
    await tick();
    http.expectOne('/api/v1/time/closings').flush(months);
    await tick();
    await fixture.whenStable();
    expect(el.textContent).toContain('2026-08 closed.');
  });

  it('sends the reason when reopening', async () => {
    dialogResult = { comment: 'Late sick note' };
    buttons('Reopen')[0].click();
    await tick();
    const reopen = http.expectOne('/api/v1/time/closings/2026-06/reopen');
    expect(reopen.request.body).toEqual({ reason: 'Late sick note' });
    reopen.flush({});
    await tick();
    http.expectOne('/api/v1/time/closings').flush(months);
  });
});
