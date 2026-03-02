import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ResourceBrowserPage extends BasePage {
  readonly resourceBrowserPage: Locator;
  readonly collectionSearchInput: Locator;
  readonly clearSearchButton: Locator;
  readonly collectionsGrid: Locator;
  readonly emptyState: Locator;
  readonly resultsCountLabel: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.resourceBrowserPage = this.testId("resource-browser-page");
    this.collectionSearchInput = this.testId("collection-search");
    this.clearSearchButton = this.testId("clear-search");
    this.collectionsGrid = this.testId("collections-grid");
    this.emptyState = this.testId("empty-state");
    this.resultsCountLabel = this.testId("results-count");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/resources"));
    await this.waitForLoadingComplete();
  }

  async search(text: string): Promise<void> {
    await this.collectionSearchInput.fill(text);
  }

  async clearSearch(): Promise<void> {
    await this.clearSearchButton.click();
  }

  async getCardCount(): Promise<number> {
    return this.collectionsGrid
      .locator('[data-testid^="collection-card-"]')
      .count();
  }

  async clickCard(index: number): Promise<void> {
    await this.testId(`collection-card-${index}`).click();
  }

  async getResultsCount(): Promise<string | null> {
    return this.resultsCountLabel.textContent();
  }
}
