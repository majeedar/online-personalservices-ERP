import { expect, Page, test, TestInfo } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

/**
 * Browser sweep for untranslated text (ADR-019). The app runs in the test language
 * `pseudo`, where every dictionary text is shown as `⟦Wórkíñg tímé⟧` and formatted
 * dates and amounts are bracketed too. Readable plain text left on a page never went
 * through the dictionary, so a German user would see it in English.
 *
 * - The API answers in the test language too (Accept-Language: qps-ploc), so texts
 *   the backend writes (notifications, tasks, errors, reports) are checked as well.
 * - Plain text in elements marked `data-i18n-source="external"` (messages from the
 *   ERP systems) or `"master-data"` (names stored as entered, e.g. funding sources)
 *   is known and only reported.
 * - Demo data (names, usernames, IDs, cities) is removed with the patterns in
 *   e2e/i18n-baseline.json.
 * - Any other plain text fails the test: translate it with `tr`, or mark where it
 *   comes from. I18N_SWEEP_REPORT=1 writes the report without failing.
 * The report is written to test-results/i18n-leaks.json and attached to the run.
 */

// Playwright runs from the frontend folder (playwright.config.ts).
const here = resolve('e2e');
const baseline = JSON.parse(readFileSync(join(here, 'i18n-baseline.json'), 'utf8')) as {
  data: string[];
};
const dataPatterns = baseline.data.map((p) => new RegExp(p, 'gu'));
const reportOnly = process.env['I18N_SWEEP_REPORT'] === '1';

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
/** Same transformation as pseudo() in src/app/core/i18n/i18n.ts, to find buttons by label. */
const pseudo = (text: string) => `⟦${text.replace(/[a-zA-Z]/g, (c) => ACCENTS[c] ?? c)}⟧`;

type Finding = { page: string; text: string; source?: string };
const untranslated: Finding[] = [];
const sourced: Finding[] = [];

async function login(page: Page, username: string): Promise<void> {
  await page.addInitScript(() => localStorage.setItem('ops.language', 'pseudo'));
  await page.goto('/login');
  await settle(page);
  await collect(page, 'login');
  await page.locator('input[formcontrolname=username]').fill(username);
  await page.locator('input[formcontrolname=password]').fill('demo123');
  await page.locator('button[type=submit]').click();
  await expect(page).toHaveURL(/dashboard/);
}

async function settle(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
  await expect(page.locator('mat-progress-bar')).toHaveCount(0, { timeout: 15_000 });
}

