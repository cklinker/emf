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

    // The home page has a quick-create-section (was quick-actions in the page object)
    const section = homePage.quickActions;
    const heading = page.getByText(/quick/i);
    const sectionOrHeading = section.or(heading);
    await expect(sectionOrHeading).toBeVisible();
  });

  test("shows recent records section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    // The home page has a recent-records-section
    const section = homePage.recentItems;
    const heading = page.getByText(/recent/i);
    const sectionOrHeading = section.or(heading);
    await expect(sectionOrHeading).toBeVisible();
  });

  test("shows favorites section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    // The home page has a favorites-section
    const section = homePage.favorites;
    const heading = page.getByText(/favorite/i);
    const sectionOrHeading = section.or(heading);
    await expect(sectionOrHeading).toBeVisible();
  });
});
