import { test, expect } from "../../../fixtures";

test.describe("Modules", () => {
  test("displays modules page", async ({ page }) => {
    await page.goto("/default/setup/modules");
    await page.waitForLoadState("networkidle");

    await expect(page).toHaveURL(/\/modules/);
    const heading = page.getByRole("heading", { name: /modules/i });
    await expect(heading).toBeVisible();
  });

  test("shows installed modules or description text", async ({ page }) => {
    await page.goto("/default/setup/modules");
    await page.waitForLoadState("networkidle");

    // The page shows either module cards or a description about modules
    const moduleContent = page.getByText(/module|install|extend/i);
    await expect(moduleContent.first()).toBeVisible();
  });

  test("has install module button", async ({ page }) => {
    await page.goto("/default/setup/modules");
    await page.waitForLoadState("networkidle");

    const installButton = page.getByRole("button", {
      name: /install module/i,
    });
    await expect(installButton).toBeVisible();
  });
});
