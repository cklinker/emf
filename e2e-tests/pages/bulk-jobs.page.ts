import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class BulkJobsPage extends BasePage {
  readonly bulkJobsPage: Locator;
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formOverlay: Locator;
  readonly formModal: Locator;
  readonly collectionIdInput: Locator;
  readonly operationInput: Locator;
  readonly batchSizeInput: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.bulkJobsPage = this.testId('bulk-jobs-page');
    this.createButton = this.testId('add-bulk-job-button');
    this.table = this.testId('bulk-jobs-table');
    this.formOverlay = this.testId('bulk-job-form-overlay');
    this.formModal = this.testId('bulk-job-form-modal');
    this.collectionIdInput = this.testId('bulk-job-collection-id-input');
    this.operationInput = this.testId('bulk-job-operation-input');
    this.batchSizeInput = this.testId('bulk-job-batch-size-input');
    this.submitButton = this.testId('bulk-job-form-submit');
    this.cancelButton = this.testId('bulk-job-form-cancel');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/bulk-jobs'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm(data: {
    collectionId?: string;
    operation?: string;
    batchSize?: string;
  }): Promise<void> {
    if (data.collectionId)
      await this.collectionIdInput.fill(data.collectionId);
    if (data.operation) await this.operationInput.fill(data.operation);
    if (data.batchSize) await this.batchSizeInput.fill(data.batchSize);
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.table
      .locator('[data-testid^="bulk-job-row-"]')
      .count();
  }

  async clickAbort(index: number): Promise<void> {
    await this.testId(`bulk-job-row-${index}`)
      .getByRole('button', { name: /abort/i })
      .click();
  }
}
