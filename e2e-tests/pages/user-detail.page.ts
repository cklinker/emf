import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class UserDetailPage extends BasePage {
  readonly container: Locator;
  readonly editButton: Locator;
  readonly detailsTab: Locator;
  readonly securityTab: Locator;
  readonly loginHistoryTab: Locator;
  readonly profileSelect: Locator;
  readonly assignPermissionSetButton: Locator;
  readonly permissionSetsList: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("user-detail-page");
    this.editButton = this.container.getByRole("button", {
      name: /edit|save/i,
    });
    this.detailsTab = this.page.getByRole("tab", { name: /details/i });
    this.securityTab = this.page.getByRole("tab", { name: /security/i });
    this.loginHistoryTab = this.page.getByRole("tab", {
      name: /login history/i,
    });
    this.profileSelect = this.testId("profile-select");
    this.assignPermissionSetButton = this.testId(
      "assign-permission-set-button",
    );
    this.permissionSetsList = this.testId("permission-sets-list");
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/users/${id}`));
    await this.waitForPageLoad();
  }

  async clickTab(
    name: "Details" | "Security" | "Login History",
  ): Promise<void> {
    await this.page.getByRole("tab", { name: new RegExp(name, "i") }).click();
  }

  async clickEdit(): Promise<void> {
    await this.editButton.click();
  }

  async getDisplayedEmail(): Promise<string | null> {
    const emailElement = this.container.locator("text=/\\S+@\\S+/").first();
    return emailElement.textContent();
  }
}
