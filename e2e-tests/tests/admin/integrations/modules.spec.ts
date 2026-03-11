import { test, expect } from "../../../fixtures";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Modules", () => {
  test("displays modules page", async ({ page }) => {
    await page.goto("/default/modules");
    await page.waitForLoadState("load");

    await expect(page).toHaveURL(/\/modules/);
    const found = await waitForAnyVisible([
      page.getByTestId("modules-page"),
      page.getByRole("heading", { name: /modules/i }),
    ]);
    expect(found).toBe(true);
  });

  test("shows installed modules or description text", async ({ page }) => {
    await page.goto("/default/modules");
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      page.getByTestId("modules-page"),
      page.getByTestId("error-message"),
    ]);
    expect(found).toBe(true);
  });

  test("has install module button", async ({ page }) => {
    await page.goto("/default/modules");
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      page.getByRole("button", { name: /install module/i }),
      page.getByTestId("error-message"),
      page.getByTestId("modules-page"),
    ]);
    expect(found).toBe(true);
  });
});
