import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class WebhooksPage extends BasePage {
  readonly webhooksPage: Locator;
  readonly portalIframe: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.webhooksPage = this.testId("webhooks-page");
    this.portalIframe = this.testId("svix-portal-iframe");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/webhooks"));
    await this.waitForLoadingComplete();
  }
}
