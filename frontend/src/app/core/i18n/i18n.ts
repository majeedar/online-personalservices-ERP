import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import { signal } from '@angular/core';
import de from './de.json';

/**
 * German / English interface (ADR-019). English source texts are the keys and the
 * fallback, so a missing translation shows English instead of a broken key.
 * The language is a signal: templates re-render on switch, no reload needed.
 */
export type Language = 'en' | 'de';

export const LANGUAGES: { code: Language; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'de', label: 'Deutsch' },
];

const STORAGE_KEY = 'ops.language';
const DICTIONARIES: Record<Language, Record<string, string>> = { en: {}, de };

registerLocaleData(localeDe);

function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'de') {
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

/** The current language (a signal read, so callers re-render on switch). */
export function language(): Language {
  return current();
}

/** Locale for dates, numbers and currencies. */
export function locale(): string {
  return current() === 'de' ? 'de-DE' : 'en-US';
}

export function setLanguage(lang: Language): void {
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
