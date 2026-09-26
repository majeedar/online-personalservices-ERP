import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/**
 * The native adapter parses typed dates with Date.parse, which ignores the locale:
 * "1.3.2027" would be invalid. This one also accepts the German d.m.yyyy form.
 */
@Injectable()
export class LocaleDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const german = /^\s*(\d{1,2})\.(\d{1,2})\.(\d{4})\s*$/.exec(value);
      if (german) {
        const [day, month, year] = [Number(german[1]), Number(german[2]), Number(german[3])];
        const date = new Date(year, month - 1, day);
        return date.getMonth() === month - 1 ? date : this.invalid();
      }
    }
    return super.parse(value, parseFormat);
  }
}
