import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class CampaignsPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly subjectInput: Locator;
  readonly targetCollectionSelect: Locator;
  readonly recipientFieldInput: Locator;
  readonly bodyInput: Locator;
  readonly fromNameInput: Locator;
  readonly fromAddressInput: Locator;
  readonly scheduledAtInput: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;
  readonly suppressionsSection: Locator;
  readonly suppressionEmailInput: Locator;
  readonly addSuppressionButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-campaign-button");
    this.table = this.testId("campaigns-table");
    this.formModal = this.testId("campaign-form-modal");
    this.nameInput = this.testId("campaign-name-input");
    this.subjectInput = this.testId("campaign-subject-input");
    this.targetCollectionSelect = this.testId(
      "campaign-target-collection-input",
    );
    this.recipientFieldInput = this.testId("campaign-recipient-field-input");
    this.bodyInput = this.testId("campaign-body-input");
    this.fromNameInput = this.testId("campaign-from-name-input");
    this.fromAddressInput = this.testId("campaign-from-address-input");
    this.scheduledAtInput = this.testId("campaign-scheduled-at-input");
    this.submitButton = this.testId("campaign-form-submit");
    this.cancelButton = this.testId("campaign-form-cancel");
    this.suppressionsSection = this.testId("suppressions-section");
    this.suppressionEmailInput = this.testId("suppression-email-input");
    this.addSuppressionButton = this.testId("add-suppression-button");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/campaigns"));
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
    targetCollection,
    recipientEmailField,
  }: {
    name: string;
    subject?: string;
    targetCollection?: string;
    recipientEmailField?: string;
  }): Promise<void> {
    await this.nameInput.fill(name);
    if (subject !== undefined) {
      await this.subjectInput.fill(subject);
    }
    if (targetCollection !== undefined) {
      await this.targetCollectionSelect.selectOption(targetCollection);
    }
    if (recipientEmailField !== undefined) {
      await this.recipientFieldInput.fill(recipientEmailField);
    }
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }
}
