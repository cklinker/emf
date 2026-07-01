import { describe, it, expect } from 'vitest'
import { isValidCidr } from './cidr'

describe('isValidCidr', () => {
  it('accepts valid IPv4 CIDR ranges', () => {
    expect(isValidCidr('10.0.0.0/8')).toBe(true)
    expect(isValidCidr('192.168.1.0/24')).toBe(true)
    expect(isValidCidr('0.0.0.0/0')).toBe(true)
    expect(isValidCidr('203.0.113.5/32')).toBe(true)
  })

  it('accepts valid IPv6 CIDR ranges', () => {
    expect(isValidCidr('2001:db8::/32')).toBe(true)
    expect(isValidCidr('::/0')).toBe(true)
    expect(isValidCidr('fe80::1/128')).toBe(true)
  })

  it('trims surrounding whitespace', () => {
    expect(isValidCidr('  10.0.0.0/8  ')).toBe(true)
  })

  it('rejects a bare IP without a prefix', () => {
    expect(isValidCidr('10.0.0.1')).toBe(false)
    expect(isValidCidr('2001:db8::1')).toBe(false)
  })

  it('rejects out-of-range prefix lengths', () => {
    expect(isValidCidr('10.0.0.0/33')).toBe(false)
    expect(isValidCidr('2001:db8::/129')).toBe(false)
    expect(isValidCidr('10.0.0.0/-1')).toBe(false)
  })

  it('rejects out-of-range octets', () => {
    expect(isValidCidr('999.0.0.0/8')).toBe(false)
    expect(isValidCidr('10.0.0.256/24')).toBe(false)
  })

  it('rejects malformed input', () => {
    expect(isValidCidr('')).toBe(false)
    expect(isValidCidr('not-a-cidr')).toBe(false)
    expect(isValidCidr('10.0.0.0/')).toBe(false)
    expect(isValidCidr('/8')).toBe(false)
    expect(isValidCidr('10.0.0.0/8/9')).toBe(false)
  })
})
