import { test, expect } from '../../../fixtures';
import { BulkJobsPage } from '../../../pages/bulk-jobs.page';

test.describe('Bulk Jobs', () => {
  test('displays bulk jobs page', async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    await expect(page).toHaveURL(/\/bulk-jobs/);
    await expect(bulkJobsPage.bulkJobsPage).toBeVisible();
  });

  test('shows bulk jobs table or empty state', async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    const rowCount = await bulkJobsPage.getRowCount();
    if (rowCount > 0) {
      await expect(bulkJobsPage.table).toBeVisible();
    } else {
      await expect(bulkJobsPage.bulkJobsPage).toBeVisible();
    }
  });

  test('opens create bulk job form', async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    await bulkJobsPage.clickCreate();

    await expect(bulkJobsPage.formModal).toBeVisible();
  });

  test('can select operation type', async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    await bulkJobsPage.clickCreate();
    await expect(bulkJobsPage.formModal).toBeVisible();

    await expect(bulkJobsPage.operationInput).toBeVisible();
    await bulkJobsPage.fillForm({ operation: 'delete' });

    await expect(bulkJobsPage.operationInput).toHaveValue('delete');
  });
});
