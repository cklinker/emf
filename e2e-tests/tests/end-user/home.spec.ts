import { test, expect } from "../../fixtures";
import { AppHomePage } from "../../pages/end-user/app-home.page";

test.describe("End-User Home", () => {
  test("displays end-user home page", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(page).toHaveURL(/\/app\/home/);
  });

  test("shows quick create section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();
    await page.waitForLoadState("load");

    // CardTitle renders as h3 "Quick Actions" — no data-testid on the page
    const section = homePage.quickActions;
    const isSectionVisible = await section
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasCardTitle = await page
      .getByRole("heading", { name: /quick actions/i })
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasQuickText = await page
      .getByText(/quick actions/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(isSectionVisible || hasCardTitle || hasQuickText).toBe(true);
  });

  test("shows recent records section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();
    await page.waitForLoadState("load");

    // CardTitle renders as h3 "Recent Items"
    const section = homePage.recentItems;
    const isSectionVisible = await section
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasCardTitle = await page
      .getByRole("heading", { name: /recent items/i })
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasRecentText = await page
      .getByText(/recent items/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(isSectionVisible || hasCardTitle || hasRecentText).toBe(true);
  });

  test("shows favorites section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();
    await page.waitForLoadState("load");

    // CardTitle renders as h3 "Favorites"
    const section = homePage.favorites;
    const isSectionVisible = await section
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasCardTitle = await page
      .getByRole("heading", { name: /favorites/i })
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasFavText = await page
      .getByText(/favorites/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(isSectionVisible || hasCardTitle || hasFavText).toBe(true);
  });
});
