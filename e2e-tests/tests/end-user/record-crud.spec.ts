import { test, expect } from "../../fixtures";
import { ObjectDetailPage } from "../../pages/end-user/object-detail.page";
import { ObjectFormPage } from "../../pages/end-user/object-form.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

async function setupCollection(dataFactory: {
  createCollection: (overrides?: Record<string, unknown>) => Promise<{
    id: string;
    attributes: Record<string, unknown>;
  }>;
  addField: (
    collectionId: string,
    field: {
      name: string;
      displayName: string;
      type: string;
      required?: boolean;
    },
  ) => Promise<unknown>;
  waitForStorageReady: (collectionName: string) => Promise<void>;
}) {
  const collection = await dataFactory.createCollection();
  const collectionName = collection.attributes.name as string;

  await dataFactory.addField(collection.id, {
    name: "title",
    displayName: "Title",
    type: "string",
    required: true,
  });

  // Wait for the backend to finish provisioning storage for this collection
  await dataFactory.waitForStorageReady(collectionName);

  return collectionName;
}

test.describe("Record CRUD", () => {
  test("creates a new record via form", async ({ page, dataFactory }) => {
    const collectionName = await setupCollection(dataFactory);

    const formPage = new ObjectFormPage(
      page,
      collectionName,
      undefined,
      tenantSlug,
    );
    await formPage.goto("new");

    await expect(formPage.formFields).toBeVisible();
    await formPage.fillField("title", `E2E Record ${Date.now()}`);
    await formPage.save();

    // After save, should navigate away from the form
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));
  });

  test("views record detail page", async ({ page, dataFactory }) => {
    const collectionName = await setupCollection(dataFactory);

    const record = await dataFactory.createRecord(collectionName, {
      title: `Detail Test ${Date.now()}`,
    });

    const detailPage = new ObjectDetailPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await detailPage.goto();

    await expect(detailPage.fieldValues).toBeVisible();
    await expect(detailPage.editButton).toBeVisible();
    // Delete is inside the "More actions" dropdown — verify the trigger is visible
    await expect(
      page.getByRole("button", { name: /more actions/i }),
    ).toBeVisible();
  });

  test("edits an existing record", async ({ page, dataFactory }) => {
    const collectionName = await setupCollection(dataFactory);

    const record = await dataFactory.createRecord(collectionName, {
      title: `Edit Test ${Date.now()}`,
    });

    const formPage = new ObjectFormPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await formPage.goto("edit");

    await expect(formPage.formFields).toBeVisible();
    await formPage.fillField("title", `Updated Record ${Date.now()}`);
    await formPage.save();

    // After save, should navigate to detail or list
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));
  });

  test("deletes a record with confirmation", async ({ page, dataFactory }) => {
    const collectionName = await setupCollection(dataFactory);

    const record = await dataFactory.createRecord(collectionName, {
      title: `Delete Test ${Date.now()}`,
    });

    const detailPage = new ObjectDetailPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await detailPage.goto();

    await detailPage.clickDelete();

    // Confirm the deletion in the dialog
    await detailPage.confirmDialog();

    // Should navigate back to the list after deletion
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));
  });
});
