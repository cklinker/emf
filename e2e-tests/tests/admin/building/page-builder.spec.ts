import { test, expect } from "../../../fixtures";

test.describe("Page Builder", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/pages");
    await page.waitForLoadState("load");
  });

  test("displays page builder page", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /page builder/i });
    await expect(heading).toBeVisible();
  });

  test("shows pages table or empty state", async ({ page }) => {
    const grid = page.locator("table, [role='grid']");
    // The page builder renders a generic "No results found" empty state
    // (not "no pages"/"no data"), so match common empty-state phrasings.
    const emptyState = page.getByText(/no.*(page|data|result)/i);
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
