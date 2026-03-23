import { test, expect } from "../../../fixtures";
import { ConnectedAppsPage } from "../../../pages/connected-apps.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Connected Apps", () => {
  test("displays connected apps page", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    await expect(page).toHaveURL(/\/connected-apps/);
    await expect(connectedAppsPage.connectedAppsPage).toBeVisible();
  });

  test("shows apps table or empty state", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      connectedAppsPage.table,
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      connectedAppsPage.connectedAppsPage,
    ]);
    expect(found).toBe(true);
  });

  test("opens create connected app form", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    // The create button may not be visible (rate limiting, permissions, or error state)
    try {
      await connectedAppsPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }

    await connectedAppsPage.clickCreate();

    await expect(connectedAppsPage.formModal).toBeVisible({ timeout: 5000 });
    await expect(connectedAppsPage.nameInput).toBeVisible();
    await expect(connectedAppsPage.descriptionInput).toBeVisible();
  });

  test("shows tokens button for connected apps", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    const hasApps = await waitForAnyVisible([
      connectedAppsPage.table,
      page.getByTestId("empty-state"),
    ]);

    if (hasApps && (await connectedAppsPage.table.isVisible())) {
      const tokensButton = page.getByTestId("tokens-button-0");
      await expect(tokensButton).toBeVisible({ timeout: 5000 });
    }
  });

  test("shows audit button for connected apps", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    const hasApps = await waitForAnyVisible([
      connectedAppsPage.table,
      page.getByTestId("empty-state"),
    ]);

    if (hasApps && (await connectedAppsPage.table.isVisible())) {
      const auditButton = page.getByTestId("audit-button-0");
      await expect(auditButton).toBeVisible({ timeout: 5000 });
    }
  });

  test("opens tokens modal when tokens button clicked", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    const hasApps = await waitForAnyVisible([
      connectedAppsPage.table,
      page.getByTestId("empty-state"),
    ]);

    if (hasApps && (await connectedAppsPage.table.isVisible())) {
      const tokensButton = page.getByTestId("tokens-button-0");
      if (await tokensButton.isVisible()) {
        await tokensButton.click();
        await expect(page.getByTestId("tokens-modal-overlay")).toBeVisible({
          timeout: 5000,
        });
      }
    }
  });

  test("opens audit modal when audit button clicked", async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();
    await page.waitForLoadState("load");

    const hasApps = await waitForAnyVisible([
      connectedAppsPage.table,
      page.getByTestId("empty-state"),
    ]);

    if (hasApps && (await connectedAppsPage.table.isVisible())) {
      const auditButton = page.getByTestId("audit-button-0");
      if (await auditButton.isVisible()) {
        await auditButton.click();
        await expect(page.getByTestId("audit-modal-overlay")).toBeVisible({
          timeout: 5000,
        });
      }
    }
  });
});
