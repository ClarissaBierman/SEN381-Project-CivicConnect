import { expect, test } from '@playwright/test';
import { ACCOUNTS, loginAs, tokenOf, unique } from './support';

/*
 * E2E-02 - object-level access control (ASR-001, NFR-003, R-10 / OWASP API1).
 * Negative test: a logged-in requester must not see another requester's request, even by calling the
 * API directly with their own valid token, and must not reach staff or management screens or endpoints.
 * Expected: 404 (the API does not reveal that the request exists), 403 for staff endpoints, and the
 * role guard sends the requester back to their own home screen.
 */
test('E2E-02: a requester cannot see another requester\'s request or use staff screens', async ({ browser }) => {
  // Thabo's request (created here, so the test does not depend on the seed IDs)
  const thabo = await loginAs(browser, ACCOUNTS.thabo);
  await thabo.getByLabel('Category *').selectOption({ label: 'Maintenance' });
  await thabo.getByLabel('Description *').fill(unique('E2E private request'));
  await thabo.getByLabel('Location *').fill('Block B');
  await thabo.getByRole('button', { name: 'Submit request' }).click();
  await expect(thabo.locator('.confirm-box')).toContainText('status: New');
  const ref = (await thabo.locator('.confirm-box').textContent())!.match(/CC-\d{4}-\d{6}/)![0];
  const thaboToken = await tokenOf(thabo);
  const mine = await thabo.request.get('/api/v1/requests/mine?size=1', {
    headers: { Authorization: `Bearer ${thaboToken}` },
  });
  const thabosRequestId: number = (await mine.json()).content[0].id;

  // Naledi does not see it in her own list
  const naledi = await loginAs(browser, ACCOUNTS.naledi);
  await naledi.goto('/requests/mine');
  await expect(naledi.getByRole('heading', { name: 'My requests' })).toBeVisible();
  await expect(naledi.getByRole('button', { name: ref })).toHaveCount(0);

  // Changing the ID in a direct API call does not work either: 404, not the data
  const token = await tokenOf(naledi);
  const other = await naledi.request.get(`/api/v1/requests/${thabosRequestId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(other.status()).toBe(404);
  const body = await other.json();
  expect(body.code).toBe('NOT_FOUND');
  expect(JSON.stringify(body)).not.toContain('Block B');

  // Staff endpoints refuse a requester's token (403), and the role guard keeps the screens hidden
  const staffList = await naledi.request.get('/api/v1/requests', { headers: { Authorization: `Bearer ${token}` } });
  expect(staffList.status()).toBe(403);
  expect((await staffList.json()).code).toBe('FORBIDDEN');
  await naledi.goto('/management/activity');
  await expect(naledi).toHaveURL(/\/requests\/new$/);
  await expect(naledi.getByRole('link', { name: 'Activity' })).toHaveCount(0);
});
