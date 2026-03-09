import { test, expect } from "../../../fixtures";

test.describe("Governor Limits", () => {
  test("displays governor limits page", async ({ page }) => {
    await page.goto("/default/governor-limits");

    // Page should render without crashing — verify heading or content is visible
    const heading = page.getByRole("heading", { name: /governor limits/i });
    const noData = page.getByText(/no data/i);
    const pageContent = heading.or(noData);
    await expect(pageContent).toBeVisible({ timeout: 10_000 });
  });
});
