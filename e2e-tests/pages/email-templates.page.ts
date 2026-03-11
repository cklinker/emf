import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class EmailTemplatesPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly subjectInput: Locator;
  readonly bodyHtmlInput: Locator;
  readonly bodyTextInput: Locator;
  readonly activeCheckbox: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-email-template-button");
    this.table = this.testId("email-templates-table");
    this.formModal = this.testId("email-template-form-modal");
    this.nameInput = this.testId("email-template-name-input");
    this.subjectInput = this.testId("email-template-subject-input");
    this.bodyHtmlInput = this.testId("email-template-body-html-input");
    this.bodyTextInput = this.testId("email-template-body-text-input");
    this.activeCheckbox = this.testId("email-template-active-input");
    this.submitButton = this.testId("email-template-form-submit");
    this.cancelButton = this.testId("email-template-form-cancel");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/email-templates"));
    await this.waitForLoadingComplete();
  }

  async waitForTableLoaded(): Promise<void> {
    const tableOrEmpty = this.table.or(this.page.getByTestId("empty-state"));
    await this.waitForContentReady(tableOrEmpty);
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm({
    name,
    subject,
    bodyHtml,
  }: {
    name: string;
    subject?: string;
    bodyHtml?: string;
  }): Promise<void> {
    await this.nameInput.fill(name);
    if (subject !== undefined) {
      await this.subjectInput.fill(subject);
    }
    if (bodyHtml !== undefined) {
      await this.bodyHtmlInput.fill(bodyHtml);
    }
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="email-template-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }
}
