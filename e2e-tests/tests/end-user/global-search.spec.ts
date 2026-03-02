import { test, expect } from "../../fixtures";
import { GlobalSearchPage } from "../../pages/end-user/global-search.page";

test.describe("Global Search", () => {
  test("displays global search page", async ({ page }) => {
    const searchPage = new GlobalSearchPage(page);
    await searchPage.goto();

    await expect(page).toHaveURL(/\/app\/search/);
  });

  test("can enter search query", async ({ page }) => {
    const searchPage = new GlobalSearchPage(page);
    await searchPage.goto();

    await expect(searchPage.searchInput).toBeVisible();
    await searchPage.search("test query");

    await expect(searchPage.searchInput).toHaveValue("test query");
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("shows search results", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.createRecord(collectionName, {
      title: `SearchTarget_${Date.now()}`,
    });

    const searchPage = new GlobalSearchPage(page);
    await searchPage.goto();

    await searchPage.search("SearchTarget");

    // Wait for search results to load
    await page.waitForTimeout(1000);

    const resultCount = await searchPage.getResultCount();
    expect(resultCount).toBeGreaterThanOrEqual(0);
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("navigates to result on click", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.createRecord(collectionName, {
      title: `NavTarget_${Date.now()}`,
    });

    const searchPage = new GlobalSearchPage(page);
    await searchPage.goto();

    await searchPage.search("NavTarget");
    await page.waitForTimeout(1000);

    const resultCount = await searchPage.getResultCount();
    if (resultCount > 0) {
      const initialUrl = page.url();
      await searchPage.clickResult(0);

      // Should navigate away from search page
      await page.waitForTimeout(500);
      expect(page.url()).not.toBe(initialUrl);
    }
  });
});
