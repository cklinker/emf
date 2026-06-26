import { describe, it, expect } from 'vitest'
import { resolveHomePageSlug } from './homePage'

describe('resolveHomePageSlug', () => {
  it('returns the slug of a published, active page flagged config.isHomePage', () => {
    const pages = [
      { slug: 'reports', published: true, active: true, config: {} },
      { slug: 'dashboard', published: true, active: true, config: { isHomePage: true } },
    ]
    expect(resolveHomePageSlug(pages)).toBe('dashboard')
  })

  it('returns undefined when no page opts in', () => {
    expect(
      resolveHomePageSlug([{ slug: 'a', published: true, active: true, config: {} }])
    ).toBeUndefined()
  })

  it('ignores an unpublished or inactive home page', () => {
    expect(
      resolveHomePageSlug([
        { slug: 'd', published: false, active: true, config: { isHomePage: true } },
      ])
    ).toBeUndefined()
    expect(
      resolveHomePageSlug([
        { slug: 'd', published: true, active: false, config: { isHomePage: true } },
      ])
    ).toBeUndefined()
  })

  it('treats absent published/active as true (default-published)', () => {
    expect(resolveHomePageSlug([{ slug: 'd', config: { isHomePage: true } }])).toBe('d')
  })

  it('returns the first opted-in page when several are flagged', () => {
    const pages = [
      { slug: 'first', published: true, active: true, config: { isHomePage: true } },
      { slug: 'second', published: true, active: true, config: { isHomePage: true } },
    ]
    expect(resolveHomePageSlug(pages)).toBe('first')
  })

  it('is null-safe for non-array / empty input', () => {
    expect(resolveHomePageSlug(undefined)).toBeUndefined()
    expect(resolveHomePageSlug(null)).toBeUndefined()
    expect(resolveHomePageSlug([])).toBeUndefined()
  })
})
