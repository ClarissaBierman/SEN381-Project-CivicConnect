import { Browser, expect, Page } from '@playwright/test';

// Dummy accounts from database/civicconnect_supabase_build.sql (development data only).
const SEED_PASSWORD = process.env['E2E_PASSWORD'] ?? 'Password123!';

export const ACCOUNTS = {
  thabo: { email: 'thabo.mokoena@example.com', password: SEED_PASSWORD }, // requester
  naledi: { email: 'naledi.khumalo@example.com', password: SEED_PASSWORD }, // requester
  ayesha: { email: 'ayesha.patel@civicconnect.example', password: SEED_PASSWORD }, // staff: IT Support
  lerato: { email: 'lerato.nkosi@civicconnect.example', password: SEED_PASSWORD }, // manager
};

export type Account = { email: string; password: string };

/** A fresh browser session (own storage), logged in as the account. */
export async function loginAs(browser: Browser, account: Account): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/login');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).not.toHaveURL(/\/login/);
  return page;
}

/** The logged-in user's API token, as the app keeps it. */
export async function tokenOf(page: Page): Promise<string> {
  const token = await page.evaluate(() => sessionStorage.getItem('cc.token'));
  expect(token, 'logged in').toBeTruthy();
  return token!;
}

/** Text that is unique to this test run, so repeated runs do not interfere. */
export function unique(label: string): string {
  return `${label} ${Date.now().toString(36)}`;
}
