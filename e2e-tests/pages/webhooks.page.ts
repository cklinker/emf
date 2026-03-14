import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class WebhooksPage extends BasePage {
  readonly webhooksPage: Locator;
  readonly endpointsList: Locator;
  readonly createEndpointButton: Locator;
  readonly messagesTab: Locator;
  readonly eventCatalogTab: Locator;
  readonly endpointsTab: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.webhooksPage = this.testId("webhooks-page");
    this.endpointsList = this.testId("endpoints-list");
    this.createEndpointButton = this.testId("create-endpoint-button");
    this.messagesTab = page.getByRole("tab", { name: /messages/i });
    this.eventCatalogTab = page.getByRole("tab", { name: /event catalog/i });
    this.endpointsTab = page.getByRole("tab", { name: /endpoints/i });
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/webhooks"));
    await this.waitForLoadingComplete();
  }
}
