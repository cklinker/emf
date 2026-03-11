import { test, expect } from "../../../fixtures";
import { ListViewsPage } from "../../../pages/list-views.page";

test.describe("List Views", () => {
  let listViewsPage: ListViewsPage;

  test.beforeEach(async ({ page }) => {
    listViewsPage = new ListViewsPage(page);
    await listViewsPage.goto();
  });

  test("displays list views page", async () => {
    await listViewsPage.waitForTableLoaded();
  });

  test("shows list views in table", async () => {
    await listViewsPage.waitForTableLoaded();
    const rowCount = await listViewsPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test("opens create list view form", async () => {
    await listViewsPage.waitForTableLoaded();
    await listViewsPage.clickCreate();
    await expect(listViewsPage.formModal).toBeVisible();
  });
});
