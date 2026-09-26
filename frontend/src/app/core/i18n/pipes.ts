import { formatCurrency, formatDate, getCurrencySymbol } from '@angular/common';
import { Pipe, PipeTransform } from '@angular/core';
import { locale, markFormatted, tr } from './i18n';

/*
 * The pipes are impure on purpose: they read the language signal, so a switch
 * re-renders every template that uses them. The work per call is a map lookup.
 */

/** `{{ 'Working time' | tr }}`, `{{ 'Hello {name}' | tr: { name } }}` */
@Pipe({ name: 'tr', pure: false })
export class TranslatePipe implements PipeTransform {
  transform(text: string, params?: Record<string, string | number | null | undefined>): string {
    return tr(text, params);
  }
}

/** Like `date`, in the current locale. */
@Pipe({ name: 'ldate', pure: false })
export class LocalDatePipe implements PipeTransform {
  transform(
    value: string | number | Date | null | undefined,
    format = 'mediumDate',
  ): string | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    return markFormatted(formatDate(value, format, locale()));
  }
}

/** Like `currency`, in the current locale. */
@Pipe({ name: 'lcurrency', pure: false })
export class LocalCurrencyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency = 'EUR'): string | null {
    if (value === null || value === undefined) {
      return null;
    }
    return markFormatted(
      formatCurrency(value, locale(), getCurrencySymbol(currency, 'narrow', locale()), currency),
    );
  }
}

export const I18N_PIPES = [TranslatePipe, LocalDatePipe, LocalCurrencyPipe] as const;
