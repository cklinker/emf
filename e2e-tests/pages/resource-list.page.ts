import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ResourceListPage extends BasePage {
  readonly container: Locator;
  readonly dataTable: Locator;
  readonly createButton: Locator;
  readonly searchInput: Locator;

  constructor(
    page: Page,
    private readonly collection: string,
    tenantSlug?: string,
  ) {
    super(page, tenantSlug);
    this.container = this.testId("resource-list-page").or(
      this.page.locator("main").first(),
    );
    this.dataTable = this.page.locator("table").first();
    this.createButton = this.page.getByRole("link", { name: /new|create/i });
    this.searchInput = this.page.getByPlaceholder(/search/i);
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl(`/resources/${this.collection}`));
    await this.waitForPageLoad();
  }
}
