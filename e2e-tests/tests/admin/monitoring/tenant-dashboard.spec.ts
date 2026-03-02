import { test, expect } from "../../../fixtures";

// Skip: backend endpoint api/tenant-dashboard does not exist yet
test.describe.skip("Tenant Dashboard", () => {
  test("displays tenant dashboard page", async ({ page }) => {
    await page.goto("/default/setup/tenant-dashboard");
  });
});
