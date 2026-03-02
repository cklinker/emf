import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class SecurityAuditPage extends BasePage {
  readonly securityAuditPage: Locator;
  readonly table: Locator;
  readonly dateFilter: Locator;
  readonly typeFilter: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.securityAuditPage = this.testId('security-audit-page');
    this.table = this.testId('security-audit-table');
    this.dateFilter = this.testId('security-audit-date-filter');
    this.typeFilter = this.testId('security-audit-type-filter');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/security-audit'));
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
    await this.page.getByRole('option', { name: type }).click();
  }
}
