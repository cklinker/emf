import { test, expect } from "../../fixtures";
import { ObjectDetailPage } from "../../pages/end-user/object-detail.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

/**
 * Left section navigator on the record detail page.
 *
 * Seeds a collection with a two-section DETAIL page layout, then verifies:
 * - the sticky left panel lists each layout section with its field count
 * - the Activity timeline is docked in the left panel below the section
 *   list (desktop), not in the main column
 * - clicking a section entry scrolls its section into view
 */
test.describe("Record detail — section nav", () => {
  test("lists layout sections and jumps to sections and activity", async ({
    page,
    dataFactory,
  }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    const title = await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });
    const summary = await dataFactory.addField(collection.id, {
      name: "summary",
      displayName: "Summary",
      type: "string",
    });
    const notes = await dataFactory.addField(collection.id, {
      name: "notes",
      displayName: "Notes",
      type: "string",
    });
    await dataFactory.waitForStorageReady(collectionName);

    await dataFactory.createDetailLayout(collection.id, [
      {
        heading: "Overview",
        fields: [{ fieldId: title.id }, { fieldId: summary.id }],
      },
      {
        heading: "Extra",
        fields: [{ fieldId: notes.id }],
      },
    ]);

    const record = await dataFactory.createRecord(collectionName, {
      title: "Section nav record",
      summary: "First section",
      notes: "Second section",
    });

    const detailPage = new ObjectDetailPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await detailPage.goto();
    await expect(detailPage.fieldValues).toBeVisible();

    // The left nav lists both layout sections with their field counts
    await expect(detailPage.sectionNav).toBeVisible();
    const sectionEntries = detailPage.sectionNav.locator(
      '[data-testid^="section-nav-record-section-"]',
    );
    await expect(sectionEntries).toHaveCount(2);
    await expect(sectionEntries.nth(0)).toContainText("Overview");
    await expect(sectionEntries.nth(0)).toContainText("2");
    await expect(sectionEntries.nth(1)).toContainText("Extra");
    await expect(sectionEntries.nth(1)).toContainText("1");

    // The Activity timeline is docked in the left panel below the section
    // list; the main-column copy is hidden at desktop widths
    await expect(
      detailPage.sectionNavActivity.getByTestId("activity-timeline"),
    ).toBeVisible();
    await expect(
      detailPage.fieldValues.getByTestId("activity-timeline"),
    ).toBeHidden();

    // Clicking a section entry scrolls that section into view
    await sectionEntries.nth(1).click();
    await expect(
      page.locator('[id^="record-section-"]').nth(1),
    ).toBeInViewport();
  });

  test("hides the section nav when the collection has no page layout", async ({
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
    const record = await dataFactory.createRecord(collectionName, {
      title: "No layout record",
    });

    const detailPage = new ObjectDetailPage(
      page,
      collectionName,
      record.id,
      tenantSlug,
    );
    await detailPage.goto();
    await expect(detailPage.fieldValues).toBeVisible();

    // No layout sections → no left nav, but Activity still renders in the body
    await expect(detailPage.sectionNav).toHaveCount(0);
    await expect(
      detailPage.fieldValues.getByTestId("activity-timeline"),
    ).toBeVisible();
  });
});
