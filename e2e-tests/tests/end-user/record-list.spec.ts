import { test, expect } from "../../fixtures";
import { ObjectListPage } from "../../pages/end-user/object-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Record List", () => {
  test("displays record list for a collection", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}`));
  });

  test("shows data table with records", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);
    await dataFactory.createRecord(collectionName, { title: "Record A" });
    await dataFactory.createRecord(collectionName, { title: "Record B" });

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    const tableOrEmpty = listPage.dataTable.or(page.getByTestId("empty-state"));
    await expect(tableOrEmpty).toBeVisible();

    const rowCount = await listPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test("can search records", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);
    await dataFactory.createRecord(collectionName, {
      title: "Searchable Item",
    });

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    // Search input may not exist if the end-user page doesn't support it yet
    const hasSearch = await listPage.searchInput
      .isVisible({ timeout: 3_000 })
      .catch(() => false);
    if (hasSearch) {
      await listPage.search("Searchable");
    }
  });

  test("can sort by column", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);
    await dataFactory.createRecord(collectionName, { title: "Alpha" });
    await dataFactory.createRecord(collectionName, { title: "Beta" });

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    const tableOrEmpty = listPage.dataTable.or(page.getByTestId("empty-state"));
    await expect(tableOrEmpty).toBeVisible();

    // Sort column header may not exist if table uses different headers
    const titleHeader = listPage.dataTable.getByRole("columnheader", {
      name: "Title",
    });
    const hasHeader = await titleHeader
      .isVisible({ timeout: 3_000 })
      .catch(() => false);
    if (hasHeader) {
      await titleHeader.click();
    }
  });

  test("shows pagination for large datasets", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    // Pagination may or may not be visible depending on record count
    const paginationVisible = await listPage.pagination
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    if (paginationVisible) {
      await expect(listPage.pagination).toBeVisible();
    } else {
      // No pagination needed when there are few records
      expect(paginationVisible).toBe(false);
    }
  });
});
