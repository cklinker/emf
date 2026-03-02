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

    // Check for the data-testid section or a heading with "Quick Create"
    const section = homePage.quickActions;
    const isSectionVisible = await section
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const heading = page.getByRole("heading", { name: /quick/i });
    const isHeadingVisible = await heading
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(isSectionVisible || isHeadingVisible).toBe(true);
  });

  test("shows recent records section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    // Check for the data-testid section or a heading with "Recent"
    const section = homePage.recentItems;
    const isSectionVisible = await section
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const heading = page.getByRole("heading", { name: /recent/i });
    const isHeadingVisible = await heading
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(isSectionVisible || isHeadingVisible).toBe(true);
  });

  test("shows favorites section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    // Check for the data-testid section or a heading with "Favorite"
    const section = homePage.favorites;
    const isSectionVisible = await section
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const heading = page.getByRole("heading", { name: /favorite/i });
    const isHeadingVisible = await heading
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(isSectionVisible || isHeadingVisible).toBe(true);
  });
});
