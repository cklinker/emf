import { test, expect } from "../../../fixtures";
import { RequestLogPage } from "../../../pages/request-log.page";

test.describe("Request Log", () => {
  let requestLogPage: RequestLogPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    requestLogPage = new RequestLogPage(page, tenantSlug);
    await requestLogPage.goto();
  });

  test("displays request log page with title", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /request log/i });
    await expect(heading).toBeVisible();
  });

  test("shows filter controls", async () => {
    await expect(requestLogPage.methodFilter).toBeVisible();
    await expect(requestLogPage.statusFilter).toBeVisible();
    await expect(requestLogPage.pathSearch).toBeVisible();
  });

  test("shows time range selector", async () => {
    await expect(requestLogPage.dateRange).toBeVisible();
  });

  test("shows table or empty state", async ({ page }) => {
    const table = requestLogPage.table;
    const noEntries = page.getByText(/no request log entries/i);
    const tableOrEmpty = table.or(noEntries);
    await expect(tableOrEmpty).toBeVisible();
  });

  test("can filter by method", async () => {
    await requestLogPage.filterByMethod("GET");
    await requestLogPage.page.waitForTimeout(500);
  });

  test("can filter by status", async () => {
    await requestLogPage.filterByStatus("2xx");
    await requestLogPage.page.waitForTimeout(500);
  });

  test("can search by path", async () => {
    await requestLogPage.searchByPath("/api/collections");
    await requestLogPage.page.waitForTimeout(500);
  });

  test("navigates from setup page", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/setup`);
    await page.waitForLoadState("networkidle");
    await page.getByText("Request Log", { exact: true }).click();
    await page.waitForURL(`**/${tenantSlug}/request-log`);
    const heading = page.getByRole("heading", { name: /request log/i });
    await expect(heading).toBeVisible();
  });
});
