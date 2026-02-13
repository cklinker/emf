/**
 * TenantContext Unit Tests
 *
 * Tests for the tenant context provider, useTenant hook,
 * and module-level tenant slug/id accessors.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import {
  TenantProvider,
  useTenant,
  getTenantSlug,
  setResolvedTenantId,
  getResolvedTenantId,
} from './TenantContext'

/**
 * Test component that consumes useTenant() and renders context values.
 */
function TestConsumer() {
  const { tenantSlug, tenantBasePath } = useTenant()
  return (
    <div>
      <div data-testid="tenant-slug">{tenantSlug}</div>
      <div data-testid="tenant-base-path">{tenantBasePath}</div>
    </div>
  )
}

/**
 * Helper to render TenantProvider within a MemoryRouter at a given path.
 * The route pattern uses /:tenantSlug/* so useParams extracts tenantSlug.
 */
function renderWithTenantRoute(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path="/:tenantSlug/*"
          element={
            <TenantProvider>
              <TestConsumer />
            </TenantProvider>
          }
        />
        <Route
          path="*"
          element={
            <TenantProvider>
              <TestConsumer />
            </TenantProvider>
          }
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('TenantContext', () => {
  beforeEach(() => {
    // Reset module-level state between tests
    setResolvedTenantId(null)
  })

  describe('TenantProvider', () => {
    it('should extract tenantSlug from URL params and provide it via context', () => {
      renderWithTenantRoute('/acme-corp/dashboard')

      expect(screen.getByTestId('tenant-slug')).toHaveTextContent('acme-corp')
      expect(screen.getByTestId('tenant-base-path')).toHaveTextContent('/acme-corp')
    })

    it('should default tenantSlug to "default" when no param is present', () => {
      renderWithTenantRoute('/')

      expect(screen.getByTestId('tenant-slug')).toHaveTextContent('default')
      expect(screen.getByTestId('tenant-base-path')).toHaveTextContent('/default')
    })

    it('should handle different tenant slugs correctly', () => {
      renderWithTenantRoute('/my-org/settings')

      expect(screen.getByTestId('tenant-slug')).toHaveTextContent('my-org')
      expect(screen.getByTestId('tenant-base-path')).toHaveTextContent('/my-org')
    })
  })

  describe('useTenant Hook', () => {
    it('should return the correct tenantSlug and tenantBasePath', () => {
      renderWithTenantRoute('/test-tenant/page')

      expect(screen.getByTestId('tenant-slug')).toHaveTextContent('test-tenant')
      expect(screen.getByTestId('tenant-base-path')).toHaveTextContent('/test-tenant')
    })

    it('should throw when used outside TenantProvider', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      expect(() => {
        render(
          <MemoryRouter>
            <TestConsumer />
          </MemoryRouter>
        )
      }).toThrow('useTenant must be used within a TenantProvider')

      consoleSpy.mockRestore()
    })
  })

  describe('getTenantSlug', () => {
    it('should return the module-level tenant slug value', () => {
      // The default value before any provider renders
      const slug = getTenantSlug()
      expect(typeof slug).toBe('string')
    })

    it('should update after TenantProvider renders with a route param', () => {
      renderWithTenantRoute('/synced-org/home')

      // After render + useEffect, the module-level variable should be updated
      expect(getTenantSlug()).toBe('synced-org')
    })
  })

  describe('setResolvedTenantId / getResolvedTenantId', () => {
    it('should return null by default', () => {
      expect(getResolvedTenantId()).toBeNull()
    })

    it('should store and return the resolved tenant ID', () => {
      setResolvedTenantId('tenant-uuid-123')
      expect(getResolvedTenantId()).toBe('tenant-uuid-123')
    })

    it('should allow clearing the resolved tenant ID back to null', () => {
      setResolvedTenantId('tenant-uuid-456')
      expect(getResolvedTenantId()).toBe('tenant-uuid-456')

      setResolvedTenantId(null)
      expect(getResolvedTenantId()).toBeNull()
    })

    it('should allow overwriting with a different tenant ID', () => {
      setResolvedTenantId('first-id')
      expect(getResolvedTenantId()).toBe('first-id')

      setResolvedTenantId('second-id')
      expect(getResolvedTenantId()).toBe('second-id')
    })
  })
})
