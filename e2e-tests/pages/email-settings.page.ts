import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

/**
 * Page object for the tenant Email Settings page.
 * Note: the page uses semantic text/labels rather than data-testid hooks,
 * so locators here are based on role/text queries.
 */
export class EmailSettingsPage extends BasePage {
  readonly heading: Locator;
  readonly hostInput: Locator;
  readonly portInput: Locator;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly fromAddressInput: Locator;
  readonly fromNameInput: Locator;
  readonly testRecipientInput: Locator;
  readonly testSendButton: Locator;
  readonly saveButton: Locator;
  readonly platformDefaultBadge: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.heading = page.getByRole("heading", { name: /Email settings/i });
    this.hostInput = page.getByPlaceholder("smtp.example.com");
    this.portInput = page.getByPlaceholder("587");
    this.usernameInput = page.getByPlaceholder("(unchanged)").first();
    this.passwordInput = page.getByPlaceholder("(unchanged)").nth(1);
    this.fromAddressInput = page.getByPlaceholder("noreply@example.com");
    this.fromNameInput = page.getByPlaceholder("Example Co.");
    this.testRecipientInput = page.getByPlaceholder("me@example.com");
    this.testSendButton = page.getByRole("button", { name: /Send test/i });
    this.saveButton = page.getByRole("button", { name: /^Save$/ });
    this.platformDefaultBadge = page.getByText(/platform default SMTP server/i);
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/email-settings"));
    await this.waitForPageLoad();
  }
}
