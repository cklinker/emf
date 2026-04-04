import { test, expect } from "../../../fixtures";
import { CollectionDetailPage } from "../../../pages/collection-detail.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Collection Detail", () => {
  test("displays collection detail page with fields", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection({
      displayName: `Detail Test ${Date.now()}`,
    });

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await expect(detailPage.container).toBeVisible();
  });

  test("shows collection title and name", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection({
      displayName: `Title Test ${Date.now()}`,
    });

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await expect(detailPage.collectionTitle).toBeVisible();
  });

  test("shows fields panel", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();

    await dataFactory.addField(collection.id, {
      name: "name",
      displayName: "Name",
      type: "string",
    });

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await expect(detailPage.fieldsPanel).toBeVisible();
  });

  test("shows add field button", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await expect(detailPage.addFieldButton).toBeVisible();
  });

  test("shows edit and delete buttons", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await expect(detailPage.editButton).toBeVisible();
    await expect(detailPage.deleteButton).toBeVisible();
  });

  test("navigates to edit page on edit click", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await detailPage.clickEdit();

    await expect(page).toHaveURL(
      new RegExp(`/${tenantSlug}/collections/${collection.id}/edit`),
    );
  });

  test("lists fields added to collection", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();

    await dataFactory.addField(collection.id, {
      name: "first_name",
      displayName: "First Name",
      type: "string",
    });

    await dataFactory.addField(collection.id, {
      name: "email",
      displayName: "Email",
      type: "string",
    });

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await detailPage.waitForFieldRows();

    const fieldCount = await detailPage.getFieldCount();
    expect(fieldCount).toBeGreaterThanOrEqual(2);

    const fieldNames = await detailPage.getFieldNames();
    expect(fieldNames.some((n) => n.toLowerCase().includes("first_name"))).toBe(
      true,
    );
    expect(fieldNames.some((n) => n.toLowerCase().includes("email"))).toBe(
      true,
    );
  });
});
