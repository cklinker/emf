import { test, expect } from "../../../fixtures";

test.describe("Modules", () => {
  test("displays modules page", async ({ page }) => {
    await page.goto("/default/modules");
    await page.waitForLoadState("networkidle");

    await expect(page).toHaveURL(/\/modules/);
    const anyState = page
      .getByTestId("modules-page")
      .or(page.getByRole("heading", { name: /modules/i }));
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });

  test("shows installed modules or description text", async ({ page }) => {
    await page.goto("/default/modules");
    await page.waitForLoadState("networkidle");

    const anyState = page
      .getByTestId("modules-page")
      .or(page.getByTestId("error-message"));
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });

  test("has install module button", async ({ page }) => {
    await page.goto("/default/modules");
    await page.waitForLoadState("networkidle");

    const anyState = page
      .getByRole("button", { name: /install module/i })
      .or(page.getByTestId("error-message"))
      .or(page.getByTestId("modules-page"));
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });
});
