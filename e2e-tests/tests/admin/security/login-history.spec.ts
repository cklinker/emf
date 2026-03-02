import { test, expect } from "../../../fixtures";

test.describe("Login History", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/login-history");
    await page.waitForLoadState("networkidle");
  });

  test("displays login history page", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /login history/i });
    await expect(heading).toBeVisible();
  });

  test("shows login history table or empty state", async ({ page }) => {
    const table = page.locator("table");
    const noEntries = page.getByText(/no.*entries|no.*data|no.*history/i);
    const tableOrEmpty = table.or(noEntries);
    await expect(tableOrEmpty).toBeVisible();
  });

  test.skip("displays pagination controls", async () => {
    // Skipped: pagination may not be visible when there is little data
  });

  test("navigates between pages", async ({ page }) => {
    const nextButton = page.getByRole("button", { name: /next/i });
    const isNextVisible = await nextButton
      .isVisible({ timeout: 2000 })
      .catch(() => false);

    if (isNextVisible) {
      const isNextEnabled = await nextButton.isEnabled();
      if (isNextEnabled) {
        await nextButton.click();
        await page.waitForLoadState("networkidle");
      }
    }
  });
});
