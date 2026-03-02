import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class ProfilesListPage extends BasePage {
  readonly container: Locator;
  readonly createButton: Locator;
  readonly profileTable: Locator;
  readonly emptyState: Locator;
  readonly formModal: Locator;
  readonly formNameInput: Locator;
  readonly formDescriptionInput: Locator;
  readonly formSubmitButton: Locator;
  readonly formCancelButton: Locator;
  readonly formCloseButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId('profiles-page');
    this.createButton = this.testId('new-profile-button');
    this.profileTable = this.testId('profiles-table');
    this.emptyState = this.testId('empty-state');
    this.formModal = this.testId('profile-form-modal');
    this.formNameInput = this.testId('profile-name-input');
    this.formDescriptionInput = this.testId('profile-description-input');
    this.formSubmitButton = this.testId('profile-form-submit');
    this.formCancelButton = this.testId('profile-form-cancel');
    this.formCloseButton = this.testId('profile-form-close');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/profiles'));
    await this.waitForPageLoad();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm(data: {
    name: string;
    description?: string;
  }): Promise<void> {
    await this.formNameInput.fill(data.name);
    if (data.description) {
      await this.formDescriptionInput.fill(data.description);
    }
  }

  async submitForm(): Promise<void> {
    await this.formSubmitButton.click();
  }

  async getRowCount(): Promise<number> {
    const rows = this.page.locator('[data-testid^="profile-row-"]');
    return rows.count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }

  async clickClone(index: number): Promise<void> {
    await this.testId(`clone-button-${index}`).click();
  }

  async clickRow(index: number): Promise<void> {
    await this.testId(`profile-row-${index}`).click();
  }

  editButton(index: number): Locator {
    return this.testId(`edit-button-${index}`);
  }

  deleteButton(index: number): Locator {
    return this.testId(`delete-button-${index}`);
  }

  cloneButton(index: number): Locator {
    return this.testId(`clone-button-${index}`);
  }

  row(index: number): Locator {
    return this.testId(`profile-row-${index}`);
  }
}
