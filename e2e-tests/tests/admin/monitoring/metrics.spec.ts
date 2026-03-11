import { test, expect } from "../../../fixtures";
import { MetricsPage } from "../../../pages/metrics.page";

test.describe("Metrics Page", () => {
  let metricsPage: MetricsPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    metricsPage = new MetricsPage(page, tenantSlug);
    await metricsPage.goto();
  });

  test("displays metrics page with title", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /monitoring/i });
    await expect(heading).toBeVisible();
  });

  test("shows summary cards", async () => {
    await expect(metricsPage.summaryCards).toBeVisible();
    const cardCount = await metricsPage.getSummaryCardCount();
    expect(cardCount).toBe(4);
  });

  test("shows time range selector with default selection", async () => {
    await expect(metricsPage.timeRangeToolbar).toBeVisible();
    const activeRange = await metricsPage.getActiveTimeRange();
    expect(activeRange).toBe("24h");
  });

  test("can change time range", async ({ page }) => {
    await metricsPage.selectTimeRange("7d");
    const button = page.getByTestId("overview-time-range-7d");
    await expect(button).toHaveClass(/bg-primary/);
  });

  test("shows route filter dropdown", async () => {
    await expect(metricsPage.routeFilter).toBeVisible();
  });

  test("displays all chart panels", async () => {
    const chartCount = await metricsPage.getChartCount();
    expect(chartCount).toBe(6);
  });

  test("charts have proper titles", async () => {
    const grid = metricsPage.chartGrid;
    await expect(grid.getByText("Request Rate")).toBeVisible();
    await expect(grid.getByText("Latency", { exact: true })).toBeVisible();
    await expect(grid.getByText("Errors", { exact: true })).toBeVisible();
    await expect(grid.getByText("Auth Failures")).toBeVisible();
    await expect(grid.getByText("Rate Limit Events")).toBeVisible();
  });

  test("navigates from setup page", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/setup`);
    await page.waitForLoadState("load");

    // Click the Monitoring item in the platform section
    await page.getByText("Monitoring", { exact: true }).click();
    await page.waitForURL(`**/${tenantSlug}/monitoring/overview`);

    const heading = page.getByRole("heading", { name: /monitoring/i });
    await expect(heading).toBeVisible();
  });
});
