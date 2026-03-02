import type { Page, Locator } from "@playwright/test";
import { BasePage } from "../base.page";

export class CustomPagePage extends BasePage {
  readonly pageContent: Locator;

  constructor(
    page: Page,
    private readonly pageSlug: string,
    tenantSlug?: string,
  ) {
    super(page, tenantSlug);
    this.pageContent = this.testId("custom-page-content");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl(`/app/p/${this.pageSlug}`));
    await this.waitForLoadingComplete();
  }

  async isContentVisible(): Promise<boolean> {
    return this.pageContent.isVisible();
  }
}
