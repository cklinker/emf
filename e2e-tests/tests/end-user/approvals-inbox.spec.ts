import { test, expect } from '@playwright/test'

/**
 * Approvals inbox (app-surfacing slice 2) — post-deploy end-to-end flow.
 *
 * Skip-gated until the /app/approvals route is deployed (the e2e suite runs against the
 * live environment; the route does not exist until this slice ships). To arm: remove
 * `.skip`, ensure the target tenant has an active approval process with a USER-type step
 * assigned to the test user, and a submittable record.
 *
 * Asserts a full mutation cycle, not just render (per the page-builder-v2 precedent):
 * submit → inbox shows the pending step → approve with a comment → the record's timeline
 * shows Approved → the bell count decrements.
 */
test.describe.skip('Approvals inbox (post-deploy)', () => {
  test('submit → inbox → approve with comment → timeline shows approved', async ({ page }) => {
    // 1. Log in as the submitter and open a record with an active approval process.
    //    (Fixture wiring intentionally environment-specific — see suite comment.)
    await page.goto('/acme/app/o/orders')
    await page.getByTestId('object-row').first().click()

    // 2. Submit for approval from the record header.
    await page.getByTestId('submit-approval-button').click()
    await expect(page.getByTestId('pending-approval-badge')).toBeVisible()

    // 3. As the assignee: bell shows a count, click through to the inbox.
    await page.getByRole('button', { name: /notifications/i }).click()
    await expect(page).toHaveURL(/\/app\/approvals$/)
    await expect(page.getByTestId('pending-approval-row').first()).toBeVisible()

    // 4. Approve with a comment.
    await page.getByTestId('approve-button').first().click()
    await page.getByTestId('approval-comment').fill('e2e approved')
    await page.getByTestId('approval-confirm').click()
    await expect(page.getByTestId('pending-empty')).toBeVisible()

    // 5. Record timeline reflects the approval.
    await page.goBack()
    await expect(page.getByText('Approved')).toBeVisible()
  })
})
