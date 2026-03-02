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

    // Check for either plugin cards or the empty state message within the plugins page
    // Note: both plugins-page and empty-state may exist simultaneously (empty-state inside plugins-page),
    // so we check for either a plugin card or the empty state text instead of using .or()
    const hasPluginCards =
      (await page.locator('[data-testid^="plugin-card-"]').count()) > 0;
    const hasEmptyState = await page
      .getByText(/no.*plugin/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    expect(hasPluginCards || hasEmptyState).toBe(true);
  });
});
