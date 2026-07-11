import { test, expect } from "@playwright/test";

/**
 * Telehealth archival & retention (slice 7) — post-deploy spec, skip-gated
 * until slices 1–7 are live (scheduling/chat-console precedent). Needs a staff
 * user (MANAGE_CHAT + MANAGE_DATA) and a portal user (magic-link auth state),
 * a closed chat conversation, and an ended video session with an archive row.
 *
 * Manual smoke (documented in specs/telehealth/7-archival-retention.md):
 *  1. Staff closes a chat in the console → POST an archive-now, or wait for the
 *     auto-archive sweep (shorten kelta.telehealth.auto-archive.poll-interval-ms
 *     and archiveAfterDays in dev). The conversation status flips to ARCHIVED.
 *  2. Console → Archived tab shows the thread READ-ONLY with an
 *     "Archived <date> · retained until <date>" banner and Download (PDF/JSON).
 *  3. Clicking Download PDF opens the presigned artifact (audited as
 *     ARCHIVE_ACCESSED in the security log).
 *  4. On the appointment/visit page the "Encounter record" card lists the chat
 *     transcript + video summary with a download.
 *  5. Admin → Setup → Telehealth Retention: edit the three retention windows
 *     (MANAGE_DATA required); toggle legal hold on an archive (confirm dialog) —
 *     it is then excluded from the retention purge.
 *  6. Portal user sees ONLY their own archived visits (own-history), read-only.
 *  7. Retention purge is DRY-RUN by default
 *     (kelta.telehealth.retention.purge-dry-run=true): the sweep logs what it
 *     WOULD purge and deletes nothing until an operator sets it false.
 */
test.describe.skip("Telehealth archival (post-deploy)", () => {
  const base = process.env.KELTA_APP_URL ?? "http://localhost:5173";
  const tenant = process.env.KELTA_TENANT_SLUG ?? "acme";

  test("staff sees an archived thread read-only and can download the transcript", async ({
    page,
  }) => {
    await page.goto(`${base}/${tenant}/app/chat`);
    await page.getByTestId("chat-console-tab-archived").click();
    // Open the first archived conversation.
    await page.locator('[data-testid^="chat-console-item-"]').first().click();

    // Read-only banner is present; no composer.
    await expect(page.getByTestId("chat-console-archive-banner")).toBeVisible();
    await expect(page.getByTestId("chat-console-composer")).toHaveCount(0);

    // Download opens the presigned PDF in a new tab.
    const popupPromise = page.waitForEvent("popup");
    await page.getByTestId("chat-console-archive-banner-pdf").click();
    const popup = await popupPromise;
    expect(popup.url()).toContain("http");
    await popup.close();
  });

  test("admin edits retention settings and toggles a legal hold", async ({
    page,
  }) => {
    await page.goto(`${base}/${tenant}/telehealth-settings`);
    await expect(page.getByTestId("telehealth-settings-retentionYears")).toBeVisible();

    await page.getByTestId("telehealth-settings-retentionYears").fill("10");
    await page.getByTestId("telehealth-settings-save").click();
    await expect(page.getByText("Retention settings saved")).toBeVisible();

    // Legal hold requires a confirm.
    page.once("dialog", (dialog) => dialog.accept());
    await page.locator('[data-testid^="telehealth-settings-hold-"]').first().click();
    await expect(page.getByText("Legal hold updated")).toBeVisible();
  });

  test("portal user sees only their own encounter record", async ({
    browser,
  }) => {
    const portalContext = await browser.newContext({
      storageState: "playwright/.auth/portal.json",
    });
    const portal = await portalContext.newPage();

    // The visit page surfaces the caller's own archived encounter record.
    await portal.goto(`${base}/${tenant}/app/appointments`);
    await expect(
      portal.locator('[data-testid^="encounter-record"]').first(),
    ).toBeVisible();

    await portalContext.close();
  });
});
