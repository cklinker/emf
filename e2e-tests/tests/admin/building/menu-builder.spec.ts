import { test, expect } from "../../../fixtures";

test.describe("Menu Builder", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/menus");
    await page.waitForLoadState("load");
  });

  test("displays menu builder page", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /menu/i });
    await expect(heading).toBeVisible();
  });

  test("shows menu list or empty state", async ({ page }) => {
    const grid = page.locator("table, [role='grid']");
    const emptyState = page.getByText(/no.*menu|no.*data/i);
    const gridOrEmpty = grid.or(emptyState);
    await expect(gridOrEmpty).toBeVisible();
  });

  test("has create menu button", async ({ page }) => {
    const createButton = page.getByRole("button", {
      name: /create menu/i,
    });
    await expect(createButton).toBeVisible();
  });
});
