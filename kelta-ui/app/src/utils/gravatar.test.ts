import { describe, it, expect } from 'vitest'
import { getGravatarUrl } from './gravatar'

describe('getGravatarUrl', () => {
  it('should return a Gravatar URL for a valid email', () => {
    const url = getGravatarUrl('test@example.com')
    expect(url).toMatch(/^https:\/\/www\.gravatar\.com\/avatar\/[a-f0-9]{32}\?s=80&d=404$/)
  })

  it('should normalize email to lowercase and trimmed', () => {
    const url1 = getGravatarUrl('Test@Example.COM')
    const url2 = getGravatarUrl('  test@example.com  ')
    const url3 = getGravatarUrl('test@example.com')
    expect(url1).toBe(url3)
    expect(url2).toBe(url3)
  })

  it('should use custom size parameter', () => {
    const url = getGravatarUrl('test@example.com', 200)
    expect(url).toContain('?s=200&d=404')
  })

  it('should return null for empty email', () => {
    expect(getGravatarUrl('')).toBeNull()
  })

  it('should return null for undefined email', () => {
    expect(getGravatarUrl(undefined)).toBeNull()
  })

  it('should return null for null email', () => {
    expect(getGravatarUrl(null)).toBeNull()
  })

  it('should produce a known MD5 hash for a known email', () => {
    // MD5 of "test@example.com" is "55502f40dc8b7c769880b10874abc9d0"
    const url = getGravatarUrl('test@example.com')
    expect(url).toBe('https://www.gravatar.com/avatar/55502f40dc8b7c769880b10874abc9d0?s=80&d=404')
  })

  it('should produce different hashes for different emails', () => {
    const url1 = getGravatarUrl('alice@example.com')
    const url2 = getGravatarUrl('bob@example.com')
    expect(url1).not.toBe(url2)
  })
})
