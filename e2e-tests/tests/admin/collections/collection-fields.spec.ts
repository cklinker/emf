import { test, expect } from "../../../fixtures";
import { CollectionsListPage } from "../../../pages/collections-list.page";
import { CollectionDetailPage } from "../../../pages/collection-detail.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Collection Fields", () => {
  test("displays fields on collection detail page", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();
    await dataFactory.addField(collection.id, {
      name: "test_field",
      displayName: "Test Field",
      type: "string",
    });

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    // Wait for field rows to appear
    await detailPage.waitForFieldRows();

    const fieldCount = await detailPage.getFieldCount();
    expect(fieldCount).toBeGreaterThan(0);
  });

  test("shows field types correctly", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    await dataFactory.addField(collection.id, {
      name: "string_field",
      displayName: "String Field",
      type: "string",
    });
    await dataFactory.addField(collection.id, {
      name: "number_field",
      displayName: "Number Field",
      type: "number",
    });
    await dataFactory.addField(collection.id, {
      name: "boolean_field",
      displayName: "Boolean Field",
      type: "boolean",
    });

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    // Wait for field rows to appear
    await detailPage.waitForFieldRows();

    const fieldNames = await detailPage.getFieldNames();
    expect(fieldNames).toContain("string_field");
    expect(fieldNames).toContain("number_field");
    expect(fieldNames).toContain("boolean_field");
  });

  test("navigates to collection detail from collections list", async ({
    page,
    dataFactory,
  }) => {
    await dataFactory.createCollection();

    const listPage = new CollectionsListPage(page, tenantSlug);
    await listPage.goto();
    await listPage.waitForTableLoaded();

    // Wait for rows before clicking
    await listPage.waitForRows();

    await listPage.clickRow(0);

    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await expect(detailPage.container).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/collections/.+`));
  });
});
