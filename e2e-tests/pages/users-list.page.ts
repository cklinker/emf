import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class UsersListPage extends BasePage {
  readonly container: Locator;
  readonly createButton: Locator;
  readonly userTable: Locator;
  readonly searchInput: Locator;
  readonly statusFilter: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId('users-page');
    this.createButton = this.container
      .locator('header')
      .getByRole('button');
    this.userTable = this.container.locator('table');
    this.searchInput = this.container.locator('input[type="text"]');
    this.statusFilter = this.container.locator('select');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/users'));
    await this.waitForPageLoad();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async search(text: string): Promise<void> {
    await this.searchInput.fill(text);
  }

  async getRowCount(): Promise<number> {
    const rows = this.userTable.locator('tbody tr');
    return rows.count();
  }

  async clickRow(index: number): Promise<void> {
    const row = this.userTable.locator('tbody tr').nth(index);
    await row.locator('button').first().click();
  }

  async filterByStatus(status: string): Promise<void> {
    await this.statusFilter.selectOption(status);
  }
}
