import { TestBed } from '@angular/core/testing';
import { DateAdapter, provideNativeDateAdapter } from '@angular/material/core';
import { LocaleDateAdapter } from './date-adapter';

describe('LocaleDateAdapter', () => {
  let adapter: DateAdapter<Date>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideNativeDateAdapter(),
        { provide: DateAdapter, useClass: LocaleDateAdapter },
      ],
    });
    adapter = TestBed.inject(DateAdapter);
  });

  it('parses dates typed the German way', () => {
    const date = adapter.parse('1.3.2027', null)!;
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2027, 2, 1]);
  });

  it('rejects impossible German dates and still parses US input', () => {
    expect(adapter.isValid(adapter.parse('31.2.2027', null)!)).toBe(false);
    const us = adapter.parse('3/1/2027', null)!;
    expect([us.getMonth(), us.getDate()]).toEqual([2, 1]);
  });
});
