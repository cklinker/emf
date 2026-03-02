import { test, expect } from '../../../fixtures';
import { ReportsPage } from '../../../pages/reports.page';

test.describe('Reports', () => {
  test('displays reports page', async ({ page }) => {
    const reportsPage = new ReportsPage(page);
    await reportsPage.goto();

    await expect(page).toHaveURL(/\/reports/);
    await expect(reportsPage.reportsPage).toBeVisible();
  });

  test('has create report button', async ({ page }) => {
    const reportsPage = new ReportsPage(page);
    await reportsPage.goto();

    await expect(reportsPage.createButton).toBeVisible();
  });

  test('shows reports list or empty state', async ({ page }) => {
    const reportsPage = new ReportsPage(page);
    await reportsPage.goto();

    const reportCount = await reportsPage.getReportCount();
    if (reportCount > 0) {
      await expect(reportsPage.reportList).toBeVisible();
    } else {
      // When no reports exist, the list container should still be present
      await expect(reportsPage.reportsPage).toBeVisible();
    }
  });
});
