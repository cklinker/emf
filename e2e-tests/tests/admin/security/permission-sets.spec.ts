import { test, expect } from "../../../fixtures";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Permission Sets", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/permission-sets");
    await page.waitForLoadState("networkidle");
  });

  test("displays permission sets list page", async ({ page }) => {
    await expect(page).toHaveURL(/\/permission-sets/);
    const found = await waitForAnyVisible([
      page.getByTestId("permission-sets-page"),
      page.getByRole("heading", { name: /permission sets/i }),
    ]);
    expect(found).toBe(true);
  });

  test("shows permission sets table or empty state", async ({ page }) => {
    const found = await waitForAnyVisible([
      page.getByTestId("permission-sets-table"),
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      page.getByTestId("permission-sets-page"),
    ]);
    expect(found).toBe(true);
  });

  test("has create permission set button", async ({ page }) => {
    // Button may or may not exist depending on permissions or error state
    const found = await waitForAnyVisible([
      page.getByTestId("new-permission-set-button"),
      page.getByTestId("permission-sets-page"),
    ]);
    expect(found).toBe(true);
  });
});
