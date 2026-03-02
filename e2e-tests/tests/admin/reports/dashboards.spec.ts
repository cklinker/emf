import { test, expect } from '../../../fixtures';
import { DashboardsPage } from '../../../pages/dashboards.page';

test.describe('Dashboards', () => {
  test('displays dashboards page', async ({ page }) => {
    const dashboardsPage = new DashboardsPage(page);
    await dashboardsPage.goto();

    await expect(page).toHaveURL(/\/dashboards/);
    await expect(dashboardsPage.dashboardsPage).toBeVisible();
  });

  test('has create dashboard button', async ({ page }) => {
    const dashboardsPage = new DashboardsPage(page);
    await dashboardsPage.goto();

    await expect(dashboardsPage.createButton).toBeVisible();
  });

  test('shows dashboards list or empty state', async ({ page }) => {
    const dashboardsPage = new DashboardsPage(page);
    await dashboardsPage.goto();

    const dashboardCount = await dashboardsPage.getDashboardCount();
    if (dashboardCount > 0) {
      await expect(dashboardsPage.dashboardList).toBeVisible();
    } else {
      // When no dashboards exist, the page container should still be present
      await expect(dashboardsPage.dashboardsPage).toBeVisible();
    }
  });
});
