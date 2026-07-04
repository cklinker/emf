# Native shells (Capacitor) — Rec 2C

The end-user app ships as an installable **PWA** (Rec 2A, `vite-plugin-pwa`). For
the app stores it is additionally wrapped with [Capacitor](https://capacitorjs.com),
which loads the same built web bundle (`dist/`) inside a native WebView and exposes
native capabilities (push, filesystem, etc.) to the existing React app.

The native projects (`ios/`, `android/`) are **generated**, not checked in
(`.gitignore`d) — they're produced on a developer/CI machine with the native
toolchains installed. CI builds and tests the web app only; the native build is a
separate, toolchain-bound step that cannot run in the standard frontend CI.

## One-time setup

The Capacitor toolchain is **not** a committed dependency — it would pull native
tooling into the web app's install and isn't needed by the web CI. Install it on
the machine that builds the native shells (include `@capacitor/push-notifications`
so the already-wired token registration resolves on device — see Push below):

```bash
cd kelta-ui/app
npm install
npm i -D @capacitor/cli @capacitor/core @capacitor/ios @capacitor/android @capacitor/push-notifications
npm run build            # produce dist/ (the web bundle Capacitor wraps)
npm run cap:add:ios      # generates ios/      (needs macOS + Xcode)
npm run cap:add:android  # generates android/  (needs Android SDK)
npm run cap:sync         # copy the web bundle + plugins into the native projects
```

> The `cap:*` npm scripts are pre-wired in `package.json`; they run once the
> Capacitor packages above are installed.

## Iterating

```bash
npm run build && npm run cap:sync   # rebuild web + copy into the native projects
npm run cap:open:ios                # open in Xcode to run/archive
npm run cap:open:android            # open in Android Studio
```

Config lives in [`capacitor.config.json`](./capacitor.config.json)
(`appId` `io.kelta.app`, `webDir` `dist`).

## Push notifications

The full push pipeline is implemented — only the on-device build/verification is
left (it needs the native toolchain + an Apple Developer account, so it can't run
in CI):

- **Backend delivery**: `PushProvider` SPI in `kelta-worker` with both
  `FcmPushProvider` (Android/web) and `ApnsPushProvider` (iOS, token/JWT auth over
  HTTP/2). Per-tenant credential overrides via `tenant.settings.push.{fcm,apns}`.
- **Token registration**: `kelta-ui/app/src/push/deviceRegistration.ts`.
  `initPushNotifications()` (called from `EndUserShell`) requests permission,
  registers with APNs/FCM via `@capacitor/push-notifications`, and POSTs the token
  to `POST /api/devices` (the `push_device` table). It is a **no-op on the web**
  and loads the plugin via a variable-specifier dynamic import, so the web build
  never bundles it — installing `@capacitor/push-notifications` (above) is all it
  needs to activate on device.

### iOS (APNs) on-device checklist

1. **Apple Developer** (paid membership required for push):
   - Register App ID **`io.kelta.app`** (must match `capacitor.config.json`) and
     enable the **Push Notifications** capability.
   - Create an **APNs Auth Key** → download the `.p8` (one-time). Record the
     **Key ID** and your **Team ID**.
2. **Xcode** (`npm run cap:open:ios`) → App target → Signing & Capabilities: set
   your Team, bundle id `io.kelta.app`, add **Push Notifications** + **Background
   Modes → Remote notifications**. Run on a physical device (the Simulator can't
   receive real APNs).
3. **Worker config** (env → `application.yml` binding; mount the `.p8` as a secret):
   ```
   KELTA_PUSH_PROVIDER=apns
   KELTA_PUSH_APNS_TEAM_ID=<TeamID>
   KELTA_PUSH_APNS_KEY_ID=<KeyID>
   KELTA_PUSH_APNS_BUNDLE_ID=io.kelta.app
   KELTA_PUSH_APNS_AUTH_KEY_PATH=/var/secrets/apns/AuthKey_<KeyID>.p8
   KELTA_PUSH_APNS_SANDBOX=true   # Xcode debug builds → sandbox host; TestFlight/App Store → false
   ```
   > **Sandbox must match the build.** A development-signed build's token is only
   > valid on the APNs sandbox host; a mismatch returns `BadDeviceToken` (the
   > worker then prunes the stale token).
4. **Verify**: launch → allow notifications → confirm a row in `push_device`
   (`platform='ios'`), then trigger a send (`DefaultPushService.sendToUser`, e.g.
   from a flow push action) and watch for "APNs push sent successfully".

Android push uses the same registration path with FCM; wire the FCM
`google-services.json` per the Capacitor + Firebase docs and set
`KELTA_PUSH_PROVIDER=fcm`.
