/**
 * Native push device-token registration (Rec 2C).
 *
 * The CI-testable half — {@link registerDeviceToken} — POSTs a device token to the
 * existing `POST /api/devices` endpoint (backed by the `push_device` table). The native
 * half — {@link initPushNotifications} — acquires the token from Capacitor's
 * `@capacitor/push-notifications` plugin and is a no-op off a native platform.
 *
 * Capacitor is intentionally NOT a committed web dependency (the native toolchain is
 * dev-installed), so this module never statically imports it: platform detection reads the
 * `Capacitor` global the native runtime injects, and the plugin is loaded via a dynamic
 * import with a variable specifier (kept out of the Vite/Rollup build graph). On the web
 * everything short-circuits before any Capacitor code is touched.
 */

/** Minimal slice of `ApiClient` this module needs (structurally satisfied by `ApiClient`). */
export interface DeviceRegistrationApi {
  post<T = unknown>(url: string, data?: unknown): Promise<T>
}

export interface DeviceRegistration {
  /** The APNs/FCM device token from the native platform. */
  token: string
  /** `ios` | `android` | `web`. */
  platform: string
  /** Optional friendly device name. */
  deviceName?: string
}

/**
 * Register (or upsert) a device token with the backend. Returns the server device id, or
 * `undefined` when there is no token to register. The gateway injects `X-User-Id`; the
 * body matches `PushDeviceController` (`platform`, `deviceToken`, `deviceName`).
 */
export async function registerDeviceToken(
  api: DeviceRegistrationApi,
  registration: DeviceRegistration
): Promise<string | undefined> {
  if (!registration.token) return undefined
  const response = await api.post<{ data?: { id?: string } }>('/api/devices', {
    platform: registration.platform,
    deviceToken: registration.token,
    deviceName: registration.deviceName,
  })
  return response?.data?.id
}

interface CapacitorGlobal {
  isNativePlatform?: () => boolean
  getPlatform?: () => string
}

function capacitor(): CapacitorGlobal | undefined {
  return (globalThis as { Capacitor?: CapacitorGlobal }).Capacitor
}

/** True when running inside the Capacitor native shell (false on the web). */
export function isNativePlatform(): boolean {
  return capacitor()?.isNativePlatform?.() ?? false
}

/** `ios` | `android` on native, `web` otherwise. */
export function getPlatform(): string {
  return capacitor()?.getPlatform?.() ?? 'web'
}

/** Minimal shape of the `@capacitor/push-notifications` plugin we consume. */
interface PushNotificationsPlugin {
  requestPermissions(): Promise<{ receive: string }>
  register(): Promise<void>
  addListener(event: 'registration', cb: (token: { value: string }) => void): Promise<unknown>
}

/**
 * Native-only bootstrap: request permission, register with APNs/FCM, and on each token
 * emission persist it via {@link registerDeviceToken}. No-op (and touches no Capacitor code)
 * when not on a native platform. Safe to call unconditionally at app startup.
 */
export async function initPushNotifications(api: DeviceRegistrationApi): Promise<void> {
  if (!isNativePlatform()) return
  try {
    // Variable specifier + @vite-ignore keeps the (uninstalled) plugin out of the web build.
    const moduleName = '@capacitor/push-notifications'
    const mod = (await import(/* @vite-ignore */ moduleName)) as {
      PushNotifications?: PushNotificationsPlugin
    }
    const PushNotifications = mod.PushNotifications
    if (!PushNotifications) return

    const permission = await PushNotifications.requestPermissions()
    if (permission.receive !== 'granted') return

    await PushNotifications.register()
    await PushNotifications.addListener('registration', (token) => {
      void registerDeviceToken(api, { token: token.value, platform: getPlatform() })
    })
  } catch (err) {
    // Missing plugin / denied permission / native error — push is best-effort.
    console.warn('[push] native registration unavailable', err)
  }
}
