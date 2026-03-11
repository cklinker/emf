import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class CollectionsListPage extends BasePage {
  readonly container: Locator;
  readonly createCollectionButton: Locator;
  readonly nameFilter: Locator;
  readonly statusFilter: Locator;
  readonly showSystemToggle: Locator;
  readonly emptyState: Locator;
  readonly collectionsTable: Locator;
  readonly headerName: Locator;
  readonly headerCreated: Locator;
  readonly headerUpdated: Locator;
  readonly pagination: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("collections-page");
    this.createCollectionButton = this.testId("create-collection-button");
    this.nameFilter = this.testId("name-filter");
    this.statusFilter = this.testId("status-filter");
    this.showSystemToggle = this.testId("show-system-toggle");
    this.emptyState = this.testId("empty-state");
    this.collectionsTable = this.testId("collections-table");
    this.headerName = this.testId("header-name");
    this.headerCreated = this.testId("header-created");
    this.headerUpdated = this.testId("header-updated");
    this.pagination = this.testId("pagination");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/collections"));
    await this.waitForPageLoad();
  }

  async waitForTableLoaded(): Promise<void> {
    const tableOrEmpty = this.collectionsTable.or(this.emptyState);
    await this.waitForContentReady(tableOrEmpty);
  }

  async waitForRows(minCount = 1): Promise<void> {
    const row = this.page.locator('[data-testid^="collection-row-"]').first();
    await row.waitFor({ state: "visible", timeout: 15_000 });
  }

  async clickCreateCollection(): Promise<void> {
    await this.createCollectionButton.click();
  }

  async filterByName(name: string): Promise<void> {
    await this.nameFilter.fill(name);
  }

  async filterByStatus(status: string): Promise<void> {
    await this.statusFilter.selectOption(status);
  }

  async toggleShowSystem(): Promise<void> {
    await this.showSystemToggle.click();
  }

  async getRowCount(): Promise<number> {
    const rows = this.page.locator('[data-testid^="collection-row-"]');
    return rows.count();
  }

  async clickRow(index: number): Promise<void> {
    await this.testId(`collection-row-${index}`).click();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }

  async confirmDelete(): Promise<void> {
    const confirmButton = this.testId("confirm-dialog-confirm");
    await confirmButton.waitFor({ state: "visible", timeout: 5_000 });
    await confirmButton.click();
  }

  async sortByColumn(column: "name" | "created" | "updated"): Promise<void> {
    await this.testId(`header-${column}`).click();
  }

  async getCollectionNames(): Promise<string[]> {
    const rows = this.page.locator('[data-testid^="collection-row-"]');
    const count = await rows.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).locator("td").nth(0).textContent();
      if (text) names.push(text.trim());
    }
    return names;
  }

  async getCollectionDisplayNames(): Promise<string[]> {
    const rows = this.page.locator('[data-testid^="collection-row-"]');
    const count = await rows.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).locator("td").nth(1).textContent();
      if (text) names.push(text.trim());
    }
    return names;
  }

  row(index: number): Locator {
    return this.testId(`collection-row-${index}`);
  }

  editButton(index: number): Locator {
    return this.testId(`edit-button-${index}`);
  }

  deleteButton(index: number): Locator {
    return this.testId(`delete-button-${index}`);
  }
}
