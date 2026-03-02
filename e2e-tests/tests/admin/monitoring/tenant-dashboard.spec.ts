import { test, expect } from '../../../fixtures';
import { TenantDashboardPage } from '../../../pages/tenant-dashboard.page';

test.describe('Tenant Dashboard', () => {
  let tenantDashboardPage: TenantDashboardPage;

  test.beforeEach(async ({ page }) => {
    tenantDashboardPage = new TenantDashboardPage(page);
    await tenantDashboardPage.goto();
  });

  test('displays tenant dashboard page', async () => {
    await expect(tenantDashboardPage.tenantDashboardPage).toBeVisible();
  });

  test('shows usage cards (API calls, storage, users, collections)', async () => {
    await expect(tenantDashboardPage.usageCards).toBeVisible();
    await expect(tenantDashboardPage.usageApiCalls).toBeVisible();
    await expect(tenantDashboardPage.usageStorage).toBeVisible();
    await expect(tenantDashboardPage.usageUsers).toBeVisible();
    await expect(tenantDashboardPage.usageCollections).toBeVisible();
  });

  test('shows usage percentage indicators', async () => {
    const cardCount = await tenantDashboardPage.getUsageCardCount();
    expect(cardCount).toBeGreaterThan(0);
  });
});
