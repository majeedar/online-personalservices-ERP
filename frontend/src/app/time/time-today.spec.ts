import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TimeToday } from './time-today';

describe('TimeToday', () => {
  let fixture: ComponentFixture<TimeToday>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const account = {
    date: '2026-09-21', targetMinutes: 480, workedMinutes: 0, breakMinutes: 0, absenceMinutes: 0, creditedMinutes: 0,
    balanceMinutes: -480, incomplete: false, statutoryBreakApplied: false, status: 'OPEN', future: false,
  };

  function flushLoad(state: 'OFF' | 'WORKING', allowed: string[]): void {
    http.expectOne('/api/v1/time/today').flush({ date: '2026-09-21', state, allowedActions: allowed, entries: [], account });
    http.expectOne('/api/v1/time/balance').flush({ monthBalanceMinutes: 30, yearBalanceMinutes: 90 });
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text)) as HTMLButtonElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimeToday],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TimeToday);
    el = fixture.nativeElement;
  });

  afterEach(() => fixture.destroy());

  it('only allows clocking in before the working day starts', async () => {
    flushLoad('OFF', ['CLOCK_IN']);
    await fixture.whenStable();

    expect(button('Clock in').disabled).toBe(false);
    expect(button('Clock out').disabled).toBe(true);
    expect(button('Start break').disabled).toBe(true);
    expect(button('End break').disabled).toBe(true);
    expect(el.textContent).toContain('Not clocked in');
  });

  it('allows a break or clocking out while working', async () => {
    flushLoad('WORKING', ['BREAK_START', 'CLOCK_OUT']);
    await fixture.whenStable();

    expect(button('Clock in').disabled).toBe(true);
    expect(button('Clock out').disabled).toBe(false);
    expect(button('Start break').disabled).toBe(false);
    expect(el.textContent).toContain('+0:30 h');
  });
});
