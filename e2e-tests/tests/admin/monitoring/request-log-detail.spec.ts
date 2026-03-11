import { test, expect } from "../../../fixtures";
import { RequestLogDetailPage } from "../../../pages/request-log-detail.page";

test.describe("Request Log Detail", () => {
  test("shows detail page with tabs", async ({ page, tenantSlug }) => {
    // Navigate to request log first
    await page.goto(`/${tenantSlug}/monitoring/requests`);
    await page.waitForLoadState("load");

    // If there are rows, click the first one
    const rows = page.locator('[data-testid^="request-log-row-"]');
    const rowCount = await rows.count();
    if (rowCount > 0) {
      await rows.first().click();
      await page.waitForURL(`**/${tenantSlug}/monitoring/requests/*`);

      const detailPage = new RequestLogDetailPage(page, tenantSlug);
      await expect(detailPage.summaryCard).toBeVisible();
      await expect(detailPage.requestTab).toBeVisible();
      await expect(detailPage.responseTab).toBeVisible();
      await expect(detailPage.traceTab).toBeVisible();
      await expect(detailPage.logsTab).toBeVisible();
    }
  });

  test("shows Jaeger trace link", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/monitoring/requests`);
    await page.waitForLoadState("load");

    const rows = page.locator('[data-testid^="request-log-row-"]');
    const rowCount = await rows.count();
    if (rowCount > 0) {
      await rows.first().click();
      await page.waitForURL(`**/${tenantSlug}/monitoring/requests/*`);

      const detailPage = new RequestLogDetailPage(page, tenantSlug);
      await expect(detailPage.jaegerLink).toBeVisible();
      const href = await detailPage.jaegerLink.getAttribute("href");
      expect(href).toContain("jaeger");
    }
  });
});
