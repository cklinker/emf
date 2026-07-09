import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { buildSocketUrl } from './RealtimeProvider'

describe('buildSocketUrl', () => {
  const originalEnv = import.meta.env.VITE_API_BASE_URL

  beforeEach(() => {
    // jsdom always sets window.location; reset the env var explicitly.
    vi.stubEnv('VITE_API_BASE_URL', '')
  })

  afterEach(() => {
    if (originalEnv !== undefined) {
      vi.stubEnv('VITE_API_BASE_URL', originalEnv)
    }
    vi.unstubAllEnvs()
  })

  it('targets the gateway origin from VITE_API_BASE_URL, upgraded to wss', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.kelta.io')
    expect(buildSocketUrl('tok')).toBe('wss://api.kelta.io/ws/realtime?token=tok')
  })

  it('uses ws for an http gateway origin', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
    expect(buildSocketUrl('tok')).toBe('ws://localhost:8080/ws/realtime?token=tok')
  })

  it('falls back to the page origin when env is empty (local dev)', () => {
    const expected = new URL('/ws/realtime', window.location.origin)
    expected.protocol = expected.protocol === 'https:' ? 'wss:' : 'ws:'
    expected.searchParams.set('token', 'tok')
    expect(buildSocketUrl('tok')).toBe(expected.toString())
  })

  it('URL-encodes the token', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.kelta.io')
    expect(buildSocketUrl('a b+c')).toBe('wss://api.kelta.io/ws/realtime?token=a+b%2Bc')
  })
})
