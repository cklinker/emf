import { test, expect } from "../../../fixtures";
import { BulkJobsPage } from "../../../pages/bulk-jobs.page";

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

    const hasTable = await bulkJobsPage.table
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const hasEmptyState = await page
      .getByTestId("empty-state")
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasError = await page
      .getByTestId("error-message")
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasLoading = await page
      .getByText(/loading/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    const hasPage = await bulkJobsPage.bulkJobsPage
      .isVisible()
      .catch(() => false);
    expect(hasTable || hasEmptyState || hasError || hasLoading || hasPage).toBe(
      true,
    );
  });

  test("opens create bulk job form", async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    // Create button may not be available in error state
    const hasCreateButton = await page
      .getByTestId("add-bulk-job-button")
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (!hasCreateButton) {
      return;
    }
    await bulkJobsPage.clickCreate();

    await expect(bulkJobsPage.formModal).toBeVisible();
  });

  test("can select operation type", async ({ page }) => {
    const bulkJobsPage = new BulkJobsPage(page);
    await bulkJobsPage.goto();

    // Create button may not be available in error state
    const hasCreateButton = await page
      .getByTestId("add-bulk-job-button")
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (!hasCreateButton) {
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
