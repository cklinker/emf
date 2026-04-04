import { test, expect } from "../../../fixtures";
import { ResourceListPage } from "../../../pages/resource-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Resource Browser CRUD", () => {
  test("displays resource list for a collection", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });

    await dataFactory.waitForStorageReady(collectionName);

    const resourcePage = new ResourceListPage(page, collectionName, tenantSlug);
    await resourcePage.goto();

    await expect(page).toHaveURL(
      new RegExp(`/resources/${collectionName}`),
    );
  });

  test("shows create button on resource list", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);

    const resourcePage = new ResourceListPage(page, collectionName, tenantSlug);
    await resourcePage.goto();

    await expect(resourcePage.createButton).toBeVisible({ timeout: 10_000 });
  });

  test("navigates to create form", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);

    const resourcePage = new ResourceListPage(page, collectionName, tenantSlug);
    await resourcePage.goto();

    await resourcePage.createButton.click();

    await expect(page).toHaveURL(
      new RegExp(`/resources/${collectionName}/new`),
    );
  });

  test("shows records in data table after creation", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);

    // Create a record via API
    await dataFactory.createRecord(collectionName, {
      title: `Resource Test ${Date.now()}`,
    });

    const resourcePage = new ResourceListPage(page, collectionName, tenantSlug);
    await resourcePage.goto();

    // Wait for data table to show rows
    const tableRow = resourcePage.dataTable.locator("tbody tr").first();
    await expect(tableRow).toBeVisible({ timeout: 15_000 });
  });
});
