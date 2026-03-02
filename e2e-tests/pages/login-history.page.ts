import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class LoginHistoryPage extends BasePage {
  readonly loginHistoryPage: Locator;
  readonly table: Locator;
  readonly pagination: Locator;
  readonly previousButton: Locator;
  readonly nextButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.loginHistoryPage = this.testId("login-history-page");
    this.table = this.testId("login-history-table");
    this.pagination = this.testId("login-history-pagination");
    this.previousButton = this.testId("login-history-previous-button");
    this.nextButton = this.testId("login-history-next-button");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/login-history"));
    await this.waitForLoadingComplete();
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('tbody tr, [role="row"]').count();
  }

  async clickNext(): Promise<void> {
    await this.nextButton.click();
  }

  async clickPrevious(): Promise<void> {
    await this.previousButton.click();
  }
}
