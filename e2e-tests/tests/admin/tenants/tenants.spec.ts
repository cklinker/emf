import { test, expect } from "../../../fixtures";
import { TenantsPage } from "../../../pages/tenants.page";

test.describe("Tenants", () => {
  test("displays tenants page", async ({ page }) => {
    const tenantsPage = new TenantsPage(page);
    await tenantsPage.goto();

    await expect(page).toHaveURL(/\/tenants/);
    await expect(tenantsPage.tenantsPage).toBeVisible();
  });

  test("shows tenants table or empty state", async ({ page }) => {
    const tenantsPage = new TenantsPage(page);
    await tenantsPage.goto();

    const tableOrEmpty = tenantsPage.table.or(page.getByTestId("empty-state"));
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has create tenant button", async ({ page }) => {
    const tenantsPage = new TenantsPage(page);
    await tenantsPage.goto();
    await page.waitForLoadState("networkidle");

    await expect(tenantsPage.createButton).toBeVisible();
  });
});
