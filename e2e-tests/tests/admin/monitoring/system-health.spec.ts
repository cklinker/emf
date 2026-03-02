import { test, expect } from "../../../fixtures";

// Skip: backend endpoint api/admin/dashboard does not exist yet
test.describe.skip("System Health", () => {
  test("displays system health dashboard", async ({ page }) => {
    await page.goto("/default/setup/system-health");
  });
});
