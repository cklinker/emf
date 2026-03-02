import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class AuditTrailPage extends BasePage {
  readonly auditTrailPage: Locator;
  readonly table: Locator;
  readonly dateFilter: Locator;
  readonly typeFilter: Locator;
  readonly pagination: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.auditTrailPage = this.testId("audit-trail-page");
    this.table = this.testId("audit-trail-table");
    this.dateFilter = this.testId("audit-trail-date-filter");
    this.typeFilter = this.testId("audit-trail-type-filter");
    this.pagination = this.testId("audit-trail-pagination");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/audit-trail"));
    await this.waitForLoadingComplete();
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('tbody tr, [role="row"]').count();
  }

  async filterByDate(date: string): Promise<void> {
    await this.dateFilter.fill(date);
  }

  async filterByType(type: string): Promise<void> {
    await this.typeFilter.click();
    await this.page.getByRole("option", { name: type }).click();
  }
}
