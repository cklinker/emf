import { test, expect } from "../../../fixtures";
import { ScheduledJobsPage } from "../../../pages/scheduled-jobs.page";

test.describe("Scheduled Jobs", () => {
  test("displays scheduled jobs page", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await expect(page).toHaveURL(/\/scheduled-jobs/);
  });

  test("shows scheduled jobs table or empty state", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    const tableOrEmpty = scheduledJobsPage.table.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("opens create scheduled job form", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await scheduledJobsPage.clickCreate();

    await expect(scheduledJobsPage.formModal).toBeVisible();
    await expect(scheduledJobsPage.nameInput).toBeVisible();
  });

  test("can fill out job form fields", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await scheduledJobsPage.clickCreate();

    // Fill name and cron fields (type is a <select>, use selectOption via page object)
    await scheduledJobsPage.nameInput.fill("Test Scheduled Job");
    await scheduledJobsPage.cronInput.fill("0 0 * * *");

    // Select job type from the dropdown (it's a <select> element)
    await scheduledJobsPage.typeInput.selectOption({ index: 1 });

    await expect(scheduledJobsPage.nameInput).toHaveValue("Test Scheduled Job");
    await expect(scheduledJobsPage.cronInput).toHaveValue("0 0 * * *");
  });

  test("closes form on cancel", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await scheduledJobsPage.clickCreate();
    await expect(scheduledJobsPage.formModal).toBeVisible();

    await scheduledJobsPage.cancelButton.click();
    // Allow time for the modal to close
    await page.waitForTimeout(500);
    await expect(scheduledJobsPage.formModal).not.toBeVisible({
      timeout: 5000,
    });
  });
});
