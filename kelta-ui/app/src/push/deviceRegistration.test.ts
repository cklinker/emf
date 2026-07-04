/**
 * Native push device-token registration (Rec 2C). Covers the CI-testable half:
 * the backend POST, empty-token guard, platform detection off the Capacitor global,
 * and initPushNotifications short-circuiting on the web.
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  registerDeviceToken,
  isNativePlatform,
  getPlatform,
  initPushNotifications,
  type DeviceRegistrationApi,
} from './deviceRegistration'

function setCapacitor(value: unknown): void {
  ;(globalThis as { Capacitor?: unknown }).Capacitor = value
}

afterEach(() => {
  delete (globalThis as { Capacitor?: unknown }).Capacitor
  vi.restoreAllMocks()
})

describe('registerDeviceToken', () => {
  it('POSTs the token to /api/devices and returns the device id', async () => {
    const post = vi.fn().mockResolvedValue({ data: { id: 'dev-1' } })
    const api: DeviceRegistrationApi = { post }

    const id = await registerDeviceToken(api, {
      token: 'apns-token',
      platform: 'ios',
      deviceName: 'iPhone',
    })

    expect(id).toBe('dev-1')
    expect(post).toHaveBeenCalledWith('/api/devices', {
      platform: 'ios',
      deviceToken: 'apns-token',
      deviceName: 'iPhone',
    })
  })

  it('no-ops on an empty token', async () => {
    const post = vi.fn()
    const id = await registerDeviceToken({ post }, { token: '', platform: 'ios' })
    expect(id).toBeUndefined()
    expect(post).not.toHaveBeenCalled()
  })
})

describe('platform detection', () => {
  it('reads the injected Capacitor global', () => {
    setCapacitor({ isNativePlatform: () => true, getPlatform: () => 'ios' })
    expect(isNativePlatform()).toBe(true)
    expect(getPlatform()).toBe('ios')
  })

  it('defaults to web when no Capacitor global is present', () => {
    expect(isNativePlatform()).toBe(false)
    expect(getPlatform()).toBe('web')
  })
})

describe('initPushNotifications', () => {
  it('is a no-op on the web (never touches the API or Capacitor plugin)', async () => {
    const post = vi.fn()
    await initPushNotifications({ post })
    expect(post).not.toHaveBeenCalled()
  })
})
