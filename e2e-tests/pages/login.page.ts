import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class LoginPage extends BasePage {
  readonly container: Locator;
  readonly providerButtons: Locator;
  readonly errorMessage: Locator;
  readonly loadingSpinner: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId('login-page');
    this.providerButtons = this.page.getByRole('button');
    this.errorMessage = this.testId('error-message');
    this.loadingSpinner = this.page.locator('[role="status"]');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/login'));
    await this.waitForPageLoad();
  }

  async clickProvider(name: string): Promise<void> {
    await this.page.getByRole('button', { name }).click();
  }

  async getProviderNames(): Promise<string[]> {
    await this.container.waitFor({ state: 'visible' });
    const buttons = this.container.getByRole('button');
    const count = await buttons.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = await buttons.nth(i).textContent();
      if (text) names.push(text.trim());
    }
    return names;
  }

  async isErrorVisible(): Promise<boolean> {
    return this.errorMessage.isVisible({ timeout: 5000 }).catch(() => false);
  }
}
