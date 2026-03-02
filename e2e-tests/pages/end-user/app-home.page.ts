import type { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page';

export class AppHomePage extends BasePage {
  readonly quickActions: Locator;
  readonly recentItems: Locator;
  readonly favorites: Locator;
  readonly collectionsGrid: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.quickActions = this.testId('quick-actions');
    this.recentItems = this.testId('recent-items');
    this.favorites = this.testId('favorites');
    this.collectionsGrid = this.testId('collections-grid');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/app/home'));
    await this.waitForLoadingComplete();
  }

  async getQuickActionCount(): Promise<number> {
    return this.quickActions
      .locator('[data-testid^="quick-action-"]')
      .count();
  }

  async clickQuickAction(name: string): Promise<void> {
    await this.quickActions.getByText(name).click();
  }

  async getRecentItemCount(): Promise<number> {
    return this.recentItems
      .locator('[data-testid^="recent-item-"]')
      .count();
  }

  async clickRecentItem(index: number): Promise<void> {
    await this.recentItems
      .locator('[data-testid^="recent-item-"]')
      .nth(index)
      .click();
  }
}
