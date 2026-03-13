import type { Page, Locator } from "@playwright/test";
import { BasePage } from "../base.page";

export class AppHomePage extends BasePage {
  readonly quickActions: Locator;
  readonly recentItems: Locator;
  readonly favorites: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    // AppHomePage uses Card components with text headings (no data-testid attributes)
    this.quickActions = this.page.getByText("Quick Actions").first();
    this.recentItems = this.page.getByText("Recent Items").first();
    this.favorites = this.page
      .locator("[data-slot='card-title']")
      .filter({ hasText: "Favorites" })
      .first();
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/app/home"));
    await this.waitForLoadingComplete();
  }
}
