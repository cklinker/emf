import type { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page';

export class ObjectFormPage extends BasePage {
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly formFields: Locator;

  constructor(
    page: Page,
    private readonly collectionName: string,
    private readonly recordId?: string,
    tenantSlug?: string,
  ) {
    super(page, tenantSlug);
    this.saveButton = this.testId('save-button');
    this.cancelButton = this.testId('cancel-button');
    this.formFields = this.testId('form-fields');
  }

  async goto(mode: 'new' | 'edit' = 'new'): Promise<void> {
    const path =
      mode === 'new'
        ? `/app/o/${this.collectionName}/new`
        : `/app/o/${this.collectionName}/${this.recordId}/edit`;
    await this.page.goto(this.tenantUrl(path));
    await this.waitForLoadingComplete();
  }

  async fillField(name: string, value: string): Promise<void> {
    await this.testId(`field-${name}`).fill(value);
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
  }
}
