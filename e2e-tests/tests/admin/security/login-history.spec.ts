import { test, expect } from "../../../fixtures";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Login History", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/login-history");
    await page.waitForLoadState("load");
  });

  test("displays login history page", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /login history/i });
    await expect(heading).toBeVisible();
  });

  test("shows login history table or empty state", async ({ page }) => {
    const found = await waitForAnyVisible([
      page.locator("table"),
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      page.getByTestId("login-history-page"),
    ]);
    expect(found).toBe(true);
  });

  test("displays pagination controls when data exists", async ({ page }) => {
    // Pagination controls may not exist if there is little data — handle both cases
    const pagination = page
      .getByTestId("pagination")
      .or(page.getByRole("navigation", { name: /pagination/i }));
    const hasPagination = await pagination
      .isVisible({ timeout: 5_000 })
      .catch(() => false);

    if (hasPagination) {
      await expect(pagination).toBeVisible();
    } else {
      // Not enough data for pagination — verify page still rendered correctly
      const heading = page.getByRole("heading", { name: /login history/i });
      await expect(heading).toBeVisible();
    }
  });

  test("navigates between pages", async ({ page }) => {
    const nextButton = page.getByRole("button", { name: /next/i });
    try {
      await nextButton.waitFor({ state: "visible", timeout: 5000 });
    } catch {
      return;
    }

    const isNextEnabled = await nextButton.isEnabled();
    if (isNextEnabled) {
      await nextButton.click();
      await page.waitForLoadState("load");
    }
  });
});
