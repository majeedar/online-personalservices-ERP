import { expect, Page, test } from '@playwright/test';

/**
 * Browser walkthrough of the main demo scenarios (docs/demo-script.md).
 * Fails on unexpected console errors. Writes screenshots to docs/screenshots.
 */

const SHOTS = '../docs/screenshots';

function watchConsole(page: Page): string[] {
  const errors: string[] = [];
  page.on('console', (msg) => {
    // Expected HTTP errors (e.g. the 401 session probe on the login page) are logged by the browser.
    if (msg.type() === 'error' && !/Failed to load resource/.test(msg.text())) {
      errors.push(msg.text());
    }
  });
  page.on('pageerror', (e) => errors.push(e.message));
  return errors;
}

async function login(page: Page, username: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('demo123');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/dashboard/);
}

function nav(page: Page, name: string) {
  return page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name, exact: true });
}

async function logout(page: Page): Promise<void> {
  await page.locator('.user-button').click();
  await page.getByRole('menuitem', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/login/);
}

/** A Monday–Wednesday in 2027 that differs between runs, so re-runs do not overlap. */
function leaveWeek(): { start: string; end: string } {
  const base = new Date(2027, 1, 1); // 1 Feb 2027 is a Monday
  const week = Math.floor(Date.now() / 60_000) % 30;
  const start = new Date(base.getTime() + week * 7 * 86_400_000);
  const end = new Date(start.getTime() + 2 * 86_400_000);
  const fmt = (d: Date) => `${d.getMonth() + 1}/${d.getDate()}/${d.getFullYear()}`;
  return { start: fmt(start), end: fmt(end) };
}

test('employee requests leave, supervisor approves (Scenario 1)', async ({ page }) => {
  const errors = watchConsole(page);
  await login(page, 'employee');
  await expect(page.getByText('Remaining leave')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/dashboard-employee.png`, fullPage: true });

  await nav(page, 'Absence').click();
  await page.getByRole('link', { name: 'New request' }).click();
  const { start, end } = leaveWeek();
  await page.getByPlaceholder('Start date').fill(start);
  await page.getByPlaceholder('End date').fill(end);
  await expect(page.getByText('Calculated working days')).toBeVisible();
  await expect(page.locator('.figures dd').first()).toHaveText('3');
  await page.screenshot({ path: `${SHOTS}/absence-form.png`, fullPage: true });
  await page.getByRole('button', { name: 'Save and submit' }).click();
  await expect(page.getByText('In approval').first()).toBeVisible();
  const requestUrl = page.url();
  await logout(page);

  await login(page, 'supervisor');
  await nav(page, 'My Tasks').click();
  await expect(page.getByRole('link', { name: /Approve annual leave – Erika Mustermann/ }).first()).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/tasks-supervisor.png`, fullPage: true });
  await page.goto(requestUrl);
  await page.getByRole('button', { name: 'Approve' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click();
  await expect(page.locator('ops-status').filter({ hasText: 'Approved' }).first()).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/absence-approved.png`, fullPage: true });

  await nav(page, 'Team Calendar').click();
  await expect(page.getByRole('heading', { name: 'Team calendar' })).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/team-calendar.png`, fullPage: true });
  expect(errors).toEqual([]);
});

test('part-time employee sees only scheduled working days (Scenario 2)', async ({ page }) => {
  const errors = watchConsole(page);
  await login(page, 'parttime');
  await page.goto('/absence/new');
  // Thu 4 Mar – Mon 8 Mar 2027: Friday is not a working day for 'parttime'.
  await page.getByPlaceholder('Start date').fill('3/4/2027');
  await page.getByPlaceholder('End date').fill('3/8/2027');
  await expect(page.locator('.figures dd').first()).toHaveText('2');
  await expect(page.getByText('Non working day').first()).toBeVisible();
  expect(errors).toEqual([]);
});

test('employee records working time and requests travel (Scenarios 3 and 5)', async ({ page }) => {
  const errors = watchConsole(page);
  await login(page, 'employee');

  // Read button states only after today's state has arrived and been rendered.
  const todayLoaded = page.waitForResponse((r) => r.url().includes('/api/v1/time/today') && r.ok());
  await nav(page, 'Working Time').click();
  await todayLoaded;
  await expect(page.getByRole('heading', { name: 'Working time' })).toBeVisible();
  // Once loaded, exactly the valid next action(s) are enabled (Clock in, End break or Clock out).
  await expect(page.locator('.clock-buttons button:enabled').first()).toBeVisible();
  const clockIn = page.getByRole('button', { name: 'Clock in' });
  if (await clockIn.isEnabled()) {
    await expect(page.getByRole('button', { name: 'Clock out' })).toBeDisabled();
    await clockIn.click();
    await expect(page.locator('.state')).toContainText('Working');
  }
  await expect(page.getByRole('button', { name: 'Clock in' })).toBeDisabled();
  await page.screenshot({ path: `${SHOTS}/time-today.png`, fullPage: true });
  await page.getByRole('link', { name: 'Monthly overview' }).click();
  await expect(page.getByText('Daily accounts')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/time-month.png`, fullPage: true });

  await nav(page, 'Travel').click();
  await page.getByRole('link', { name: 'New travel request' }).click();
  await page.getByLabel('Purpose').fill('Conference talk on research software');
  await page.getByLabel('Destination city').fill('Heidelberg');
  await page.getByLabel('Start').fill('2027-01-18T08:00');
  await page.getByLabel('End').fill('2027-01-19T19:00');
  await page.getByLabel('Estimated cost').fill('380');
  await page.getByLabel('Cost centre').fill('CC-2200');
  await page.screenshot({ path: `${SHOTS}/travel-form.png`, fullPage: true });
  await page.getByRole('button', { name: 'Save and submit' }).click();
  await expect(page.getByText('In approval').first()).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/travel-detail.png`, fullPage: true });
  expect(errors).toEqual([]);
});

test('ERP admin monitors integrations and runs a batch job (Scenario 7)', async ({ page }) => {
  const errors = watchConsole(page);
  await login(page, 'erpadmin');
  await expect(page.getByText('System health')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/dashboard-admin.png`, fullPage: true });

  await nav(page, 'Batch Jobs').click();
  await page.getByRole('row', { name: /organisation-sync/ }).getByRole('button', { name: 'Run now' }).click();
  await expect(page.getByRole('status')).toContainText('organisation-sync');
  await page.screenshot({ path: `${SHOTS}/batch-jobs.png`, fullPage: true });

  await nav(page, 'Integration Monitor').click();
  await expect(page.getByText('Finance ERP')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/integration-monitor.png`, fullPage: true });

  await nav(page, 'Audit').click();
  await expect(page.getByText('Append-only')).toBeVisible();
  expect(errors).toEqual([]);
});

test('HR admin runs a report', async ({ page }) => {
  const errors = watchConsole(page);
  await login(page, 'hradmin');
  await nav(page, 'Reports').click();
  await page.getByRole('link', { name: 'Leave usage by organisational unit' }).click();
  await expect(page.getByRole('table')).toContainText('Unit');
  await page.screenshot({ path: `${SHOTS}/reports.png`, fullPage: true });
  expect(errors).toEqual([]);
});
