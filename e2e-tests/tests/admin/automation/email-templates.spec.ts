import { test, expect } from "../../../fixtures";
import { EmailTemplatesPage } from "../../../pages/email-templates.page";

test.describe("Email Templates", () => {
  test("displays email templates page", async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();

    await expect(page).toHaveURL(/\/email-templates/);
  });

  test("shows templates table or empty state", async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();
    await templatesPage.waitForTableLoaded();
  });

  test("opens create template form", async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();
    await templatesPage.waitForTableLoaded();

    await templatesPage.clickCreate();

    await expect(templatesPage.formModal).toBeVisible();
    await expect(templatesPage.nameInput).toBeVisible();
    await expect(templatesPage.subjectInput).toBeVisible();
    await expect(templatesPage.bodyHtmlEditor).toBeVisible();
    await expect(templatesPage.previewIframe).toBeVisible();
  });

  test("can fill out template form", async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();
    await templatesPage.waitForTableLoaded();

    await templatesPage.clickCreate();
    await templatesPage.fillForm({
      name: "Welcome Email",
      subject: "Welcome to EMF",
      bodyHtml: "<h1>Welcome</h1><p>Thank you for joining.</p>",
    });

    await expect(templatesPage.nameInput).toHaveValue("Welcome Email");
    await expect(templatesPage.subjectInput).toHaveValue("Welcome to EMF");
    await expect(templatesPage.bodyHtmlEditor).toContainText("Welcome");
    await expect(templatesPage.bodyHtmlEditor).toContainText(
      "Thank you for joining.",
    );
  });

  test("opens the field/function picker from the body toolbar", async ({
    page,
  }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();
    await templatesPage.waitForTableLoaded();
    await templatesPage.clickCreate();

    await templatesPage.bodyInsertFieldButton.click();
    const picker = page.getByTestId("field-expression-picker");
    await expect(picker).toBeVisible();

    // Switch to functions tab and pick UPPER — the preview should reflect the
    // assembled merge tag.
    await page.getByTestId("field-expression-picker-tab-functions").click();
    await page
      .getByTestId("field-expression-picker-functions-fn-UPPER")
      .click();
    await expect(page.getByTestId("field-expression-picker-preview")).toContainText(
      "UPPER",
    );

    await page.getByTestId("field-expression-picker-insert").click();
    await expect(picker).toBeHidden();
    await expect(templatesPage.bodyHtmlEditor).toContainText("UPPER");
  });

  test("opens the field picker from the subject row", async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();
    await templatesPage.waitForTableLoaded();
    await templatesPage.clickCreate();

    await templatesPage.subjectInsertFieldButton.click();
    await expect(page.getByTestId("field-expression-picker")).toBeVisible();
  });
});
