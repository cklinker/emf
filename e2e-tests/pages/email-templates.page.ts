import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class EmailTemplatesPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly subjectInput: Locator;
  /**
   * The TipTap editor's contenteditable surface. Use {@link fillBodyHtml} to
   * write content; the saved `bodyHtml` is the editor's serialized HTML.
   */
  readonly bodyHtmlEditor: Locator;
  /** Toggle that switches the body editor to a raw HTML source textarea. */
  readonly bodyHtmlSourceToggle: Locator;
  /** Raw HTML textarea visible after toggling source mode on. */
  readonly bodyHtmlSource: Locator;
  /** Toolbar button that opens the field/function picker for the body. */
  readonly bodyInsertFieldButton: Locator;
  /** Inline button that opens the field/function picker for the subject. */
  readonly subjectInsertFieldButton: Locator;
  readonly bodyTextInput: Locator;
  readonly activeCheckbox: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;
  readonly relatedCollectionSelect: Locator;
  readonly previewIframe: Locator;
  readonly previewSubject: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-email-template-button");
    this.table = this.testId("email-templates-table");
    this.formModal = this.testId("email-template-form-modal");
    this.nameInput = this.testId("email-template-name-input");
    this.subjectInput = this.testId("email-template-subject-input");
    this.bodyHtmlEditor = this.testId("email-template-body-html-content");
    this.bodyHtmlSourceToggle = this.testId(
      "email-template-body-html-html-toggle",
    );
    this.bodyHtmlSource = this.testId("email-template-body-html-html-source");
    this.bodyInsertFieldButton = this.testId(
      "email-template-body-html-insert-field",
    );
    this.subjectInsertFieldButton = this.testId(
      "email-template-subject-insert-field",
    );
    this.bodyTextInput = this.testId("email-template-body-text-input");
    this.activeCheckbox = this.testId("email-template-active-input");
    this.submitButton = this.testId("email-template-form-submit");
    this.cancelButton = this.testId("email-template-form-cancel");
    this.relatedCollectionSelect = this.testId(
      "email-template-related-collection-input",
    );
    this.previewIframe = this.testId("email-template-preview-iframe");
    this.previewSubject = this.testId("email-template-preview-subject");
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

  /**
   * Replaces the body editor's contents. The string is set via the source-HTML
   * pane so it round-trips through the TipTap loader (which tokenises any
   * `{{merge tag}}` runs into chips).
   */
  async fillBodyHtml(html: string): Promise<void> {
    await this.bodyHtmlSourceToggle.click();
    await this.bodyHtmlSource.fill(html);
    await this.bodyHtmlSourceToggle.click();
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
      await this.fillBodyHtml(bodyHtml);
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
