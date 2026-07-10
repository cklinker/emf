import { test, expect } from "@playwright/test";

/**
 * Chat console + portal chat-panel (telehealth slice 3) — post-deploy spec.
 *
 * Skip-gated until slices 1–3 are live on the dev stack (per the
 * approvals-inbox precedent): needs a portal user (magic-link login via
 * mailpit) and a custom page carrying the chat-panel widget.
 *
 * Manual smoke (documented in specs/telehealth/3-chat-ui.md):
 *  1. Portal user opens the tenant page with the chat-panel → Start a
 *     conversation → sends "hello".
 *  2. Agent opens /app/chat → Queue tab shows the conversation → Claim →
 *     replies; unread badge on the bell clears after opening the thread.
 *  3. Portal panel shows the reply (invalidation refetch, no reload).
 *  4. Agent closes the conversation → portal panel shows the closed state.
 */
test.describe.skip("Chat console + portal widget (post-deploy)", () => {
  const base = process.env.KELTA_APP_URL ?? "http://localhost:5173";
  const tenant = process.env.KELTA_TENANT_SLUG ?? "acme";

  test("agent inbox round-trip with a portal conversation", async ({
    browser,
  }) => {
    const agentContext = await browser.newContext({
      storageState: "playwright/.auth/agent.json",
    });
    const portalContext = await browser.newContext({
      storageState: "playwright/.auth/portal.json",
    });

    const portal = await portalContext.newPage();
    await portal.goto(`${base}/${tenant}/app/p/support`);
    await portal.getByTestId("page-node-chat-panel-start").click();
    await portal
      .getByTestId("page-node-chat-panel-composer-input")
      .fill("hello from the portal");
    await portal.getByTestId("page-node-chat-panel-composer-send").click();

    const agent = await agentContext.newPage();
    await agent.goto(`${base}/${tenant}/app/chat`);
    await agent.getByTestId("chat-console-tab-queue").click();
    await agent
      .getByText("hello from the portal")
      .waitFor({ state: "visible" });

    await agentContext.close();
    await portalContext.close();
  });

  test("closed conversations reject the composer", async ({ page }) => {
    await page.goto(`${base}/${tenant}/app/chat`);
    // Selecting a CLOSED conversation renders the read-only strip instead of
    // the composer.
    await expect(page.getByText("This conversation is closed")).toBeVisible();
  });
});
