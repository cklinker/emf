import { test, expect } from "../../fixtures";
import { ObjectDetailPage } from "../../pages/end-user/object-detail.page";
import { ObjectFormPage } from "../../pages/end-user/object-form.page";
import type { DataFactory } from "../../helpers/data-factory";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

/**
 * Seed a collection (optionally with record versioning enabled) with two
 * text fields, and wait for storage to be provisioned.
 */
async function setupCollection(
  dataFactory: DataFactory,
  trackHistory: boolean,
): Promise<string> {
  const collection = await dataFactory.createCollection(
    trackHistory ? { trackHistory: true } : {},
  );
  const collectionName = collection.attributes.name as string;

  await dataFactory.addField(collection.id, {
    name: "title",
    displayName: "Title",
    type: "string",
    required: true,
  });
  await dataFactory.addField(collection.id, {
    name: "status",
    displayName: "Status",
    type: "string",
  });

  // Wait for the backend to finish provisioning storage for this collection
  await dataFactory.waitForStorageReady(collectionName);

  return collectionName;
}

test.describe("Record History", () => {
  test("tracks versions and shows them in the History tab", async ({
    page,
    dataFactory,
  }) => {
    const collectionName = await setupCollection(dataFactory, true);

    // v1 — CREATED (via API)
    const record = await dataFactory.createRecord(collectionName, {
      title: "History v1",
      status: "new",
    });

    // v2 — UPDATED (edit one field via the record form, as record-crud does)
    const formPage = new ObjectFormPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await formPage.goto("edit");
    await expect(formPage.formFields).toBeVisible();
    await formPage.fillField("title", "History v2");
    await formPage.save();
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));

    const detailPage = new ObjectDetailPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await detailPage.goto();
    await expect(detailPage.fieldValues).toBeVisible();

    // The collection tracks history, so the History tab is present
    await expect(detailPage.historyTab).toBeVisible();
    await detailPage.openHistoryTab();
    await expect(detailPage.historyPanel).toBeVisible();

    // Two versions, newest first (v2 on top)
    await expect(detailPage.versionRows).toHaveCount(2);
    await expect(detailPage.versionRows.first()).toHaveAttribute(
      "data-version",
      "2",
    );
    await expect(detailPage.versionRows.last()).toHaveAttribute(
      "data-version",
      "1",
    );

    // Drill into v2 — only the edited field carries a Changed badge
    await detailPage.versionRows.first().click();
    await expect(detailPage.versionDetail).toBeVisible();
    await expect(detailPage.changedBadges).toHaveCount(1);

    // Back to the version list
    await detailPage.historyBackButton.click();
    await expect(detailPage.versionRows).toHaveCount(2);

    // The activity timeline shows a click-through entry for the update
    const activityLink = detailPage.activityVersionLinks.filter({
      hasText: "Updated 1 field:",
    });
    await expect(activityLink).toBeVisible();

    // Clicking it opens the History tab at that version and scrolls the tab
    // bar into view (the switch used to happen off-screen and look like a no-op)
    await activityLink.click();
    await expect(detailPage.versionDetail).toBeVisible();
    await expect(detailPage.versionDetail).toBeInViewport();
  });

  test("shows no History tab for a collection without trackHistory", async ({
    page,
    dataFactory,
  }) => {
    const collectionName = await setupCollection(dataFactory, false);

    const record = await dataFactory.createRecord(collectionName, {
      title: "No History",
      status: "new",
    });

    const detailPage = new ObjectDetailPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await detailPage.goto();
    await expect(detailPage.fieldValues).toBeVisible();

    // System Information tab renders, but no History tab
    await expect(detailPage.testId("detail-tab-system")).toBeVisible();
    await expect(detailPage.historyTab).toHaveCount(0);
  });
});
