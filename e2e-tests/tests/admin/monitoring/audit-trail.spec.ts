import { test, expect } from "../../../fixtures";

test.describe("Audit Trail", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/audit-trail");
    await page.waitForLoadState("load");
  });

  test("displays audit trail page", async ({ page }) => {
    // Wait for page content or error to appear before asserting
    const heading = page.getByRole("heading", { name: /audit trail/i });
    const errorMessage = page.getByTestId("error-message");
    await expect(heading.or(errorMessage)).toBeVisible();
    await expect(heading).toBeVisible();
  });

  test("shows audit entries table or empty state", async ({ page }) => {
    const table = page.locator("table");
    const noEntries = page.getByText(/no audit trail entries/i);
    const errorMessage = page.getByTestId("error-message");
    await expect(table.or(noEntries).or(errorMessage)).toBeVisible();
  });

  test("has section filter", async ({ page }) => {
    // Wait for page to finish loading content before checking filter
    const heading = page.getByRole("heading", { name: /audit trail/i });
    const errorMessage = page.getByTestId("error-message");
    await expect(heading.or(errorMessage)).toBeVisible();

    const sectionFilter = page
      .locator("#section-filter")
      .or(page.getByRole("combobox", { name: /section/i }));
    await expect(sectionFilter).toBeVisible();
  });

  test("has entity type filter", async ({ page }) => {
    // Wait for page to finish loading content before checking filter
    const heading = page.getByRole("heading", { name: /audit trail/i });
    const errorMessage = page.getByTestId("error-message");
    await expect(heading.or(errorMessage)).toBeVisible();

    const entityFilter = page
      .locator("#entity-filter")
      .or(page.getByRole("textbox", { name: /entity type/i }));
    await expect(entityFilter).toBeVisible();
  });
});
