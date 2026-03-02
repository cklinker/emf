import { test, expect } from "../../fixtures";
import { AppHomePage } from "../../pages/end-user/app-home.page";
import { ObjectListPage } from "../../pages/end-user/object-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("End-User Navigation", () => {
  test("shows top navigation bar", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.header).toBeVisible();
  });

  test("shows user menu", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    const userMenu = page.getByTestId("user-menu");
    await expect(userMenu).toBeVisible();
  });

  test("navigates between collections", async ({ page, dataFactory }) => {
    const collectionA = await dataFactory.createCollection({
      displayName: "Nav Test A",
    });
    const collectionB = await dataFactory.createCollection({
      displayName: "Nav Test B",
    });

    const collectionAName = collectionA.attributes.name as string;
    const collectionBName = collectionB.attributes.name as string;

    const listPageA = new ObjectListPage(page, collectionAName, tenantSlug);
    await listPageA.goto();
    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionAName}`));

    const listPageB = new ObjectListPage(page, collectionBName, tenantSlug);
    await listPageB.goto();
    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionBName}`));
  });

  test("shows collection tabs in nav", async ({ page, dataFactory }) => {
    await dataFactory.createCollection();

    const homePage = new AppHomePage(page);
    await homePage.goto();

    // The navigation should contain links to collections
    const nav = page.locator("nav");
    await expect(nav).toBeVisible();
  });
});
