/**
 * Route Guard Utilities Tests
 *
 * Tests for the route guard utility functions.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 * - 2.2: Display provider selection page for multiple providers
 */

import { describe, it, expect } from 'vitest'
import {
  checkPageAuthorization,
  checkPolicyAuthorization,
  checkAuthorization,
  getRedirectPath,
  filterAuthorizedPages,
  canAccessRoute,
} from './routeGuards'
import type { User } from '../types/auth'
import type { PageConfig } from '../types/config'

// Test fixtures
const createUser = (overrides: Partial<User> = {}): User => ({
  id: 'user-1',
  email: 'test@example.com',
  name: 'Test User',
  ...overrides,
})

const createPageConfig = (overrides: Partial<PageConfig> = {}): PageConfig => ({
  id: 'page-1',
  path: '/test',
  title: 'Test Page',
  component: 'TestPage',
  ...overrides,
})

describe('checkPageAuthorization', () => {
  it('should return unauthorized for null user', () => {
    const page = createPageConfig({ policies: ['read:collections'] })
    const result = checkPageAuthorization(null, page)

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('unauthenticated')
  })

  it('should return authorized when page has no policies', () => {
    const user = createUser()
    const page = createPageConfig({ policies: [] })
    const result = checkPageAuthorization(user, page)

    expect(result.authorized).toBe(true)
  })

  it('should return authorized when page policies is undefined', () => {
    const user = createUser()
    const page = createPageConfig()
    delete page.policies
    const result = checkPageAuthorization(user, page)

    expect(result.authorized).toBe(true)
  })

  it('should return authorized when user has required policy', () => {
    const user = createUser({
      claims: { policies: ['read:collections', 'write:collections'] },
    })
    const page = createPageConfig({ policies: ['read:collections'] })
    const result = checkPageAuthorization(user, page)

    expect(result.authorized).toBe(true)
  })

  it('should return authorized when user has any of the required policies', () => {
    const user = createUser({
      claims: { policies: ['write:collections'] },
    })
    const page = createPageConfig({
      policies: ['read:collections', 'write:collections'],
    })
    const result = checkPageAuthorization(user, page)

    expect(result.authorized).toBe(true)
  })

  it('should return unauthorized when user lacks required policy', () => {
    const user = createUser({
      claims: { policies: ['read:other'] },
    })
    const page = createPageConfig({ policies: ['read:collections'] })
    const result = checkPageAuthorization(user, page)

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('missing_policy')
    expect(result.missingPolicies).toContain('read:collections')
  })

  it('should return unauthorized when user has no policies', () => {
    const user = createUser()
    const page = createPageConfig({ policies: ['read:collections'] })
    const result = checkPageAuthorization(user, page)

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('missing_policy')
  })
})

describe('checkPolicyAuthorization', () => {
  it('should return unauthorized for null user', () => {
    const result = checkPolicyAuthorization(null, ['read:collections'])

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('unauthenticated')
  })

  it('should return authorized when no policies are required', () => {
    const user = createUser()
    const result = checkPolicyAuthorization(user, [])

    expect(result.authorized).toBe(true)
  })

  it('should return authorized when user has required policy', () => {
    const user = createUser({
      claims: { policies: ['read:collections', 'write:collections'] },
    })
    const result = checkPolicyAuthorization(user, ['read:collections'])

    expect(result.authorized).toBe(true)
  })

  it('should return unauthorized when user lacks required policy', () => {
    const user = createUser({
      claims: { policies: ['read:other'] },
    })
    const result = checkPolicyAuthorization(user, ['read:collections'])

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('missing_policy')
  })
})

