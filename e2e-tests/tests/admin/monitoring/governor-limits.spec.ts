import { test, expect } from "../../../fixtures";
import { GovernorLimitsPage } from "../../../pages/governor-limits.page";

test.describe("Governor Limits", () => {
  let governorLimitsPage: GovernorLimitsPage;

  test.beforeEach(async ({ page }) => {
    governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();
  });

  test("displays governor limits page", async () => {
    await expect(governorLimitsPage.governorLimitsPage).toBeVisible();
  });

  test("shows metric cards", async ({ page }) => {
    const cardsOrEmpty = governorLimitsPage.metricCards.or(
      page.getByTestId("empty-state"),
    );
    await expect(cardsOrEmpty).toBeVisible();
  });

  test("shows limits table or empty state", async ({ page }) => {
    const tableOrEmpty = governorLimitsPage.limitsTable.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has edit button for admin", async ({ page }) => {
    await page.waitForLoadState("networkidle");
    await expect(governorLimitsPage.editButton).toBeVisible();
  });
});
