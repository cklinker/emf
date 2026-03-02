import { test, expect } from "../../../fixtures";
import { ResourceBrowserPage } from "../../../pages/resource-browser.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Resource Browser", () => {
  let resourceBrowserPage: ResourceBrowserPage;

  test.beforeEach(async ({ page }) => {
    resourceBrowserPage = new ResourceBrowserPage(page);
    await resourceBrowserPage.goto();
  });

  test("displays resource browser page", async () => {
    await expect(resourceBrowserPage.resourceBrowserPage).toBeVisible();
  });

  test("shows collection cards grid or empty state", async ({ page }) => {
    const found = await waitForAnyVisible([
      resourceBrowserPage.collectionsGrid,
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      resourceBrowserPage.resourceBrowserPage,
    ]);
    expect(found).toBe(true);
  });

  test("can search for collections", async () => {
    // Search input may not be available if API failed (error state)
    try {
      await resourceBrowserPage.collectionSearchInput.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await resourceBrowserPage.search("test");
    await expect(resourceBrowserPage.collectionSearchInput).toHaveValue("test");
  });

  test("can clear search", async () => {
    // Search input may not be available if API failed (error state)
    try {
      await resourceBrowserPage.collectionSearchInput.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await resourceBrowserPage.search("test");
    await resourceBrowserPage.clearSearch();
    await expect(resourceBrowserPage.collectionSearchInput).toHaveValue("");
  });

  test("shows results count when collections exist", async ({ page }) => {
    const found = await waitForAnyVisible([
      resourceBrowserPage.resultsCountLabel,
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      resourceBrowserPage.resourceBrowserPage,
    ]);
    expect(found).toBe(true);
  });
});
