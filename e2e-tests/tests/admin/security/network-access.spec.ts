import { test, expect } from "../../../fixtures";

/**
 * Tenant IP allowlist (Network Access) admin page.
 *
 * Keeps the shared `default` tenant unrestricted: the save flow persists a CIDR with the
 * restriction toggle OFF, so no request is ever blocked for other e2e specs.
 */
test.describe("Network Access", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/network-access");
    await page.waitForLoadState("load");
  });

  test("renders the network access page", async ({ page }) => {
    await expect(page.getByTestId("network-access-page")).toBeVisible();
    await expect(page.getByRole("heading", { name: /network access/i })).toBeVisible();
  });

  test("validates CIDR input", async ({ page }) => {
    const input = page.getByTestId("cidr-input");
    await input.fill("not-a-cidr");
    await expect(page.getByTestId("add-cidr")).toBeDisabled();
    await expect(page.getByTestId("cidr-error")).toBeVisible();

    await input.fill("10.0.0.0/8");
    await expect(page.getByTestId("add-cidr")).toBeEnabled();
    await expect(page.getByTestId("cidr-error")).toBeHidden();
  });

  test("adds and removes a range", async ({ page }) => {
    await page.getByTestId("cidr-input").fill("192.168.10.0/24");
    await page.getByTestId("add-cidr").click();
    await expect(page.getByText("192.168.10.0/24")).toBeVisible();

    await page.getByTestId("remove-cidr").last().click();
    await expect(page.getByText("192.168.10.0/24")).toBeHidden();
  });

  test("saves ranges with the restriction disabled", async ({ page }) => {
    // Restriction stays OFF (default) so the shared tenant is never locked down.
    await page.getByTestId("cidr-input").fill("203.0.113.0/24");
    await page.getByTestId("add-cidr").click();
    await expect(page.getByText("203.0.113.0/24")).toBeVisible();

    await page.getByTestId("save-network-access").click();
    await expect(page.getByText(/network access settings saved/i)).toBeVisible();
  });
});
