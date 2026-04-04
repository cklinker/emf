import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class SearchSettingsPage extends BasePage {
  readonly container: Locator;
  readonly reindexAllButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("search-settings-page");
    this.reindexAllButton = this.testId("reindex-all-btn");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/search-settings"));
    await this.waitForPageLoad();
  }

  reindexButton(collectionName: string): Locator {
    return this.testId(`reindex-${collectionName}`);
  }
}
