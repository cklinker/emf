import { describe, it, expect } from 'vitest'
import { buildGrantTypesJson } from './ConnectedAppsPage'

describe('buildGrantTypesJson', () => {
  it('emits client_credentials only', () => {
    expect(buildGrantTypesJson(true, false)).toBe('["client_credentials"]')
  })

  it('emits both grants when both are enabled', () => {
    expect(buildGrantTypesJson(true, true)).toBe('["client_credentials","authorization_code"]')
  })

  it('emits authorization_code only', () => {
    expect(buildGrantTypesJson(false, true)).toBe('["authorization_code"]')
  })

  it('falls back to client_credentials when nothing is selected', () => {
    expect(buildGrantTypesJson(false, false)).toBe('["client_credentials"]')
  })
})
