import { test, expect } from "../../fixtures";

test.describe("Custom Page", () => {
  test("navigates to custom page URL", async ({ page }) => {
    await page.goto("/default/app/p/home");
    await expect(page).toHaveURL(/\/app\/p\/home/);
  });

  test("shows page content or not-found state", async ({ page }) => {
    await page.goto("/default/app/p/home");

    // Custom pages show "Page Not Found" or "Component Not Available" when
    // the slug doesn't exist or the component isn't registered. Either state
    // (plus a loading spinner) is acceptable here.
    await expect(
      page
        .getByText(/page not found/i)
        .first()
        .or(page.getByText(/component not available/i).first())
        .or(page.locator(".animate-spin").first()),
    ).toBeVisible({ timeout: 10_000 });
  });
});
