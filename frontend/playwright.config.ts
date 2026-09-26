import { defineConfig } from '@playwright/test';

/**
 * End-to-end smoke tests of the demo scenarios against a running stack:
 *   docker compose up --build            (E2E_BASE_URL defaults to http://localhost:4200)
 * or backend (mvn spring-boot:test-run) + frontend (npm start).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    viewport: { width: 1360, height: 860 },
    locale: 'en-US',
    trace: 'retain-on-failure',
  },
});
