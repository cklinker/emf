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
    await scheduledJobsPage.waitForTableLoaded();
  });

  test("opens create scheduled job form", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();
    await scheduledJobsPage.waitForTableLoaded();

    await scheduledJobsPage.clickCreate();

    await expect(scheduledJobsPage.formModal).toBeVisible();
    await expect(scheduledJobsPage.nameInput).toBeVisible();
  });

  test("can fill out job form fields", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();
    await scheduledJobsPage.waitForTableLoaded();

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
    await scheduledJobsPage.waitForTableLoaded();

    await scheduledJobsPage.clickCreate();
    await expect(scheduledJobsPage.formModal).toBeVisible();

    // The name input is auto-focused. A direct Playwright click on the cancel
    // button triggers blur → validation error → layout shift, which can cause
    // the mouseup to miss the button. Use a programmatic click to bypass this.
    await scheduledJobsPage.cancelButton.evaluate((node) =>
      (node as HTMLButtonElement).click(),
    );
    await expect(scheduledJobsPage.formModal).not.toBeVisible({
      timeout: 5000,
    });
  });

  test("shows pause button for active jobs", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();
    await scheduledJobsPage.waitForTableLoaded();

    const rowCount = await scheduledJobsPage.getRowCount();
    if (rowCount > 0) {
      // Active jobs should show Pause button (or Resume if inactive)
      const pauseOrResume = page.getByTestId("pause-button-0").or(
        page.getByTestId("resume-button-0"),
      );
      await expect(pauseOrResume).toBeVisible();
    }
  });

  test("shows execution logs modal", async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();
    await scheduledJobsPage.waitForTableLoaded();

    const rowCount = await scheduledJobsPage.getRowCount();
    if (rowCount > 0) {
      await scheduledJobsPage.clickLogs(0);
      // Execution log modal should appear
      await expect(page.getByText("Scheduled Job Logs")).toBeVisible({
        timeout: 5000,
      });
    }
  });
});
