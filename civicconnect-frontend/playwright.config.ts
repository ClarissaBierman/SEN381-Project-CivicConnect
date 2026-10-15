import { defineConfig, devices } from '@playwright/test';

/*
 * End-to-end / system tests (M3): real browser -> Angular -> Spring Boot API -> PostgreSQL.
 * Needs the whole stack running with the dummy data from database/civicconnect_supabase_build.sql.
 *   E2E_BASE_URL  where the app is served (default: ng serve on http://localhost:4200)
 *   E2E_CHROMIUM  optional path to an already installed Chromium
 * Run: npm run e2e
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // The tests share one database, so they run one at a time
  workers: 1,
  fullyParallel: false,
  // A failing system test is investigated, never rerun until it passes
  retries: 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['junit', { outputFile: 'test-results/e2e-junit.xml' }],
  ],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env['E2E_CHROMIUM'] ? { executablePath: process.env['E2E_CHROMIUM'] } : {},
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
