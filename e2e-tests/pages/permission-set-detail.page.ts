import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class PermissionSetDetailPage extends BasePage {
  readonly detailPage: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly deleteButton: Locator;
  readonly systemPermissions: Locator;
  readonly objectPermissions: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.detailPage = this.testId('permission-set-detail-page');
    this.editButton = this.testId('edit-permissions-button');
    this.saveButton = this.testId('save-permissions-button');
    this.deleteButton = this.testId('delete-button');
    this.systemPermissions = this.testId('system-permissions-section');
    this.objectPermissions = this.testId('object-permissions-section');
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/permission-sets/${id}`));
    await this.waitForLoadingComplete();
  }

  async toggleEditMode(): Promise<void> {
    await this.editButton.click();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async clickDelete(): Promise<void> {
    await this.deleteButton.click();
  }
}
