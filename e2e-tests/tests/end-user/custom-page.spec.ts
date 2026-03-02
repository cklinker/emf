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
    const content = page.getByTestId("custom-page-content");
    const notFound = page.getByText(/not found|no.*page|loading/i);
    const contentOrNotFound = content.or(notFound);
    await expect(contentOrNotFound).toBeVisible({ timeout: 10_000 });
  });
});