describe('checkAuthorization', () => {
  it('should return unauthorized for null user', () => {
    const result = checkAuthorization(null, ['read:collections'])

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('unauthenticated')
  })

  it('should return authorized when no requirements', () => {
    const user = createUser()
    const result = checkAuthorization(user, [])

    expect(result.authorized).toBe(true)
  })

  it('should return authorized when user meets policy requirements', () => {
    const user = createUser({
      claims: { policies: ['read:collections'] },
    })
    const result = checkAuthorization(user, ['read:collections'])

    expect(result.authorized).toBe(true)
  })

  it('should return unauthorized when user lacks policy', () => {
    const user = createUser({
      claims: { policies: ['read:other'] },
    })
    const result = checkAuthorization(user, ['read:collections'])

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('missing_policy')
  })
})

describe('getRedirectPath', () => {
  it('should return empty string when authorized', () => {
    const result = getRedirectPath({ authorized: true })

    expect(result).toBe('')
  })

  it('should return login path when unauthenticated', () => {
    const result = getRedirectPath({
      authorized: false,
      reason: 'unauthenticated',
    })

    expect(result).toBe('/login')
  })

  it('should return unauthorized path when missing policy', () => {
    const result = getRedirectPath({
      authorized: false,
      reason: 'missing_policy',
    })

    expect(result).toBe('/unauthorized')
  })

  it('should use custom login path', () => {
    const result = getRedirectPath(
      { authorized: false, reason: 'unauthenticated' },
      '/custom-login'
    )

    expect(result).toBe('/custom-login')
  })

  it('should use custom unauthorized path', () => {
    const result = getRedirectPath(
      { authorized: false, reason: 'missing_policy' },
      '/login',
      '/custom-unauthorized'
    )

    expect(result).toBe('/custom-unauthorized')
  })
})

describe('filterAuthorizedPages', () => {
  const pages: PageConfig[] = [
    createPageConfig({ id: 'public', path: '/public', policies: [] }),
    createPageConfig({
      id: 'admin',
      path: '/admin',
      policies: ['admin:access'],
    }),
    createPageConfig({
      id: 'collections',
      path: '/collections',
      policies: ['read:collections'],
    }),
  ]

  it('should return empty array for null user', () => {
    const result = filterAuthorizedPages(pages, null)

    expect(result).toEqual([])
  })

  it('should return only public pages for user without policies', () => {
    const user = createUser()
    const result = filterAuthorizedPages(pages, user)

    expect(result).toHaveLength(1)
    expect(result[0].id).toBe('public')
  })

  it('should return authorized pages for user with policies', () => {
    const user = createUser({
      claims: { policies: ['read:collections'] },
    })
    const result = filterAuthorizedPages(pages, user)

    expect(result).toHaveLength(2)
    expect(result.map((p) => p.id)).toContain('public')
    expect(result.map((p) => p.id)).toContain('collections')
  })

  it('should return all pages for user with all policies', () => {
    const user = createUser({
      claims: { policies: ['admin:access', 'read:collections'] },
    })
    const result = filterAuthorizedPages(pages, user)

    expect(result).toHaveLength(3)
  })
})

describe('canAccessRoute', () => {
  const pages: PageConfig[] = [
    createPageConfig({ id: 'public', path: '/public', policies: [] }),
    createPageConfig({
      id: 'admin',
      path: '/admin',
      policies: ['admin:access'],
    }),
  ]

  it('should return authorized for unknown route', () => {
    const user = createUser()
    const result = canAccessRoute('/unknown', pages, user)

    expect(result.authorized).toBe(true)
  })

  it('should return authorized for public route', () => {
    const user = createUser()
    const result = canAccessRoute('/public', pages, user)

    expect(result.authorized).toBe(true)
  })

  it('should return unauthorized for protected route without policy', () => {
    const user = createUser()
    const result = canAccessRoute('/admin', pages, user)

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('missing_policy')
  })

  it('should return authorized for protected route with policy', () => {
    const user = createUser({
      claims: { policies: ['admin:access'] },
    })
    const result = canAccessRoute('/admin', pages, user)

    expect(result.authorized).toBe(true)
  })

  it('should return unauthenticated for null user on protected route', () => {
    const result = canAccessRoute('/admin', pages, null)

    expect(result.authorized).toBe(false)
    expect(result.reason).toBe('unauthenticated')
  })
})
