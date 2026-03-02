import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class PermissionSetsListPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly editButtons: Locator;
  readonly deleteButtons: Locator;
  readonly cloneButtons: Locator;
  readonly formModal: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId('new-permission-set-button');
    this.table = this.testId('permission-sets-table');
    this.editButtons = this.page.locator('[data-testid^="edit-button-"]');
    this.deleteButtons = this.page.locator('[data-testid^="delete-button-"]');
    this.cloneButtons = this.page.locator('[data-testid^="clone-button-"]');
    this.formModal = this.testId('permission-set-form-modal');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/permission-sets'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm({
    name,
    description,
  }: {
    name: string;
    description?: string;
  }): Promise<void> {
    await this.testId('permission-set-name-input').fill(name);
    if (description !== undefined) {
      await this.testId('permission-set-description-input').fill(description);
    }
  }

  async submitForm(): Promise<void> {
    await this.testId('permission-set-form-submit').click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="permission-set-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }
}
