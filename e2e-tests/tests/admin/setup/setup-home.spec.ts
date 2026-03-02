import { test, expect } from "../../../fixtures";
import { SetupHomePage } from "../../../pages/setup-home.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Setup Home", () => {
  test("displays setup home page", async ({ page }) => {
    const setupPage = new SetupHomePage(page, tenantSlug);
    await setupPage.goto();

    await expect(setupPage.container).toBeVisible();
  });

  test("shows all category cards", async ({ page }) => {
    const setupPage = new SetupHomePage(page, tenantSlug);
    await setupPage.goto();

    const categoryCount = await setupPage.getCategoryCount();
    expect(categoryCount).toBeGreaterThan(0);
  });

  test("can search for setup items", async ({ page }) => {
    const setupPage = new SetupHomePage(page, tenantSlug);
    await setupPage.goto();

    await expect(setupPage.searchInput).toBeVisible();
    await setupPage.searchFor("collection");

    // Search should filter visible items
    await page.waitForTimeout(300);
  });

  test("clears search input", async ({ page }) => {
    const setupPage = new SetupHomePage(page, tenantSlug);
    await setupPage.goto();

    await setupPage.searchFor("collection");
    await expect(setupPage.searchInput).toHaveValue("collection");

    await setupPage.clearSearch();
    await expect(setupPage.searchInput).toHaveValue("");
  });

  test("navigates to collection settings from setup", async ({ page }) => {
    const setupPage = new SetupHomePage(page, tenantSlug);
    await setupPage.goto();

    await setupPage.clickItem("collections");

    await page.waitForURL(/\/collections/);
    await expect(page).toHaveURL(/\/collections/);
  });

  test("shows quick stats section", async ({ page }) => {
    const setupPage = new SetupHomePage(page, tenantSlug);
    await setupPage.goto();

    await expect(setupPage.stats).toBeVisible();

    const statCards = await setupPage.getStatCards();
    expect(statCards.length).toBeGreaterThan(0);
  });
});
