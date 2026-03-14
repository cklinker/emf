import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class PluginsPage extends BasePage {
  readonly pluginsPage: Locator;
  readonly pluginsCount: Locator;
  readonly detailsPanel: Locator;
  readonly emptyState: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.pluginsPage = this.testId("plugins-page");
    this.pluginsCount = this.testId("plugins-count");
    this.detailsPanel = this.testId("plugin-details-panel");
    this.emptyState = this.testId("empty-state");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/plugins"));
    await this.waitForContentReady(
      this.pluginsPage.or(this.page.getByText(/insufficient permissions/i)),
    );
  }

  async getPluginCount(): Promise<string | null> {
    return this.pluginsCount.textContent();
  }

  async togglePlugin(id: string): Promise<void> {
    await this.testId(`plugin-toggle-${id}`).click();
  }

  async openDetails(id: string): Promise<void> {
    await this.testId(`plugin-card-${id}`).click();
  }

  async closeDetails(): Promise<void> {
    await this.detailsPanel.getByRole("button", { name: /close/i }).click();
  }
}
