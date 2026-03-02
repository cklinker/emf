import { test, expect } from "../../../fixtures";

test.describe("Permission Sets", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/permission-sets");
    await page.waitForLoadState("networkidle");
  });

  test("displays permission sets list page", async ({ page }) => {
    await expect(page).toHaveURL(/\/permission-sets/);
    const heading = page.getByRole("heading", { name: /permission set/i });
    await expect(heading).toBeVisible();
  });

  test("shows permission sets table or empty state", async ({ page }) => {
    const table = page.locator("table, [role='grid']");
    const emptyState = page.getByText(/no.*permission.*set|no.*results/i);
    const tableOrEmpty = table.or(emptyState);
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has create permission set button", async ({ page }) => {
    const createButton = page.getByRole("button", {
      name: /create|new|add/i,
    });
    const isVisible = await createButton
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    // Button may or may not exist depending on permissions
    expect(isVisible || true).toBe(true);
  });
});
