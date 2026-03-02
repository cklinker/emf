import { test, expect } from "../../../fixtures";

test.describe("Reports", () => {
  test("displays reports page", async ({ page }) => {
    await page.goto("/default/setup/reports");
    await page.waitForLoadState("networkidle");

    await expect(page).toHaveURL(/\/reports/);
    const heading = page.getByRole("heading", { name: /reports/i });
    await expect(heading).toBeVisible();
  });

  test("has create report button", async ({ page }) => {
    await page.goto("/default/setup/reports");
    await page.waitForLoadState("networkidle");

    const createButton = page.getByRole("button", {
      name: /create report/i,
    });
    await expect(createButton).toBeVisible();
  });

  test("shows reports list or empty state", async ({ page }) => {
    await page.goto("/default/setup/reports");
    await page.waitForLoadState("networkidle");

    const emptyState = page.getByText(/no.*report|no.*data/i);
    const table = page.locator("table, [role='grid']");
    const listOrEmpty = table.or(emptyState);
    await expect(listOrEmpty).toBeVisible();
  });
});
