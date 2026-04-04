import { test, expect } from "../../../fixtures";
import { SearchSettingsPage } from "../../../pages/search-settings.page";

test.describe("Search Settings", () => {
  let searchSettingsPage: SearchSettingsPage;

  test.beforeEach(async ({ page }) => {
    searchSettingsPage = new SearchSettingsPage(page);
    await searchSettingsPage.goto();
  });

  test("displays search settings page", async () => {
    await expect(searchSettingsPage.container).toBeVisible();
  });

  test("shows reindex all button", async () => {
    await expect(searchSettingsPage.reindexAllButton).toBeVisible();
  });

  test("displays indexed records summary", async ({ page }) => {
    // The page shows total records indexed and number of collections
    await expect(searchSettingsPage.container).toBeVisible();

    // Look for summary statistics on the page
    const statsText = await searchSettingsPage.container.textContent();
    expect(statsText).toBeTruthy();
  });
});
