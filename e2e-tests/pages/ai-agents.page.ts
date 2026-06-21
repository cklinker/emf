import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

/**
 * Page Object for the admin "AI Agents" page (governed agent management).
 */
export class AiAgentsPage extends BasePage {
  readonly container: Locator;
  readonly createButton: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly systemPromptInput: Locator;
  readonly formSubmit: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("ai-agents-page");
    this.createButton = this.testId("new-agent-button");
    this.formModal = this.testId("agent-form-modal");
    this.nameInput = this.testId("agent-name-input");
    this.systemPromptInput = this.testId("agent-system-prompt-input");
    this.formSubmit = this.testId("agent-form-submit");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/ai-agents"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  /** One Run button is rendered per agent row. */
  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="run-agent-button-"]').count();
  }
}
