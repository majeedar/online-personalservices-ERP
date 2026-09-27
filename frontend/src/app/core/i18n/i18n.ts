import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import { signal } from '@angular/core';
import de from './de.json';

/**
 * German / English interface (ADR-019). English source texts are the keys and the
 * fallback, so a missing translation shows English instead of a broken key.
 * The language is a signal: templates re-render on switch, no reload needed.
 */
export type Language = 'en' | 'de' | 'pseudo';

/** Languages offered in the menu. `pseudo` is a test language (see {@link pseudo}), set only by tests. */
export const LANGUAGES: { code: Language; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'de', label: 'Deutsch' },
];

const STORAGE_KEY = 'ops.language';
const DICTIONARIES: Record<Language, Record<string, string>> = { en: {}, de, pseudo: {} };

const ACCENTS: Record<string, string> = {
  a: 'á',
  e: 'é',
  i: 'í',
  o: 'ó',
  u: 'ú',
  n: 'ñ',
  c: 'ç',
  y: 'ý',
  A: 'Á',
  E: 'É',
  I: 'Í',
  O: 'Ó',
  U: 'Ú',
  N: 'Ñ',
  C: 'Ç',
  Y: 'Ý',
};

/**
 * Test language: every translatable text becomes `⟦Wórkíñg tímé⟧`, so text that
 * reaches the screen without passing through the dictionary stays plain and can be
 * found by the browser sweep (e2e/i18n-sweep.spec.ts). Placeholders are kept.
 */
export function pseudo(text: string): string {
  const accented = text
    .split(/(\{\w+\})/)
    .map((part) =>
      part.startsWith('{') ? part : part.replace(/[a-zA-Z]/g, (c) => ACCENTS[c] ?? c),
    )
    .join('');
  return `⟦${accented}⟧`;
}

/** Brackets formatted values (dates, amounts) in the test language. */
export function markFormatted(value: string): string {
  return current() === 'pseudo' ? `⟦${value}⟧` : value;
}

function pseudoDictionary(): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [key, german] of Object.entries(de as Record<string, string>)) {
    // Dynamic keys have no English source text; bracket what German users would see.
    result[key] = pseudo(key.startsWith('enum.') || key.startsWith('error.') ? german : key);
  }
  return result;
}

registerLocaleData(localeDe);

function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'de' || stored === 'pseudo') {
      return stored;
    }
  } catch {
    // storage blocked: fall back to the browser language
  }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('de')
    ? 'de'
    : 'en';
}

const current = signal<Language>(initialLanguage());
if (current() === 'pseudo') {
  DICTIONARIES.pseudo = pseudoDictionary();
}

/** The current language (a signal read, so callers re-render on switch). */
export function language(): Language {
  return current();
}

/** Locale for dates, numbers and currencies. */
export function locale(): string {
  // The test language formats like German, so the sweep sees what German users see.
  return current() === 'en' ? 'en-US' : 'de-DE';
}

export function setLanguage(lang: Language): void {
  if (lang === 'pseudo' && !Object.keys(DICTIONARIES.pseudo).length) {
    DICTIONARIES.pseudo = pseudoDictionary();
  }
  current.set(lang);
  try {
    localStorage.setItem(STORAGE_KEY, lang);
  } catch {
    // not persisted; the choice still applies to this page
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = lang;
  }
}

/**
 * Translates an English source text. Placeholders: `tr('Hello {name}', { name })`.
 */
export function tr(
  text: string,
  params?: Record<string, string | number | null | undefined>,
): string {
  const translated = DICTIONARIES[current()][text] ?? text;
  if (!params) {
    return translated;
  }
  return translated.replace(/\{(\w+)\}/g, (match, key: string) => {
    const value = params[key];
    return value === undefined || value === null ? match : String(value);
  });
}

/** True if the current language has its own text for this key. */
export function hasTranslation(key: string): boolean {
  return key in DICTIONARIES[current()];
}

if (typeof document !== 'undefined') {
  document.documentElement.lang = current();
}

/** Marks a text for translation where it is defined; `tr` translates it where it is shown. */
export function marker<T extends string>(text: T): T {
  return text;
}

/** Accept-Language for API calls, so server texts come in the same language (ADR-020). */
export function acceptLanguage(): string {
  return current() === 'pseudo' ? 'qps-ploc' : current();
}

/** True if a language was chosen in this browser (it then wins over the profile's). */
export function hasStoredChoice(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== null;
  } catch {
    return false;
  }
}
