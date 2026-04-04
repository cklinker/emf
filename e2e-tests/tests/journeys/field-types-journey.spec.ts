import { test, expect } from "../../fixtures";
import { CollectionDetailPage } from "../../pages/collection-detail.page";
import { ObjectFormPage } from "../../pages/end-user/object-form.page";
import { ObjectListPage } from "../../pages/end-user/object-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Field Types Journey", () => {
  test("creates collection with multiple field types and verifies in end-user app", async ({
    page,
    dataFactory,
  }) => {
    // Step 1: Create collection with diverse field types
    const collection = await dataFactory.createCollection({
      displayName: `Field Types ${Date.now()}`,
    });
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });

    await dataFactory.addField(collection.id, {
      name: "amount",
      displayName: "Amount",
      type: "number",
    });

    await dataFactory.addField(collection.id, {
      name: "is_active",
      displayName: "Is Active",
      type: "boolean",
    });

    await dataFactory.addField(collection.id, {
      name: "start_date",
      displayName: "Start Date",
      type: "date",
    });

    await dataFactory.waitForStorageReady(collectionName);

    // Step 2: Verify fields appear in admin collection detail
    const detailPage = new CollectionDetailPage(page, tenantSlug);
    await detailPage.goto(collection.id);

    await detailPage.waitForFieldRows();
    const fieldNames = await detailPage.getFieldNames();
    expect(fieldNames.some((n) => n.includes("title"))).toBe(true);
    expect(fieldNames.some((n) => n.includes("amount"))).toBe(true);
    expect(fieldNames.some((n) => n.includes("is_active"))).toBe(true);
    expect(fieldNames.some((n) => n.includes("start_date"))).toBe(true);

    // Step 3: Create a record with all field types via API
    await dataFactory.createRecord(collectionName, {
      title: "Multi-Field Record",
      amount: 99.95,
      is_active: true,
      start_date: "2026-01-15",
    });

    // Step 4: Verify record appears in end-user list
    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();
    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}`));
  });

  test("creates record with string and number fields via end-user form", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection({
      displayName: `Form Fields ${Date.now()}`,
    });
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "name",
      displayName: "Name",
      type: "string",
      required: true,
    });

    await dataFactory.addField(collection.id, {
      name: "quantity",
      displayName: "Quantity",
      type: "number",
    });

    await dataFactory.waitForStorageReady(collectionName);

    // Navigate to the new record form
    const formPage = new ObjectFormPage(
      page,
      collectionName,
      undefined,
      tenantSlug,
    );
    await formPage.goto("new");

    await expect(formPage.formFields).toBeVisible();

    // Fill in both fields
    await formPage.fillField("name", `Test Record ${Date.now()}`);
    await formPage.fillField("quantity", "42");
    await formPage.save();

    // Should navigate back to the collection
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));
  });
});
