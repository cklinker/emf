import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ProfileDetailPage extends BasePage {
  readonly container: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly deleteButton: Locator;
  readonly cancelEditButton: Locator;
  readonly systemPermissions: Locator;
  readonly objectPermissions: Locator;
  readonly backLink: Locator;
  readonly systemBadge: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("profile-detail-page");
    this.editButton = this.testId("edit-permissions-button");
    this.saveButton = this.testId("save-permissions-button");
    this.deleteButton = this.testId("delete-button");
    this.cancelEditButton = this.testId("cancel-edit-button");
    this.systemPermissions = this.testId("system-permissions-section");
    this.objectPermissions = this.testId("object-permissions-section");
    this.backLink = this.testId("back-link");
    this.systemBadge = this.testId("system-badge");
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/profiles/${id}`));
    await this.waitForPageLoad();
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

  async cancelEdit(): Promise<void> {
    await this.cancelEditButton.click();
  }

  async getPermissionCount(): Promise<number> {
    const checkboxes = this.systemPermissions.locator('input[type="checkbox"]');
    return checkboxes.count();
  }

  async isSystemProfile(): Promise<boolean> {
    return this.systemBadge.isVisible({ timeout: 2000 }).catch(() => false);
  }
}
