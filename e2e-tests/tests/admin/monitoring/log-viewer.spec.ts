import { test, expect } from "../../../fixtures";
import { LogViewerPage } from "../../../pages/log-viewer.page";

test.describe("Log Viewer", () => {
  let logViewerPage: LogViewerPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    logViewerPage = new LogViewerPage(page, tenantSlug);
    await logViewerPage.goto();
  });

  test("displays log viewer page with title", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /log viewer/i });
    await expect(heading).toBeVisible();
  });

  test("shows filter controls", async () => {
    await expect(logViewerPage.searchInput).toBeVisible();
    await expect(logViewerPage.levelFilter).toBeVisible();
    await expect(logViewerPage.serviceFilter).toBeVisible();
  });

  test("shows table or empty state", async ({ page }) => {
    const table = logViewerPage.table;
    const noEntries = page.getByText(/no log entries/i);
    const tableOrEmpty = table.or(noEntries);
    await expect(tableOrEmpty).toBeVisible();
  });

  test("can filter by log level", async () => {
    await logViewerPage.filterByLevel("ERROR");
    await logViewerPage.page.waitForTimeout(500);
  });

  test("can search logs", async () => {
    await logViewerPage.search("exception");
    await logViewerPage.page.waitForTimeout(500);
  });

  test("navigates from setup page", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/setup`);
    await page.waitForLoadState("networkidle");
    await page.getByText("Log Viewer", { exact: true }).click();
    await page.waitForURL(`**/${tenantSlug}/logs`);
    const heading = page.getByRole("heading", { name: /log viewer/i });
    await expect(heading).toBeVisible();
  });
});
