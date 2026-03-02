import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class CollectionFormPage extends BasePage {
  readonly container: Locator;
  readonly nameInput: Locator;
  readonly displayNameInput: Locator;
  readonly descriptionInput: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("collection-form-page");
    this.nameInput = this.page.getByLabel(/^name$/i);
    this.displayNameInput = this.page.getByLabel(/display name/i);
    this.descriptionInput = this.page.getByLabel(/description/i);
    this.submitButton = this.page.getByRole("button", {
      name: /save|submit|update/i,
    });
    this.cancelButton = this.page.getByRole("button", { name: /cancel/i });
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/collections/${id}/edit`));
    await this.waitForPageLoad();
  }

  async fillName(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async fillDisplayName(name: string): Promise<void> {
    await this.displayNameInput.fill(name);
  }

  async fillDescription(desc: string): Promise<void> {
    await this.descriptionInput.fill(desc);
  }

  async submit(): Promise<void> {
    await this.submitButton.click();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
  }
}
