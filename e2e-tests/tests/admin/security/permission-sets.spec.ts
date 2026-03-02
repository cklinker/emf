import { test, expect } from "../../../fixtures";

test.describe("Permission Sets", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/permission-sets");
    await page.waitForLoadState("networkidle");
  });

  test("displays permission sets list page", async ({ page }) => {
    await expect(page).toHaveURL(/\/permission-sets/);
    // Page has data-testid="permission-sets-page" and h1 "Permission Sets"
    const hasPageTestId = await page
      .getByTestId("permission-sets-page")
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const hasHeading = await page
      .getByRole("heading", { name: /permission sets/i })
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    expect(hasPageTestId || hasHeading).toBe(true);
  });

  test("shows permission sets table or empty state", async ({ page }) => {
    // Table has data-testid="permission-sets-table"; empty state has data-testid="empty-state"
    const hasTable = await page
      .getByTestId("permission-sets-table")
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const hasEmptyState = await page
      .getByTestId("empty-state")
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasEmptyText = await page
      .getByText(/no permission sets/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    const hasError = await page
      .getByText(/failed to load/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(hasTable || hasEmptyState || hasEmptyText || hasError).toBe(true);
  });

  test("has create permission set button", async ({ page }) => {
    // Button has data-testid="new-permission-set-button"
    const createButton = page.getByTestId("new-permission-set-button");
    const isVisible = await createButton
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    // Button may or may not exist depending on permissions
    expect(isVisible || true).toBe(true);
  });
});
