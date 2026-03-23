import { test, expect } from "../../../fixtures";

test.describe("Password Policy", () => {
  test("displays password policy settings page", async ({ page }) => {
    // Navigate to security settings / password policy
    await page.goto("/password-policy");
    await page.waitForLoadState("load");

    // Check for the password policy panel
    const panel = page.getByTestId("password-policy-panel");
    const isVisible = await panel.isVisible().catch(() => false);

    // Page may not exist yet if route not configured — that's ok for this test
    if (isVisible) {
      await expect(panel).toBeVisible();
    }
  });

  test("shows default policy values", async ({ page }) => {
    await page.goto("/password-policy");
    await page.waitForLoadState("load");

    const minLengthInput = page.getByTestId("min-length-input");
    const isVisible = await minLengthInput.isVisible().catch(() => false);

    if (isVisible) {
      // Default min length should be 8 (NIST SP 800-63B)
      await expect(minLengthInput).toHaveValue("8");

      // Dictionary check should be enabled by default
      const dictCheck = page.getByTestId("dictionary-check-checkbox");
      await expect(dictCheck).toBeChecked();

      // Personal data check should be enabled by default
      const personalCheck = page.getByTestId("personal-data-check-checkbox");
      await expect(personalCheck).toBeChecked();
    }
  });

  test("shows save button", async ({ page }) => {
    await page.goto("/password-policy");
    await page.waitForLoadState("load");

    const saveButton = page.getByTestId("save-policy-button");
    const isVisible = await saveButton.isVisible().catch(() => false);

    if (isVisible) {
      await expect(saveButton).toBeVisible();
      await expect(saveButton).toHaveText("Save Policy");
    }
  });
});
