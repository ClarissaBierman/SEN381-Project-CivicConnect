import { expect, test } from '@playwright/test';
import { ACCOUNTS, loginAs, unique } from './support';

/*
 * E2E-03 - deactivating an account cuts off its open session at once (B1, NFR-003, FR-014).
 * Negative/failure test: the staff member is still logged in with an unexpired token when a manager
 * deactivates them. Expected: their next call is refused (401) and the app sends them to the login
 * page with "Your account is no longer active", and they can no longer log in.
 * The staff member is created by the test, so the dummy data is not changed.
 */
test('E2E-03: a deactivated staff member is logged out on their next action', async ({ browser }) => {
  const email = `${unique('e2e.staff').replace(' ', '.')}@civicconnect.example`;
  const password = 'TempPassword1!';

  // Manager creates the staff member (FR-005 Staff page)
  const manager = await loginAs(browser, ACCOUNTS.lerato);
  await manager.getByRole('link', { name: 'Staff' }).click();
  await manager.getByLabel('First name').fill('E2E');
  await manager.getByLabel('Surname').fill('Handler');
  await manager.getByLabel('Email').fill(email);
  await manager.getByLabel('Temporary password').fill(password);
  await manager.getByRole('button', { name: 'Add employee' }).click();
  await expect(manager.getByRole('heading', { name: 'E2E Handler' })).toBeVisible();

  // The new staff member logs in and is working
  const staff = await loginAs(browser, { email, password });
  await expect(staff.getByRole('heading', { name: 'Requests in my categories' })).toBeVisible();

  // Manager deactivates them while they are logged in
  await manager.getByRole('button', { name: 'Deactivate' }).click();
  await expect(manager.locator('.confirm-box')).toHaveText('Account deactivated.');

  // Their next action is refused and they land on the login page with the reason
  await staff.reload();
  await expect(staff).toHaveURL(/\/login\?deactivated=1/);
  await expect(staff.locator('.confirm-box')).toContainText('no longer active');

  // And they cannot log in again
  await staff.getByLabel('Email').fill(email);
  await staff.getByLabel('Password').fill(password);
  await staff.getByRole('button', { name: 'Log in' }).click();
  await expect(staff.locator('.error-box')).toBeVisible();
  await expect(staff).toHaveURL(/\/login/);
});
