import type { Page, Locator } from "@playwright/test";

export abstract class BasePage {
  constructor(
    protected readonly page: Page,
    protected readonly tenantSlug: string = process.env.E2E_TENANT_SLUG ||
      "default",
  ) {}

  abstract goto(...args: unknown[]): Promise<void>;

  protected tenantUrl(path: string): string {
    return `/${this.tenantSlug}${path}`;
  }

  testId(id: string): Locator {
    return this.page.getByTestId(id);
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState("networkidle");
  }

  async waitForLoadingComplete(): Promise<void> {
    const spinner = this.page.locator(
      '[data-testid*="loading"], [aria-busy="true"]',
    );
    if (await spinner.isVisible({ timeout: 2000 }).catch(() => false)) {
      await spinner.waitFor({ state: "hidden", timeout: 30_000 });
    }
  }

  async getToastMessage(): Promise<string | null> {
    const toast = this.page.locator("[data-sonner-toast]").first();
    if (await toast.isVisible({ timeout: 5000 }).catch(() => false)) {
      return toast.textContent();
    }
    return null;
  }

  async clickBreadcrumb(text: string): Promise<void> {
    await this.page
      .getByRole("navigation", { name: /breadcrumb/i })
      .getByRole("link", { name: text })
      .click();
  }

  get header(): Locator {
    return this.page.locator("header").first();
  }

  async confirmDialog(
    buttonText: RegExp | string = /delete|confirm|yes/i,
  ): Promise<void> {
    const dialog = this.page.getByRole("dialog");
    await dialog.getByRole("button", { name: buttonText }).click();
  }

  async cancelDialog(): Promise<void> {
    const dialog = this.page.getByRole("dialog");
    await dialog.getByRole("button", { name: /cancel|no/i }).click();
  }
}
