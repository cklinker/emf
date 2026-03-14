import { test, expect } from "../../../fixtures";
import { PluginsPage } from "../../../pages/plugins.page";

test.describe("Plugins", () => {
  test("displays plugins page", async ({ page }) => {
    const pluginsPage = new PluginsPage(page);
    await pluginsPage.goto();

    await expect(page).toHaveURL(/\/plugins/);
    await expect(pluginsPage.pluginsPage).toBeVisible();
  });

  test("shows plugin count", async ({ page }) => {
    const pluginsPage = new PluginsPage(page);
    await pluginsPage.goto();

    await expect(pluginsPage.pluginsCount).toBeVisible();

    const countText = await pluginsPage.getPluginCount();
    expect(countText).not.toBeNull();
  });

  test("shows plugin cards or empty state", async ({ page }) => {
    const pluginsPage = new PluginsPage(page);
    await pluginsPage.goto();

    // After goto(), either the plugins page or "Insufficient Permissions" is visible.
    // If the plugins page is visible, check for plugin cards or empty state.
    if (await pluginsPage.pluginsPage.isVisible().catch(() => false)) {
      const hasPluginCards =
        (await page.locator('[data-testid^="plugin-card-"]').count()) > 0;
      const hasEmptyState = await pluginsPage.emptyState
        .isVisible()
        .catch(() => false);
      const hasEmptyText = await page
        .getByText(/no plugins/i)
        .first()
        .isVisible()
        .catch(() => false);

      expect(hasPluginCards || hasEmptyState || hasEmptyText).toBe(true);
    } else {
      // Permission denied — page is showing "Insufficient Permissions"
      await expect(
        page.getByText(/insufficient permissions/i),
      ).toBeVisible();
    }
  });
});
