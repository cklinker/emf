import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class CollectionDetailPage extends BasePage {
  readonly container: Locator;
  readonly fieldsTable: Locator;
  readonly versionHistory: Locator;
  readonly editButton: Locator;
  readonly deleteButton: Locator;
  readonly backButton: Locator;
  readonly collectionTitle: Locator;
  readonly collectionName: Locator;
  readonly collectionStatus: Locator;
  readonly fieldsPanel: Locator;
  readonly versionsPanel: Locator;
  readonly addFieldButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("collection-detail-page");
    this.fieldsTable = this.testId("fields-table");
    this.versionHistory = this.testId("versions-panel");
    this.editButton = this.testId("edit-button");
    this.deleteButton = this.testId("delete-button");
    this.backButton = this.testId("back-button");
    this.collectionTitle = this.testId("collection-title");
    this.collectionName = this.testId("collection-name");
    this.collectionStatus = this.testId("collection-status");
    this.fieldsPanel = this.testId("fields-panel");
    this.versionsPanel = this.testId("versions-panel");
    this.addFieldButton = this.testId("add-field-button");
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/collections/${id}`));
    await this.waitForPageLoad();
  }

  async getFieldCount(): Promise<number> {
    const rows = this.page.locator('[data-testid^="field-row-"]');
    return rows.count();
  }

  async getFieldNames(): Promise<string[]> {
    const rows = this.page.locator('[data-testid^="field-row-"]');
    const count = await rows.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).locator("td").first().textContent();
      if (text) names.push(text.trim());
    }
    return names;
  }

  async clickEdit(): Promise<void> {
    await this.editButton.click();
  }

  async clickDelete(): Promise<void> {
    await this.deleteButton.click();
  }

  async clickTab(tabId: string): Promise<void> {
    await this.testId(`${tabId}-tab`).click();
  }

  fieldRow(index: number): Locator {
    return this.testId(`field-row-${index}`);
  }

  editFieldButton(index: number): Locator {
    return this.testId(`edit-field-button-${index}`);
  }

  versionRow(index: number): Locator {
    return this.testId(`version-row-${index}`);
  }
}
