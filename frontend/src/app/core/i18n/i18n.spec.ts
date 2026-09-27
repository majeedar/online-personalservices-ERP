import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describeError } from '../api/api-error';
import { formatDays, halfDaySuffix, humanize } from '../format';
import de from './de.json';
import { acceptLanguage, language, setLanguage, tr } from './i18n';
import { I18N_PIPES } from './pipes';

@Component({
  imports: [I18N_PIPES],
  template: `<h1>{{ 'Working time' | tr }}</h1>
    <p>{{ '2027-03-01' | ldate: 'longDate' }}</p>
    <p>{{ 12.5 | lcurrency: 'EUR' }}</p>`,
})
class Host {}

describe('i18n', () => {
  afterEach(() => setLanguage('en'));

  it('uses English source texts as keys and fallback', () => {
    setLanguage('en');
    expect(tr('Working time')).toBe('Working time');
    expect(tr('Page {page}', { page: 3 })).toBe('Page 3');
    expect(tr('A text nobody translated')).toBe('A text nobody translated');
  });

  it('translates texts, placeholders, enum labels and day counts to German', () => {
    setLanguage('de');
    expect(language()).toBe('de');
    expect(tr('Working time')).toBe('Arbeitszeit');
    expect(tr('Page {page}', { page: 3 })).toBe('Seite 3');
    expect(humanize('IN_APPROVAL')).toBe('In Genehmigung');
    expect(humanize('SOME_UNKNOWN_CODE')).toBe('Some unknown code');
    expect(formatDays(1.5)).toBe('1,5 Tage');
    expect(formatDays(1)).toBe('1 Tag');
    expect(halfDaySuffix('MORNING')).toBe(' (vormittags)');
  });

  it('shows server messages as sent (the API answers in the chosen language) with a translated reference', () => {
    setLanguage('de');
    const overlap = new HttpErrorResponse({
      status: 409,
      error: {
        code: 'ABSENCE_OVERLAP',
        message: 'Die beantragte Abwesenheit überschneidet sich mit einem bestehenden Antrag.',
        correlationId: 'abc',
      },
    });
    expect(describeError(overlap)).toBe(
      'Die beantragte Abwesenheit überschneidet sich mit einem bestehenden Antrag. (Ref.: abc)',
    );
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Server ist nicht erreichbar');
  });

  it('sends the interface language to the API', () => {
    setLanguage('de');
    expect(acceptLanguage()).toBe('de');
    setLanguage('pseudo');
    expect(acceptLanguage()).toBe('qps-ploc');
  });

  it('re-renders templates when the language changes', async () => {
    setLanguage('en');
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent).toBe('Working time');
    expect(el.textContent).toContain('March 1, 2027');

    setLanguage('de');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('h1')?.textContent).toBe('Arbeitszeit');
    expect(el.textContent).toContain('1. März 2027');
    expect(el.textContent).toContain('12,50');
    expect(document.documentElement.lang).toBe('de');
  });

  it('brackets every dictionary text in the test language and leaves untranslated text plain', () => {
    setLanguage('pseudo');
    expect(tr('Working time')).toBe('⟦Wórkíñg tímé⟧');
    expect(tr('Page {page}', { page: 3 })).toBe('⟦Págé 3⟧');
    expect(humanize('IN_APPROVAL')).toBe('⟦Íñ Géñéhmígúñg⟧');
    expect(tr('A text nobody translated')).toBe('A text nobody translated');
  });

  it('has no empty German translations', () => {
    const empty = Object.entries(de as Record<string, string>).filter(([, v]) => !v.trim());
    expect(empty).toEqual([]);
  });
});
