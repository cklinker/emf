import { test, expect } from "../../../fixtures";
import { CollectionsListPage } from "../../../pages/collections-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Collections CRUD", () => {
  test("displays collections list page", async ({ page }) => {
    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    await expect(collectionsPage.container).toBeVisible();
  });

  test("shows collections table with data", async ({ page, dataFactory }) => {
    // Ensure at least one collection exists
    await dataFactory.createCollection();

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    // Wait for rows to actually appear (not just table or empty state)
    await collectionsPage.waitForRows();

    const rowCount = await collectionsPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test("filters collections by name", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection({
      displayName: "FilterTestCollection",
    });
    // Use the API name (first column) for filtering — the filter matches both name and displayName
    const collectionName = collection.attributes.name as string;

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    // Wait for rows to load
    await collectionsPage.waitForRows();

    // Filter by the collection's name
    await collectionsPage.filterByName(collectionName);

    // Wait for filtered rows to appear
    await expect(
      page.locator('[data-testid^="collection-row-"]').first(),
    ).toBeVisible({ timeout: 10_000 });

    const names = await collectionsPage.getCollectionNames();
    expect(names.length).toBeGreaterThan(0);
    for (const name of names) {
      expect(name.toLowerCase()).toContain(collectionName.toLowerCase());
    }
  });

  test("filters collections by status", async ({ page, dataFactory }) => {
    await dataFactory.createCollection({ active: true });

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    // Wait for rows to appear before filtering
    await collectionsPage.waitForRows();

    await collectionsPage.filterByStatus("active");

    // Wait for filtered results — at least one active collection should remain
    await expect(
      page.locator('[data-testid^="collection-row-"]').first(),
    ).toBeVisible();

    const rowCount = await collectionsPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test("sorts collections by name", async ({ page, dataFactory }) => {
    await dataFactory.createCollection({ displayName: "Alpha Collection" });
    await dataFactory.createCollection({ displayName: "Beta Collection" });

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    // Wait for rows to appear (ensures table header exists)
    await collectionsPage.waitForRows();

    // Click the name column header to sort ascending
    await collectionsPage.sortByColumn("name");

    // Wait for rows to re-render after sort
    await expect(
      page.locator('[data-testid^="collection-row-"]').first(),
    ).toBeVisible();

    const namesAsc = await collectionsPage.getCollectionNames();
    expect(namesAsc.length).toBeGreaterThan(0);

    // Click again to sort descending
    await collectionsPage.sortByColumn("name");

    await expect(
      page.locator('[data-testid^="collection-row-"]').first(),
    ).toBeVisible();

    const namesDesc = await collectionsPage.getCollectionNames();
    expect(namesDesc.length).toBeGreaterThan(0);

    // Verify the order changed (first item should differ if more than one)
    if (namesAsc.length > 1 && namesDesc.length > 1) {
      expect(namesAsc[0]).not.toBe(namesDesc[0]);
    }
  });

  test("navigates to collection detail on row click", async ({
    page,
    dataFactory,
  }) => {
    await dataFactory.createCollection();

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    // Wait for rows before clicking
    await collectionsPage.waitForRows();

    await collectionsPage.clickRow(0);

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/collections/.+`));
  });

  test("deletes a collection with confirmation dialog", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection({
      displayName: "ToDeleteCollection",
    });
    const collectionName = collection.attributes.name as string;

    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();
    await collectionsPage.waitForTableLoaded();

    // Wait for rows to load
    await collectionsPage.waitForRows();

    // Filter to isolate our test collection
    await collectionsPage.filterByName(collectionName);

    // Wait for the filtered row to appear
    await expect(
      page.locator('[data-testid^="collection-row-"]').first(),
    ).toBeVisible({ timeout: 10_000 });

    const initialCount = await collectionsPage.getRowCount();
    expect(initialCount).toBeGreaterThan(0);

    // Click delete on the first row
    await collectionsPage.clickDelete(0);

    // Confirm the deletion and wait for the DELETE API call to complete
    const deletePromise = page.waitForResponse(
      (resp) =>
        resp.request().method() === "DELETE" &&
        resp.url().includes("/api/collections/"),
    );
    await collectionsPage.confirmDelete();
    await deletePromise;

    // Reload the page to get fresh data — React Query's filtered-list
    // cache may not invalidate automatically after the mutation.
    await page.reload();
    await collectionsPage.waitForTableLoaded();
    await collectionsPage.filterByName(collectionName);

    // The deleted collection should no longer appear in the filtered list
    const rowLocator = page.locator('[data-testid^="collection-row-"]');
    await expect(rowLocator).toHaveCount(0, { timeout: 10_000 });
  });
});
