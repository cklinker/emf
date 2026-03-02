import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class ModulesPage extends BasePage {
  readonly modulesPage: Locator;
  readonly installButton: Locator;
  readonly moduleCards: Locator;
  readonly emptyState: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.modulesPage = this.testId('modules-page');
    this.installButton = this.testId('install-button');
    this.moduleCards = this.testId('module-cards');
    this.emptyState = this.testId('empty-state');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/modules'));
    await this.waitForLoadingComplete();
  }

  async clickInstall(): Promise<void> {
    await this.installButton.click();
  }

  async getModuleCount(): Promise<number> {
    return this.moduleCards.locator('[data-testid^="module-card-"]').count();
  }

  async isModuleVisible(name: string): Promise<boolean> {
    return this.modulesPage.getByText(name).isVisible();
  }

  async toggleModule(name: string): Promise<void> {
    const moduleCard = this.modulesPage.getByText(name).locator('..');
    await moduleCard.getByRole('switch').click();
  }
}
