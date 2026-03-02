import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class ApprovalsPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;
  readonly collectionIdInput: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId('add-approval-process-button');
    this.table = this.testId('approval-processes-table');
    this.formModal = this.testId('approval-process-form-modal');
    this.nameInput = this.testId('approval-process-name-input');
    this.descriptionInput = this.testId('approval-process-description-input');
    this.collectionIdInput = this.testId(
      'approval-process-collection-id-input',
    );
    this.submitButton = this.testId('approval-process-form-submit');
    this.cancelButton = this.testId('approval-process-form-cancel');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/approvals'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm({
    name,
    description,
    collectionId,
  }: {
    name: string;
    description?: string;
    collectionId?: string;
  }): Promise<void> {
    await this.nameInput.fill(name);
    if (description !== undefined) {
      await this.descriptionInput.fill(description);
    }
    if (collectionId !== undefined) {
      await this.collectionIdInput.fill(collectionId);
    }
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.page
      .locator('[data-testid^="approval-process-row-"]')
      .count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }
}
