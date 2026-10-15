import { expect, test } from '@playwright/test';
import { ACCOUNTS, loginAs, unique } from './support';

/*
 * E2E-01 - the critical journey across the integrated system (FR-001, FR-004, FR-008, FR-009, FR-010).
 * Technique: state transition (New -> Assigned -> In Progress -> Resolved), driven through the UI of
 * two different roles. Expected results come from the FR-009 state machine and FR-004 (the requester
 * sees each status and every note marked visible, but no staff names).
 */
test('E2E-01: a requester submits, a handler accepts and resolves, the requester sees every step', async ({ browser }) => {
  const description = unique('E2E projector in room 4 does not turn on');

  // Requester submits (FR-001): confirmation with reference number and status New
  const requester = await loginAs(browser, ACCOUNTS.thabo);
  await requester.getByLabel('Category *').selectOption({ label: 'IT Support' });
  await requester.getByLabel('Description *').fill(description);
  await requester.getByLabel('Location *').fill('Room 4');
  await requester.getByRole('button', { name: 'Submit request' }).click();
  const confirmation = requester.locator('.confirm-box');
  await expect(confirmation).toContainText('status: New');
  const ref = (await confirmation.textContent())!.match(/CC-\d{4}-\d{6}/)![0];

  // Handler covering IT Support finds it in their queue and accepts it (FR-005, FR-008)
  const handler = await loginAs(browser, ACCOUNTS.ayesha);
  await handler.getByRole('button', { name: ref }).click();
  await expect(handler.getByRole('heading', { level: 2 })).toContainText(ref);
  await handler.getByRole('button', { name: 'Accept request' }).click();

  // Only allowed next statuses are offered, nothing is pre-selected (FR-009)
  const toStatus = handler.locator('#toStatus');
  await expect(toStatus).toBeVisible();
  await expect(handler.getByRole('button', { name: 'Update' })).toBeDisabled();
  await expect(toStatus.locator('option:not([disabled])')).toHaveText(['In Progress', 'Rejected']);
  await toStatus.selectOption({ label: 'In Progress' });
  await handler.getByRole('button', { name: 'Update' }).click();
  await expect(toStatus.locator('option:not([disabled])')).toHaveText(['Resolved']);

  // A note the requester may see (FR-010), then resolve
  await handler.getByLabel('Add comment / resolution note').fill('Technician replaced the lamp');
  await handler.getByLabel('Visible to requester').check();
  await handler.getByRole('button', { name: 'Add note' }).click();
  await expect(handler.locator('ul.timeline').first()).toContainText('Technician replaced the lamp');
  await toStatus.selectOption({ label: 'Resolved' });
  await handler.getByRole('button', { name: 'Update' }).click();
  const detail = handler.locator('section.panel').nth(1);
  await expect(detail.locator('.badge').first()).toContainText('Resolved');

  // The audit history records every step (FR-007)
  const history = handler.locator('h3:has-text("Audit history") + ul');
  await expect(history).toContainText('submitted the request');
  await expect(history).toContainText('Ayesha Patel');

  // Requester sees the whole history and the visible note, with no staff names (FR-004, ASR-005)
  await requester.goto('/requests/mine');
  await requester.getByRole('button', { name: ref }).click();
  const updates = requester.locator('ul.timeline');
  await expect(updates.locator('li')).toHaveText([
    /Status: Resolved/,
    /Note: Technician replaced the lamp/,
    /Status: In Progress/,
    /Status: Assigned/,
    /Request submitted/,
  ]);
  await expect(requester.getByRole('main')).not.toContainText('Ayesha');
});
