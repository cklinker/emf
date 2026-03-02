import { test, expect } from '../../../fixtures';
import { EmailTemplatesPage } from '../../../pages/email-templates.page';

test.describe('Email Templates', () => {
  test('displays email templates page', async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();

    await expect(page).toHaveURL(/\/email-templates/);
  });

  test('shows templates table', async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();

    await expect(templatesPage.table).toBeVisible();
  });

  test('opens create template form', async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();

    await templatesPage.clickCreate();

    await expect(templatesPage.formModal).toBeVisible();
    await expect(templatesPage.nameInput).toBeVisible();
    await expect(templatesPage.subjectInput).toBeVisible();
  });

  test('can fill out template form', async ({ page }) => {
    const templatesPage = new EmailTemplatesPage(page);
    await templatesPage.goto();

    await templatesPage.clickCreate();
    await templatesPage.fillForm({
      name: 'Welcome Email',
      subject: 'Welcome to EMF',
      bodyHtml: '<h1>Welcome</h1><p>Thank you for joining.</p>',
    });

    await expect(templatesPage.nameInput).toHaveValue('Welcome Email');
    await expect(templatesPage.subjectInput).toHaveValue('Welcome to EMF');
    await expect(templatesPage.bodyHtmlInput).toHaveValue(
      '<h1>Welcome</h1><p>Thank you for joining.</p>',
    );
  });
});
