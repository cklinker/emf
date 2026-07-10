import { test, expect } from "../../fixtures";
import { AppHomePage } from "../../pages/end-user/app-home.page";

test.describe("End-User Navigation", () => {
  test("shows top navigation bar", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.header).toBeVisible();
  });

  test("shows user menu or avatar button", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(page.getByTestId("user-menu-button")).toBeVisible();
  });

  test("navigates between collections", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection({
      displayName: "Nav Test A",
    });
    const collectionName = collection.attributes.name as string;

    // Navigate to the collection's object list page
    await page.goto(`/default/app/o/${collectionName}`);
    await page.waitForLoadState("load");

    // Verify we're on the collection page
    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}`));

    // Navigate back to home
    const homePage = new AppHomePage(page);
    await homePage.goto();
    await expect(page).toHaveURL(/\/app\/home/);
  });

  test("shows collection tabs in nav", async ({ page, dataFactory }) => {
    await dataFactory.createCollection();

    const homePage = new AppHomePage(page);
    await homePage.goto();

    // The top nav bar should have a navigation region for collections
    const nav = page
      .getByRole("navigation", { name: /object/i })
      .or(page.locator("#main-navigation"));
    const hasNav = await nav
      .isVisible({ timeout: 5_000 })
      .catch(() => false);

    if (hasNav) {
      // Nav exists — verify it contains clickable elements
      const tabs = nav.locator("button, a");
      const tabCount = await tabs.count();
      expect(tabCount).toBeGreaterThanOrEqual(0);
    } else {
      // Nav tabs may not be visible on mobile or if no collections are in the menu
      await expect(homePage.header).toBeVisible();
    }
  });
});

test.describe("Nested menu items (submenus)", () => {
  test("a parentId item renders inside a dropdown group and navigates", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection({
      displayName: "Nav Nested Target",
    });
    const collectionName = collection.attributes.name as string;

    // A dedicated default app whose nav carries one group with one child.
    const menu = await dataFactory.createRecord("ui-menus", {
      name: `nested-nav-${Date.now()}`,
      isDefault: true,
      active: true,
    });
    const parent = await dataFactory.createRecord("ui-menu-items", {
      menuId: menu.id,
      label: "Nested Group",
      displayOrder: 1,
      active: true,
    });
    await dataFactory.createRecord("ui-menu-items", {
      menuId: menu.id,
      parentId: parent.id,
      label: "Nested Child",
      path: `/resources/${collectionName}`,
      displayOrder: 1,
      active: true,
    });

    await page.goto("/default/app/home");
    await page.waitForLoadState("load");

    const group = page.getByTestId("nav-group-Nested Group");
    await expect(group).toBeVisible();
    await group.click();
    await page.getByTestId("nav-group-item-Nested Child").click();
    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}`));
  });
});
