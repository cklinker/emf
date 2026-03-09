import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class LogViewerPage extends BasePage {
  readonly searchInput: Locator;
  readonly levelFilter: Locator;
  readonly serviceFilter: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly expandedLog: Locator;
  readonly traceLink: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.searchInput = this.testId("log-viewer-search");
    this.levelFilter = this.testId("log-viewer-level-filter");
    this.serviceFilter = this.testId("log-viewer-service-filter");
    this.table = this.testId("log-viewer-table");
    this.rows = this.page.locator('[data-testid^="log-viewer-row-"]');
    this.expandedLog = this.testId("log-viewer-expanded");
    this.traceLink = this.testId("log-viewer-trace-link");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/monitoring/logs"));
    await this.waitForLoadingComplete();
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
  }

  async filterByLevel(level: string): Promise<void> {
    await this.levelFilter.selectOption(level);
  }

  async filterByService(service: string): Promise<void> {
    await this.serviceFilter.selectOption(service);
  }

  async expandRow(index: number): Promise<void> {
    await this.rows.nth(index).click();
  }

  async getRowCount(): Promise<number> {
    return this.rows.count();
  }
}
