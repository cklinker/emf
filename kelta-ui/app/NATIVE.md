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
the machine that builds the native shells:

```bash
cd kelta-ui/app
npm install
npm i -D @capacitor/cli @capacitor/core @capacitor/ios @capacitor/android
npm run build            # produce dist/ (the web bundle Capacitor wraps)
npm run cap:add:ios      # generates ios/      (needs macOS + Xcode)
npm run cap:add:android  # generates android/  (needs Android SDK)
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

The backend push pipeline already exists (`PushProvider` SPI + `FcmPushProvider`
in `kelta-worker`). To wire native push, add `@capacitor/push-notifications`,
register the device token, and POST it to the worker's push-device endpoint so
the existing provider can deliver to it. (APNs delivery provider is a follow-up;
FCM is implemented.)