/** Text left after removing translated parts and demo data, normalised (digits as #). */
function plainText(raw: string): string {
  let text = raw;
  for (let prev = ''; prev !== text;) {
    prev = text;
    text = text.replace(/⟦[^⟦⟧]*⟧/g, ' ');
  }
  text = text.replace(/[⟦⟧]/g, ' ').replace(/\s+/g, ' ').trim();
  const tidy = (s: string) =>
    s
      .replace(/\d+/g, '#')
      .replace(/\s+/g, ' ')
      .replace(/^[\s·,:;–→()#.%/+-]+|[\s·,:;–→(#.%/+-]+$/gu, '')
      .trim();
  // Twice: whole-text patterns (e.g. a unit code) match once the rest ("ADM – Finance") is gone.
  for (let pass = 0; pass < 2; pass++) {
    for (const p of dataPatterns) text = text.replace(p, ' ');
    text = tidy(text);
  }
  return text;
}

/** Visible text nodes and user-facing attributes of the page, with their marked source. */
async function collect(page: Page, name: string): Promise<void> {
  const texts = await page.evaluate(() => {
    const out: { text: string; source?: string }[] = [];
    const skip = (el: Element) =>
      !!el.closest('mat-icon, .material-icons, code, script, style, noscript');
    const source = (el: Element) =>
      el.closest('[data-i18n-source]')?.getAttribute('data-i18n-source') ?? undefined;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let n = walker.nextNode(); n; n = walker.nextNode()) {
      const el = n.parentElement;
      if (el && !skip(el) && el.checkVisibility() && n.textContent?.trim()) {
        out.push({ text: n.textContent, source: source(el) });
      }
    }
    for (const el of Array.from(
      document.querySelectorAll('[aria-label], [placeholder], [title]'),
    )) {
      if (skip(el)) continue;
      for (const attr of ['aria-label', 'placeholder', 'title']) {
        const value = el.getAttribute(attr);
        if (value?.trim()) out.push({ text: value, source: source(el) });
      }
    }
    out.push({ text: document.title });
    return out;
  });
  for (const { text: raw, source } of texts) {
    const text = plainText(raw);
    if (!/\p{L}{2,}/u.test(text)) continue;
    const list = source ? sourced : untranslated;
    if (!list.some((l) => l.text === text)) list.push({ page: name, text, source });
  }
}

async function visit(page: Page, url: string, name = url): Promise<void> {
  await page.goto(url);
  await settle(page);
  await collect(page, name);
}

/** Opens the first link in the page's main table, if there is one. */
async function firstDetail(page: Page, listUrl: string, name: string): Promise<void> {
  await visit(page, listUrl);
  const link = page.locator('main table tbody a').first();
  if (await link.count()) {
    await link.click();
    await settle(page);
    await collect(page, name);
  }
}

test.describe.configure({ mode: 'serial' });

test('employee pages', async ({ page }) => {
  await login(page, 'employee');
  for (const url of [
    '/dashboard',
    '/profile',
    '/absence',
    '/travel',
    '/travel/new',
    '/time',
    '/time/month',
    '/tasks',
    '/notifications',
  ]) {
    await visit(page, url);
  }
  await firstDetail(page, '/absence', 'absence detail');
  await firstDetail(page, '/travel', 'travel detail');

  // Absence form with a calculated preview and the date picker open.
  await visit(page, '/absence/new');
  await page.locator('input[matstartdate]').fill('1.3.2027');
  await page.locator('input[matenddate]').fill('3.3.2027');
  await page.locator('input[matenddate]').blur();
  await expect(page.locator('main table tbody tr')).toHaveCount(3, { timeout: 10_000 });
  await collect(page, 'absence form preview');
  await page.locator('mat-datepicker-toggle button').click();
  await expect(page.locator('mat-calendar')).toBeVisible();
  await collect(page, 'date picker');
  await page.keyboard.press('Escape');

  // Correction dialog on the monthly overview.
  await visit(page, '/time/month');
  const correct = page.getByRole('button', { name: pseudo('Correct') }).first();
  if (await correct.count()) {
    await correct.click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await collect(page, 'correction dialog');
    await page.keyboard.press('Escape');
  }
});

test('supervisor pages', async ({ page }) => {
  await login(page, 'supervisor');
  for (const url of ['/dashboard', '/team/calendar', '/delegations']) {
    await visit(page, url);
  }
  await firstDetail(page, '/tasks', 'task detail');
  const approve = page.getByRole('button', { name: pseudo('Approve') }).first();
  if (await approve.count()) {
    await approve.click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await collect(page, 'decision dialog');
    await page.keyboard.press('Escape');
  }
});

test('administration pages', async ({ page }) => {
  await login(page, 'hradmin');
  await visit(page, '/reports');
  const report = page.locator('main mat-nav-list a').first();
  if (await report.count()) {
    await report.click();
    await settle(page);
    await collect(page, 'report');
  }
  await visit(page, '/admin/month-closing');
});

test('operations pages', async ({ page }) => {
  await login(page, 'erpadmin');
  for (const url of ['/dashboard', '/admin/batch', '/admin/integrations', '/admin/audit']) {
    await visit(page, url);
  }
});

test.afterAll(async ({}, testInfo: TestInfo) => {
  const report = { untranslated, knownServerAndMasterData: sourced };
  const out = join(here, '..', 'test-results', 'i18n-leaks.json');
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, JSON.stringify(report, null, 2));
  await testInfo.attach('i18n-leaks', {
    body: JSON.stringify(report, null, 2),
    contentType: 'application/json',
  });
  const bySource = (s: string) => sourced.filter((l) => l.source === s).length;
  console.log(
    `i18n sweep: ${untranslated.length} untranslated; known: ${bySource('external')} from external systems, ` +
      `${bySource('master-data')} from master data`,
  );
  for (const l of untranslated)
    console.log(`  untranslated on ${l.page}: ${JSON.stringify(l.text)}`);
  if (!reportOnly) {
    expect(
      untranslated,
      'text shown without translation: use tr, or mark data-i18n-source',
    ).toEqual([]);
  }
});
