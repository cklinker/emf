import { test, expect } from "../../../fixtures";
import { SecurityAuditPage } from "../../../pages/security-audit.page";

test.describe("Security Audit", () => {
  let securityAuditPage: SecurityAuditPage;

  test.beforeEach(async ({ page }) => {
    securityAuditPage = new SecurityAuditPage(page);
    await securityAuditPage.goto();
  });

  test("displays security audit page", async () => {
    await expect(securityAuditPage.securityAuditPage).toBeVisible();
  });

  test("shows audit entries table or empty state", async ({ page }) => {
    const tableOrEmpty = securityAuditPage.table.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("can filter by date", async () => {
    await expect(securityAuditPage.dateFilter).toBeVisible();
    await securityAuditPage.filterByDate("2026-01-01");
    await securityAuditPage.waitForLoadingComplete();
  });

  test("can filter by type", async () => {
    await expect(securityAuditPage.typeFilter).toBeVisible();
  });
});
