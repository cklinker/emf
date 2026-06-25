import { describe, it, expect } from 'vitest'
import { assertSafeUrl, isSafeUrl, urlScheme, UnsafeUrlError } from './urlSafety'

describe('urlSafety', () => {
  describe('urlScheme', () => {
    it('extracts the scheme of an absolute URL', () => {
      expect(urlScheme('https://example.com')).toBe('https')
      expect(urlScheme('MAILTO:a@b.com')).toBe('mailto')
      expect(urlScheme('javascript:alert(1)')).toBe('javascript')
    })

    it('returns null for a relative path (no scheme)', () => {
      expect(urlScheme('/app/p/orders')).toBeNull()
      expect(urlScheme('orders')).toBeNull()
      expect(urlScheme('./x')).toBeNull()
    })

    it('does not treat a colon inside a path segment as a scheme', () => {
      expect(urlScheme('/foo:bar')).toBeNull()
      expect(urlScheme('a/b:c')).toBeNull()
    })
  })

  describe('isSafeUrl', () => {
    it('allows http/https/mailto/tel and relative paths', () => {
      expect(isSafeUrl('https://example.com')).toBe(true)
      expect(isSafeUrl('http://example.com')).toBe(true)
      expect(isSafeUrl('mailto:a@b.com')).toBe(true)
      expect(isSafeUrl('tel:+15551234')).toBe(true)
      expect(isSafeUrl('/app/p/orders')).toBe(true)
      expect(isSafeUrl('/app/p/orders?status=NEW')).toBe(true)
    })

    it('blocks javascript: and data: schemes', () => {
      expect(isSafeUrl('javascript:alert(1)')).toBe(false)
      expect(isSafeUrl('JavaScript:alert(1)')).toBe(false)
      expect(isSafeUrl('  javascript:alert(1)')).toBe(false)
      expect(isSafeUrl('data:text/html,<script>alert(1)</script>')).toBe(false)
    })

    it('blocks non-strings and empty strings', () => {
      expect(isSafeUrl(null)).toBe(false)
      expect(isSafeUrl(undefined)).toBe(false)
      expect(isSafeUrl(42)).toBe(false)
      expect(isSafeUrl('')).toBe(false)
      expect(isSafeUrl('   ')).toBe(false)
    })
  })

  describe('assertSafeUrl', () => {
    it('throws UnsafeUrlError for a javascript: URL', () => {
      expect(() => assertSafeUrl('javascript:alert(1)')).toThrow(UnsafeUrlError)
    })

    it('throws for a data: URL', () => {
      expect(() => assertSafeUrl('data:text/html,x')).toThrow(UnsafeUrlError)
    })

    it('does not throw for an allowed URL', () => {
      expect(() => assertSafeUrl('https://example.com')).not.toThrow()
      expect(() => assertSafeUrl('/app/p/orders')).not.toThrow()
    })
  })
})
