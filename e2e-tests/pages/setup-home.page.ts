import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class SetupHomePage extends BasePage {
  readonly container: Locator;
  readonly searchInput: Locator;
  readonly searchClear: Locator;
  readonly stats: Locator;
  readonly noResults: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("setup-home-page");
    this.searchInput = this.testId("setup-search-input");
    this.searchClear = this.testId("setup-search-clear");
    this.stats = this.testId("setup-stats");
    this.noResults = this.testId("setup-no-results");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/setup"));
    await this.waitForPageLoad();
  }

  async searchFor(text: string): Promise<void> {
    await this.searchInput.fill(text);
  }

  async clearSearch(): Promise<void> {
    await this.searchClear.click();
  }

  async getCategoryCount(): Promise<number> {
    const categories = this.page.locator('[data-testid^="setup-category-"]');
    return categories.count();
  }

  async clickItem(path: string): Promise<void> {
    const sanitizedPath = path.replace(/\//g, "").replace(/-/g, "");
    await this.testId(`setup-item-${sanitizedPath}`).click();
  }

  async getStatCards(): Promise<string[]> {
    await this.stats.waitFor({ state: "visible" });
    const cards = this.stats.locator("> *");
    const count = await cards.count();
    const texts: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = await cards.nth(i).textContent();
      if (text) texts.push(text.trim());
    }
    return texts;
  }

  category(key: string): Locator {
    return this.testId(`setup-category-${key}`);
  }

  item(path: string): Locator {
    const sanitizedPath = path.replace(/\//g, "").replace(/-/g, "");
    return this.testId(`setup-item-${sanitizedPath}`);
  }
}
