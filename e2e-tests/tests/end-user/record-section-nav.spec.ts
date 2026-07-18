import { test, expect } from "../../fixtures";
import { ObjectDetailPage } from "../../pages/end-user/object-detail.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

/**
 * Left section navigator on the record detail page.
 *
 * Seeds a collection with a two-section DETAIL page layout, then verifies:
 * - the sticky left nav lists each layout section with its field count
 * - the Activity entry sits below the section list and jumps to the
 *   activity timeline, which now renders in the main column below the
 *   field sections (not below the tab bar)
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

    // Activity renders in the main column (inside the record body, above the
    // tab bar) rather than at the bottom of the page
    await expect(
      detailPage.fieldValues.getByTestId("activity-timeline"),
    ).toBeVisible();

    // The Activity nav entry sits below the sections and jumps to the timeline
    const activityEntry = detailPage.sectionNavEntry("record-activity");
    await expect(activityEntry).toBeVisible();
    await activityEntry.click();
    await expect(detailPage.activityTimeline).toBeInViewport();

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
