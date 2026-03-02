import type { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page';

export class GlobalSearchPage extends BasePage {
  readonly searchInput: Locator;
  readonly resultsList: Locator;
  readonly typeFilters: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.searchInput = this.testId('global-search-input');
    this.resultsList = this.testId('global-search-results');
    this.typeFilters = this.testId('global-search-type-filters');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/app/search'));
    await this.waitForLoadingComplete();
  }

  async search(text: string): Promise<void> {
    await this.searchInput.fill(text);
  }

  async getResultCount(): Promise<number> {
    return this.resultsList
      .locator('[data-testid^="search-result-"]')
      .count();
  }

  async clickResult(index: number): Promise<void> {
    await this.resultsList
      .locator('[data-testid^="search-result-"]')
      .nth(index)
      .click();
  }
}
