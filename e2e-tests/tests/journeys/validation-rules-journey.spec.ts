import { test, expect } from "../../fixtures";
import { ObjectFormPage } from "../../pages/end-user/object-form.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Validation Rules Journey", () => {
  // This journey does a lot of sequential work on a freshly-created
  // collection (create collection + 2 fields, storage-ready barrier,
  // navigate the SPA, fill, submit, assert). On a cold CI compose stack
  // it intermittently exceeds the 45s default (passes locally and on
  // warm runs). The work is correct — give it realistic headroom rather
  // than flake. (Rollup journeys are NOT slow-but-correct; excluded.)
  test.describe.configure({ timeout: 90_000 });

  test("required field prevents empty form submission", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection({
      displayName: `Validation Test ${Date.now()}`,
    });
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });

    await dataFactory.addField(collection.id, {
      name: "notes",
      displayName: "Notes",
      type: "string",
    });

    await dataFactory.waitForStorageReady(collectionName);

    // Navigate to create form
    const formPage = new ObjectFormPage(
      page,
      collectionName,
      undefined,
      tenantSlug,
    );
    await formPage.goto("new");

    await expect(formPage.formFields).toBeVisible();

    // Fill only the optional field, leave required field empty
    await formPage.fillField("notes", "Some notes");

    // Attempt to save — should fail due to required field
    await formPage.save();

    // Should still be on the form page (not navigated away)
    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}/new`));
  });

  test("valid form submission succeeds", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection({
      displayName: `Valid Form ${Date.now()}`,
    });
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });

    await dataFactory.waitForStorageReady(collectionName);

    const formPage = new ObjectFormPage(
      page,
      collectionName,
      undefined,
      tenantSlug,
    );
    await formPage.goto("new");

    await expect(formPage.formFields).toBeVisible();

    // Fill the required field
    await formPage.fillField("title", `Valid Record ${Date.now()}`);
    await formPage.save();

    // Should navigate away from the form on success
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));
  });
});
