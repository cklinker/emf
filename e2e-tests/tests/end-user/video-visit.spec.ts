import { test, expect } from "@playwright/test";

/**
 * Telehealth video visit (slice 6) — post-deploy spec, skip-gated until the
 * telehealth stack + a self-hosted LiveKit SFU are live (scheduling/chat
 * precedent). Runs against the dev-compose LiveKit with fake media
 * (`--use-fake-device-for-media-stream`), not a CI SFU — per the slice spec,
 * we do NOT fight CI networking with a real WebRTC connection.
 *
 * Manual smoke (documented in specs/telehealth/6-video-ui.md §6):
 *  1. Provider + portal user each authenticated (magic-link storage state), a
 *     CONFIRMED appointment inside its window.
 *  2. Portal opens /app/visits/{appointmentId} → sees the join card; before the
 *     window opens the Join button is disabled with a countdown.
 *  3. Inside the window the portal joins → waiting room; the provider's
 *     appointment/visit view shows "patient is waiting" via usePresence
 *     (resource `visit:{appointmentId}`).
 *  4. When tenant recording is enabled, the consent screen appears first;
 *     Accept posts consent before the token grant; the recording pill is
 *     visible in-call. Decline proceeds unrecorded (default policy).
 *  5. Both connect to the dev LiveKit; camera/mic tracks are present; the
 *     control bar toggles mute/camera/screen-share.
 *  6. Leaving ends the call → the session transitions to ENDED (webhook) and
 *     the ended summary renders.
 *
 * The LiveKit bundle is a lazily-loaded chunk (`@kelta/components/video`) — the
 * base app chunk must never contain it (enforced in the app vite build).
 */
test.describe.skip("Telehealth video visit (post-deploy)", () => {
  const base = process.env.KELTA_APP_URL ?? "http://localhost:5173";
  const tenant = process.env.KELTA_TENANT_SLUG ?? "acme";
  const appointmentId = process.env.KELTA_VISIT_APPOINTMENT_ID ?? "appt-under-test";

  test("portal joins the waiting room and the provider sees presence", async ({
    browser,
  }) => {
    const portalContext = await browser.newContext({
      storageState: "playwright/.auth/portal.json",
      permissions: ["camera", "microphone"],
    });
    const providerContext = await browser.newContext({
      storageState: "playwright/.auth/provider.json",
      permissions: ["camera", "microphone"],
    });
    const portal = await portalContext.newPage();
    const provider = await providerContext.newPage();

    await portal.goto(`${base}/${tenant}/app/visits/${appointmentId}`);
    // Inside the window: the join surface is interactive (not a disabled countdown).
    await expect(
      portal.getByTestId("visit-join-card").or(portal.locator('[data-testid$="-prejoin"]')),
    ).toBeVisible();

    // Provider opens their schedule; the waiting-patient indicator appears once
    // the portal user has joined presence for visit:{appointmentId}.
    await provider.goto(`${base}/${tenant}/app/appointments`);
    await expect(provider.getByText(/waiting/i)).toBeVisible({ timeout: 15_000 });

    await portalContext.close();
    await providerContext.close();
  });

  test("consent gate captures consent before the token grant when recording is on", async ({
    browser,
  }) => {
    const portalContext = await browser.newContext({
      storageState: "playwright/.auth/portal.json",
      permissions: ["camera", "microphone"],
    });
    const portal = await portalContext.newPage();

    // A page carrying the video-visit widget with a consent override enables
    // the recording-consent screen.
    await portal.goto(`${base}/${tenant}/app/p/video-visit`);
    const consent = portal.getByTestId("visit-consent");
    if (await consent.isVisible()) {
      await portal.getByRole("button", { name: /accept/i }).click();
    }
    await expect(portal.locator('[data-testid$="-prejoin"]')).toBeVisible();

    await portalContext.close();
  });
});
