import { test, expect } from "../../../fixtures";

test.describe("Audit Trail", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/audit-trail");
    await page.waitForLoadState("networkidle");
  });

  test("displays audit trail page", async ({ page }) => {
    // The page renders with a heading containing "Audit Trail"
    const heading = page.getByRole("heading", { name: /audit trail/i });
    await expect(heading).toBeVisible();
  });

  test("shows audit entries table or empty state", async ({ page }) => {
    // The page shows a table or a "no entries" message
    const table = page.locator("table");
    const noEntries = page.getByText(/no audit trail entries/i);
    const tableOrEmpty = table.or(noEntries);
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has section filter", async ({ page }) => {
    const sectionFilter = page
      .locator("#section-filter")
      .or(page.getByRole("combobox", { name: /section/i }));
    await expect(sectionFilter).toBeVisible();
  });

  test("has entity type filter", async ({ page }) => {
    const entityFilter = page
      .locator("#entity-filter")
      .or(page.getByRole("textbox", { name: /entity type/i }));
    await expect(entityFilter).toBeVisible();
  });
});
