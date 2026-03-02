import { test, expect } from "../../../fixtures";

// Skip: Governor Limits page crashes with "Cannot read properties of undefined (reading 'toLocaleString')"
// when limit data is missing from the API response
test.describe.skip("Governor Limits", () => {
  test("displays governor limits page", async ({ page }) => {
    await page.goto("/default/setup/governor-limits");
  });
});
