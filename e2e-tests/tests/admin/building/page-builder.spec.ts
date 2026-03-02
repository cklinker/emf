import { test, expect } from "../../../fixtures";

test.describe("Page Builder", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/pages");
    await page.waitForLoadState("networkidle");
  });

  test("displays page builder page", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /page builder/i });
    await expect(heading).toBeVisible();
  });

  test("shows pages table or empty state", async ({ page }) => {
    const grid = page.locator("table, [role='grid']");
    const emptyState = page.getByText(/no.*page|no.*data/i);
    const gridOrEmpty = grid.or(emptyState);
    await expect(gridOrEmpty).toBeVisible();
  });

  test("has create page button", async ({ page }) => {
    const createButton = page.getByRole("button", {
      name: /create page/i,
    });
    await expect(createButton).toBeVisible();
  });
});
