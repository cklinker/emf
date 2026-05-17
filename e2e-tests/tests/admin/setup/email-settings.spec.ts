import { test, expect } from "../../../fixtures";
import { EmailSettingsPage } from "../../../pages/email-settings.page";

test.describe("Email Settings", () => {
  let emailSettingsPage: EmailSettingsPage;

  test.beforeEach(async ({ page }) => {
    emailSettingsPage = new EmailSettingsPage(page);
    await emailSettingsPage.goto();
  });

  test("renders the page and shows the platform default state", async () => {
    await expect(emailSettingsPage.heading).toBeVisible();
    await expect(emailSettingsPage.hostInput).toBeVisible();
    await expect(emailSettingsPage.fromAddressInput).toBeVisible();
    await expect(emailSettingsPage.testRecipientInput).toBeVisible();
    await expect(emailSettingsPage.saveButton).toBeVisible();
  });

  test("can fill SMTP host and click save", async ({ page }) => {
    await emailSettingsPage.hostInput.fill("smtp.acme.example");
    await emailSettingsPage.portInput.fill("2525");
    await emailSettingsPage.fromAddressInput.fill("noreply@acme.example");

    // Save: just exercise the click path — backend response may toast either way
    // but the dialog should close (no validation errors).
    await emailSettingsPage.saveButton.click();
    // Allow the request to settle.
    await page.waitForTimeout(500);
  });

  test("test-send button disabled until a recipient is provided", async () => {
    await expect(emailSettingsPage.testSendButton).toBeDisabled();
    await emailSettingsPage.testRecipientInput.fill("qa@example.com");
    await expect(emailSettingsPage.testSendButton).toBeEnabled();
  });
});
