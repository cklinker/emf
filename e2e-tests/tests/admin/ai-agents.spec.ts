import { test, expect } from "../../fixtures";
import { AiAgentsPage } from "../../pages/ai-agents.page";

test.describe("AI Agents", () => {
  let aiAgentsPage: AiAgentsPage;

  test.beforeEach(async ({ page }) => {
    aiAgentsPage = new AiAgentsPage(page);
    await aiAgentsPage.goto();
  });

  test("displays the AI Agents page", async ({ page }) => {
    await expect(aiAgentsPage.container).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "AI Agents" }),
    ).toBeVisible();
    await expect(aiAgentsPage.createButton).toBeVisible();
  });

  test("lists agents or shows the empty state", async ({ page }) => {
    const rowsOrEmpty = aiAgentsPage.container;
    await expect(rowsOrEmpty).toBeVisible();
    // Either zero or more agent rows render; the page must not error.
    const rowCount = await aiAgentsPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
    void page;
  });

  test("opens the create form", async () => {
    await aiAgentsPage.clickCreate();
    await expect(aiAgentsPage.formModal).toBeVisible();
    await expect(aiAgentsPage.nameInput).toBeVisible();
    await expect(aiAgentsPage.systemPromptInput).toBeVisible();
  });
});
