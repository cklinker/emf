import { test, expect } from "../../../fixtures";
import { CollectionsListPage } from "../../../pages/collections-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Collections CRUD", () => {
  test("displays collections list page", async ({ page }) => {
    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    await expect(collectionsPage.container).toBeVisible();
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("shows collections table with data", async ({
    page,
    dataFactory,
  }) => {
    // Ensure at least one collection exists
    await dataFactory.createCollection();

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    const tableOrEmpty = collectionsPage.collectionsTable.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("filters collections by name", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection({
      displayName: "FilterTestCollection",
    });
    const filterName =
      (collection.attributes.displayName as string) || "FilterTestCollection";

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    await collectionsPage.filterByName(filterName);

    // Wait for the filter to take effect
    await page.waitForTimeout(500);

    const names = await collectionsPage.getCollectionNames();
    expect(names.length).toBeGreaterThan(0);
    for (const name of names) {
      expect(name.toLowerCase()).toContain(filterName.toLowerCase());
    }
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("filters collections by status", async ({ page, dataFactory }) => {
    await dataFactory.createCollection({ active: true });

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    await collectionsPage.filterByStatus("active");

    // Wait for the filter to take effect
    await page.waitForTimeout(500);

    const rowCount = await collectionsPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("sorts collections by name", async ({ page, dataFactory }) => {
    await dataFactory.createCollection({ displayName: "Alpha Collection" });
    await dataFactory.createCollection({ displayName: "Beta Collection" });

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    // Click the name column header to sort ascending
    await collectionsPage.sortByColumn("name");
    await page.waitForTimeout(500);

    const namesAsc = await collectionsPage.getCollectionNames();
    expect(namesAsc.length).toBeGreaterThan(0);

    // Click again to sort descending
    await collectionsPage.sortByColumn("name");
    await page.waitForTimeout(500);

    const namesDesc = await collectionsPage.getCollectionNames();
    expect(namesDesc.length).toBeGreaterThan(0);

    // Verify the order changed (first item should differ if more than one)
    if (namesAsc.length > 1 && namesDesc.length > 1) {
      expect(namesAsc[0]).not.toBe(namesDesc[0]);
    }
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("navigates to collection detail on row click", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    await collectionsPage.clickRow(0);

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/collections/.+`));
  });

  // Skip: requires dataFactory API token (Authentik password grant not available)
  test.skip("deletes a collection with confirmation dialog", async ({
    page,
    dataFactory,
  }) => {
    await dataFactory.createCollection({
      displayName: "ToDeleteCollection",
    });

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    const initialCount = await collectionsPage.getRowCount();
    expect(initialCount).toBeGreaterThan(0);

    // Click delete on the first row
    await collectionsPage.clickDelete(0);

    // Confirm the deletion in the dialog
    await collectionsPage.confirmDelete();

    // Wait for the deletion to complete
    await page.waitForTimeout(1000);

    // Verify the collection was removed or count decreased
    await collectionsPage.goto();
    const finalCount = await collectionsPage.getRowCount();
    expect(finalCount).toBeLessThan(initialCount);
  });
});
