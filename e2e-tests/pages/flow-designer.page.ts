import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class FlowDesignerPage extends BasePage {
  readonly canvas: Locator;
  readonly toolbar: Locator;
  readonly nodePanel: Locator;
  readonly saveButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.canvas = this.page.locator(".react-flow");
    this.toolbar = this.page.locator('[role="toolbar"]');
    this.nodePanel = this.page.locator('.steps-palette, [data-panel="steps"]');
    this.saveButton = this.page.getByRole("button", { name: /save/i });
  }

  async goto(flowId: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/flows/${flowId}/design`));
    await this.waitForLoadingComplete();
  }

  async isCanvasVisible(): Promise<boolean> {
    return this.canvas.isVisible();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
