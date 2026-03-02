import { test, expect } from "../../../fixtures";
import { AuditTrailPage } from "../../../pages/audit-trail.page";

test.describe("Audit Trail", () => {
  let auditTrailPage: AuditTrailPage;

  test.beforeEach(async ({ page }) => {
    auditTrailPage = new AuditTrailPage(page);
    await auditTrailPage.goto();
  });

  test("displays audit trail page", async () => {
    await expect(auditTrailPage.auditTrailPage).toBeVisible();
  });

  test("shows audit entries table or empty state", async ({ page }) => {
    const tableOrEmpty = auditTrailPage.table.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has date filter", async () => {
    await expect(auditTrailPage.dateFilter).toBeVisible();
  });

  test("has type filter", async () => {
    await expect(auditTrailPage.typeFilter).toBeVisible();
  });
});
