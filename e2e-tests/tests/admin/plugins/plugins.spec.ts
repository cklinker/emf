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

    const contentOrEmpty = pluginsPage.pluginsPage.or(
      page.getByTestId("empty-state"),
    );
    await expect(contentOrEmpty).toBeVisible();
  });
});
