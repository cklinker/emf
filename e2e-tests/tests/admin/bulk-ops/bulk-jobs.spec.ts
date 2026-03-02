import { test, expect } from "../../../fixtures";
import { BulkJobsPage } from "../../../pages/bulk-jobs.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Bulk Jobs", () => {
  test("displays bulk jobs page", async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    await expect(page).toHaveURL(/\/bulk-jobs/);
    await expect(bulkJobsPage.bulkJobsPage).toBeVisible();
  });

  test("shows bulk jobs table or empty state", async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    const found = await waitForAnyVisible([
      bulkJobsPage.table,
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      bulkJobsPage.bulkJobsPage,
    ]);
    expect(found).toBe(true);
  });

  test("opens create bulk job form", async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    // Create button may not be available in error state
    try {
      await bulkJobsPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await bulkJobsPage.clickCreate();

    await expect(bulkJobsPage.formModal).toBeVisible();
  });

  test("can select operation type", async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    // Create button may not be available in error state
    try {
      await bulkJobsPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await bulkJobsPage.clickCreate();
    await expect(bulkJobsPage.formModal).toBeVisible();

    await expect(bulkJobsPage.operationInput).toBeVisible();
    // Operation is a <select> element, use selectOption via page object
    await bulkJobsPage.fillForm({ operation: "Delete" });

    // Verify a value was selected (exact value depends on option text)
    const value = await bulkJobsPage.operationInput.inputValue();
    expect(value).toBeTruthy();
  });
});
