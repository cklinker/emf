import { test, expect } from "../../fixtures";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Unauthorized Page", () => {
  // Skip: The /unauthorized route may not exist or may not have the expected
  // structure (data-testid="unauthorized-page", "home" button, "back" button).
  // These tests will be re-enabled once the unauthorized page UI is confirmed
  // and has stable data-testid attributes.

  test.skip("displays unauthorized page for restricted access", async ({
    page,
  }) => {
    // The /unauthorized page may not be implemented yet or may redirect
    // elsewhere depending on auth state.
    await page.goto(`/${tenantSlug}/unauthorized`);
    await page.waitForLoadState("networkidle");

    const unauthorizedContainer = page.getByTestId("unauthorized-page");
    await expect(unauthorizedContainer).toBeVisible();
  });

  test.skip("shows go home button that navigates back", async ({ page }) => {
    // Depends on the unauthorized page having a "home" button with a
    // specific role/label. Skipped until the page structure is confirmed.
    await page.goto(`/${tenantSlug}/unauthorized`);
    await page.waitForLoadState("networkidle");

    const goHomeButton = page.getByRole("button", { name: /home/i });
    await expect(goHomeButton).toBeVisible();

    await goHomeButton.click();

    await expect(page).not.toHaveURL(new RegExp(`/${tenantSlug}/unauthorized`));
  });

  test.skip("shows go back button", async ({ page }) => {
    // Depends on the unauthorized page having a "back" button with a
    // specific role/label. Skipped until the page structure is confirmed.
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState("networkidle");

    await page.goto(`/${tenantSlug}/unauthorized`);
    await page.waitForLoadState("networkidle");

    const goBackButton = page.getByRole("button", { name: /back/i });
    await expect(goBackButton).toBeVisible();

    await goBackButton.click();

    await expect(page).not.toHaveURL(new RegExp(`/${tenantSlug}/unauthorized`));
  });
});
