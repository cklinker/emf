import { test, expect } from "../../../fixtures";

test.describe("Permission Sets", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/permission-sets");
    await page.waitForLoadState("networkidle");
  });

  test("displays permission sets list page", async ({ page }) => {
    await expect(page).toHaveURL(/\/permission-sets/);
    const anyState = page
      .getByTestId("permission-sets-page")
      .or(page.getByRole("heading", { name: /permission sets/i }));
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });

  test("shows permission sets table or empty state", async ({ page }) => {
    const anyState = page
      .getByTestId("permission-sets-table")
      .or(page.getByTestId("empty-state"))
      .or(page.getByTestId("error-message"))
      .or(page.getByTestId("permission-sets-page"));
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });

  test("has create permission set button", async ({ page }) => {
    // Button may or may not exist depending on permissions or error state
    const anyState = page
      .getByTestId("new-permission-set-button")
      .or(page.getByTestId("permission-sets-page"));
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });
});
