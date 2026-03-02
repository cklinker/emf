import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class PackagesPage extends BasePage {
  readonly packagesPage: Locator;
  readonly tabExport: Locator;
  readonly tabImport: Locator;
  readonly tabHistory: Locator;
  readonly exportButton: Locator;
  readonly importButton: Locator;
  readonly dropZone: Locator;
  readonly fileInput: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.packagesPage = this.testId("packages-page");
    this.tabExport = this.testId("tab-export");
    this.tabImport = this.testId("tab-import");
    this.tabHistory = this.testId("tab-history");
    this.exportButton = this.testId("export-button");
    this.importButton = this.testId("import-button");
    this.dropZone = this.testId("drop-zone");
    this.fileInput = this.testId("file-input");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/packages"));
    await this.waitForLoadingComplete();
  }

  async clickExportTab(): Promise<void> {
    await this.tabExport.click();
  }

  async clickImportTab(): Promise<void> {
    await this.tabImport.click();
  }

  async clickHistoryTab(): Promise<void> {
    await this.tabHistory.click();
  }

  async clickExport(): Promise<void> {
    await this.exportButton.click();
  }

  async getHistoryRowCount(): Promise<number> {
    return this.packagesPage.locator('tbody tr, [role="row"]').count();
  }
}
