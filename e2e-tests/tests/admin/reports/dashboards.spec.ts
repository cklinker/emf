import { test, expect } from "../../../fixtures";

test.describe("Dashboards", () => {
  test("displays dashboards page", async ({ page }) => {
    await page.goto("/default/setup/dashboards");
    await page.waitForLoadState("networkidle");

    await expect(page).toHaveURL(/\/dashboards/);
    const heading = page.getByRole("heading", { name: /dashboards/i });
    await expect(heading).toBeVisible();
  });

  test("has create dashboard button", async ({ page }) => {
    await page.goto("/default/setup/dashboards");
    await page.waitForLoadState("networkidle");

    const createButton = page.getByRole("button", {
      name: /create dashboard/i,
    });
    await expect(createButton).toBeVisible();
  });

  test("shows dashboards list or empty state", async ({ page }) => {
    await page.goto("/default/setup/dashboards");
    await page.waitForLoadState("networkidle");

    const emptyState = page.getByText(/no.*dashboard|no.*data/i);
    const table = page.locator("table, [role='grid']");
    const listOrEmpty = table.or(emptyState);
    await expect(listOrEmpty).toBeVisible();
  });
});
