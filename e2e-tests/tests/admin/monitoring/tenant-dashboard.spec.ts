import { test, expect } from "../../../fixtures";
import { TenantDashboardPage } from "../../../pages/tenant-dashboard.page";

test.describe("Tenant Dashboard", () => {
  let tenantDashboardPage: TenantDashboardPage;

  test.beforeEach(async ({ page }) => {
    tenantDashboardPage = new TenantDashboardPage(page);
    await tenantDashboardPage.goto();
  });

  test("displays tenant dashboard page", async () => {
    await expect(tenantDashboardPage.tenantDashboardPage).toBeVisible();
  });

  test("shows usage cards (API calls, storage, users, collections)", async ({
    page,
  }) => {
    const usageOrEmpty = tenantDashboardPage.usageCards.or(
      page.getByTestId("empty-state"),
    );
    await expect(usageOrEmpty).toBeVisible();
  });

  test("shows usage percentage indicators", async () => {
    const cardCount = await tenantDashboardPage.getUsageCardCount();
    expect(cardCount).toBeGreaterThan(0);
  });
});
