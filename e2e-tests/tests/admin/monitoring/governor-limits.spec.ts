import { test, expect } from "../../../fixtures";
import { GovernorLimitsPage } from "../../../pages/governor-limits.page";

test.describe("Governor Limits", () => {
  test("displays governor limits page with heading", async ({ page }) => {
    const governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();

    await expect(governorLimitsPage.heading).toBeVisible({ timeout: 10_000 });
    await expect(governorLimitsPage.governorLimitsPage).toBeVisible();
  });

  test("shows usage metric cards", async ({ page }) => {
    const governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();

    await expect(governorLimitsPage.metricCards).toBeVisible({
      timeout: 10_000,
    });

    // Should display 3 metric cards: API Calls, Users, Collections
    const cardCount = await governorLimitsPage.getMetricCardCount();
    expect(cardCount).toBe(3);

    // Each metric card should be visible with usage data
    for (let i = 0; i < 3; i++) {
      await expect(governorLimitsPage.metricCard(i)).toBeVisible();
    }
  });

  test("displays all limits table with 7 rows", async ({ page }) => {
    const governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();

    await expect(governorLimitsPage.limitsTable).toBeVisible({
      timeout: 10_000,
    });

    // Table should have 7 limit rows
    const rowCount = await governorLimitsPage.getLimitRowCount();
    expect(rowCount).toBe(7);

    // Verify each expected limit row exists
    const expectedLimitKeys = [
      "apiCallsPerDay",
      "storageGb",
      "maxUsers",
      "maxCollections",
      "maxFieldsPerCollection",
      "maxWorkflows",
      "maxReports",
    ];

    for (const key of expectedLimitKeys) {
      await expect(governorLimitsPage.limitRow(key)).toBeVisible();
    }
  });

  test("metric cards display usage fraction and progress bar", async ({
    page,
  }) => {
    const governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();

    await expect(governorLimitsPage.metricCards).toBeVisible({
      timeout: 10_000,
    });

    // Each metric card should contain a "/" separator (used / limit format)
    const firstCard = governorLimitsPage.metricCard(0);
    await expect(firstCard.locator("text=/")).toBeVisible();

    // Each metric card should contain a percentage
    await expect(firstCard.getByText(/%/)).toBeVisible();
  });

  test("limits table shows limit values", async ({ page }) => {
    const governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();

    await expect(governorLimitsPage.limitsTable).toBeVisible({
      timeout: 10_000,
    });

    // Each limit row should have a visible value (number text)
    // The default values are > 0, so at least one cell should have a number
    const apiCallsRow = governorLimitsPage.limitRow("apiCallsPerDay");
    const apiCallsText = await apiCallsRow.locator("td").last().textContent();
    expect(apiCallsText?.trim()).toBeTruthy();

    // Storage row should have "GB" suffix
    const storageRow = governorLimitsPage.limitRow("storageGb");
    const storageText = await storageRow.locator("td").last().textContent();
    expect(storageText).toContain("GB");
  });
});
