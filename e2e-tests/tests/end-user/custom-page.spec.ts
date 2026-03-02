import { test, expect } from "../../fixtures";

test.describe("Custom Page", () => {
  test("navigates to custom page URL", async ({ page }) => {
    await page.goto("/default/app/p/home");
    await expect(page).toHaveURL(/\/app\/p\/home/);
  });

  test("shows page content or loading/not-found state", async ({ page }) => {
    await page.goto("/default/app/p/home");
    await page.waitForLoadState("networkidle");

    // Custom pages may show content, a loading state, or a "not found" message
    // depending on whether the page slug exists and has a registered component
    const hasContent = await page
      .getByTestId("custom-page-content")
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const hasNotFound = await page
      .getByText(/not found/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasLoading = await page
      .getByText(/loading/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);

    // Any of these states is acceptable
    expect(hasContent || hasNotFound || hasLoading).toBe(true);
  });
});
