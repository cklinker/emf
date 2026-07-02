import { test, expect } from "../../../fixtures";
import { DeduplicationPage } from "../../../pages/deduplication.page";

test.describe("Deduplication", () => {
  test("renders the deduplication workbench", async ({ page }) => {
    const dedup = new DeduplicationPage(page);
    await dedup.goto();

    await expect(page).toHaveURL(/\/dedup/);
    await expect(dedup.container).toBeVisible();
    await expect(dedup.collectionSelect).toBeVisible();
  });

  test("scan is gated on a collection + match field, then shows results", async ({
    page,
  }) => {
    const dedup = new DeduplicationPage(page);
    await dedup.goto();

    // With nothing selected, scanning is disabled.
    await expect(dedup.scanButton).toBeDisabled();

    // Pick a collection and a match field, then scan.
    const collection = await dedup.selectFirstCollection();
    expect(collection).not.toEqual("");
    await expect(dedup.matchFields).toBeVisible();
    await dedup.pickFirstMatchField();
    await expect(dedup.scanButton).toBeEnabled();

    await dedup.scan();

    // A successful scan renders the results section (a group list or an empty-state).
    await expect(dedup.results).toBeVisible({ timeout: 15_000 });
  });
});
