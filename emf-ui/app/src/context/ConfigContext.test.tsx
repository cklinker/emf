/**
 * ConfigContext Unit Tests
 *
 * Tests for the configuration context and useConfig hook.
 * Validates requirements 1.1-1.8 for bootstrap configuration.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider, useConfig } from './ConfigContext'
import { clearBootstrapCache } from '../utils/bootstrapCache'
import type { ReactNode } from 'react'
import type { BootstrapConfig } from '../types/config'

// Store original fetch
const originalFetch = global.fetch

// Valid mock bootstrap config
// Note: theme and branding come from bootstrapCache defaults, not from JSON:API endpoints
const mockBootstrapConfig: BootstrapConfig = {
  pages: [
    {
      id: 'page-1',
      path: '/dashboard',
      title: 'Dashboard',
      component: 'DashboardPage',
    },
    {
      id: 'page-2',
      path: '/collections',
      title: 'Collections',
      component: 'CollectionsPage',
      policies: ['admin'],
    },
  ],
  menus: [
    {
      id: 'main-menu',
      name: 'Main Menu',
      items: [
        { id: 'item-1', label: 'Dashboard', path: '/dashboard', icon: 'home' },
        { id: 'item-2', label: 'Collections', path: '/collections', icon: 'folder' },
      ],
    },
  ],
  // These match bootstrapCache DEFAULT_THEME
  theme: {
    primaryColor: '#1976d2',
    secondaryColor: '#dc004e',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    borderRadius: '4px',
  },
  // These match bootstrapCache DEFAULT_BRANDING
  branding: {
    logoUrl: '/logo.svg',
    applicationName: 'EMF Platform',
    faviconUrl: '/favicon.ico',
  },
  oidcProviders: [
    {
      id: 'provider-1',
      name: 'Test Provider',
      issuer: 'https://auth.example.com',
    },
  ],
}

/**
 * Build JSON:API list response wrapper for mock data.
 */
function jsonApiList(type: string, items: Record<string, unknown>[]) {
  return {
    data: items.map((item, i) => ({
      type,
      id: item.id ?? `${type}-${i + 1}`,
      attributes: Object.fromEntries(Object.entries(item).filter(([k]) => k !== 'id')),
    })),
    metadata: {
      totalCount: items.length,
      currentPage: 0,
      pageSize: 500,
      totalPages: 1,
    },
  }
}

// Create mock fetch function
// The bootstrap config is now composed from 4 parallel JSON:API calls:
//   /api/ui-pages, /api/ui-menus, /api/oidc-providers, /api/tenants
function createMockFetch(
  options: {
    config?: BootstrapConfig | Partial<BootstrapConfig>
    shouldFail?: boolean
    failStatus?: number
    failStatusText?: string
    invalidJson?: boolean
    delay?: number
  } = {}
) {
  const cfg = (options.config || mockBootstrapConfig) as Record<string, unknown>

  return vi.fn(async (input: RequestInfo | URL) => {
    const url =
      typeof input === 'string'
        ? input
        : input instanceof URL
          ? input.toString()
          : (input as Request).url

    if (options.delay) {
      await new Promise((resolve) => setTimeout(resolve, options.delay))
    }

    // When shouldFail is true, simulate a network error (fetch throws)
    // This causes bootstrapCache's .catch() to fire with the error
    if (options.shouldFail) {
      const status = options.failStatus || 500
      const statusText = options.failStatusText || 'Internal Server Error'
      throw new Error(`Failed to fetch bootstrap configuration: ${status} ${statusText}`)
    }

    // Handle /api/ui-pages
    if (url.includes('/api/ui-pages')) {
      if (options.invalidJson) {
        return {
          ok: true,
          json: async () => {
            throw new SyntaxError('Unexpected token')
          },
        } as unknown as Response
      }
      const pages = (cfg.pages as Record<string, unknown>[]) || []
      return {
        ok: true,
        json: async () => jsonApiList('ui-pages', pages),
      } as Response
    }

    // Handle /api/ui-menus
    if (url.includes('/api/ui-menus')) {
      if (options.invalidJson) {
        return {
          ok: true,
          json: async () => {
            throw new SyntaxError('Unexpected token')
          },
        } as unknown as Response
      }
      const menus = (cfg.menus as Record<string, unknown>[]) || []
      return {
        ok: true,
        json: async () => jsonApiList('ui-menus', menus),
      } as Response
    }

    // Handle /api/oidc-providers
    if (url.includes('/api/oidc-providers')) {
      const providers = (cfg.oidcProviders as Record<string, unknown>[]) || []
      return {
        ok: true,
        json: async () => jsonApiList('oidc-providers', providers),
      } as Response
    }

    // Handle /api/tenants
    if (url.includes('/api/tenants')) {
      return {
        ok: true,
        json: async () =>
          jsonApiList('tenants', [{ id: 'tenant-1', slug: 'default', name: 'Default Tenant' }]),
      } as Response
    }

    return {
      ok: false,
      status: 404,
      statusText: 'Not Found',
    } as Response
  })
}

// Test component that uses useConfig
function TestComponent({
  onRender,
}: {
  onRender?: (config: ReturnType<typeof useConfig>) => void
}) {
  const configContext = useConfig()
  onRender?.(configContext)
  return (
    <div>
      <div data-testid="loading">{configContext.isLoading ? 'loading' : 'not-loading'}</div>
      <div data-testid="has-config">{configContext.config ? 'has-config' : 'no-config'}</div>
      <div data-testid="error">
        {configContext.error ? configContext.error.message : 'no-error'}
      </div>
      <div data-testid="app-name">
        {configContext.config?.branding.applicationName || 'no-app-name'}
      </div>
      <div data-testid="pages-count">{configContext.config?.pages.length ?? 'no-pages'}</div>
      <div data-testid="menus-count">{configContext.config?.menus.length ?? 'no-menus'}</div>
      <button onClick={() => configContext.reload()}>Reload</button>
    </div>
  )
}

// Helper to render with ConfigProvider
function renderWithConfig(ui: ReactNode = <TestComponent />, props?: { pollInterval?: number }) {
  return render(<ConfigProvider {...props}>{ui}</ConfigProvider>)
}

describe('ConfigContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearBootstrapCache()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    global.fetch = createMockFetch()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    global.fetch = originalFetch
  })

  describe('Initial State', () => {
    it('should start in loading state', async () => {
      renderWithConfig()

      // Initially loading
      expect(screen.getByTestId('loading')).toHaveTextContent('loading')

      // Wait for initialization to complete
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })
    })

    it('should have no config initially before fetch completes', () => {
      // Use a delayed fetch to capture initial state
      global.fetch = createMockFetch({ delay: 1000 })
      renderWithConfig()

      expect(screen.getByTestId('has-config')).toHaveTextContent('no-config')
    })

    it('should have no error initially', () => {
      global.fetch = createMockFetch({ delay: 1000 })
      renderWithConfig()

      expect(screen.getByTestId('error')).toHaveTextContent('no-error')
    })
  })

  describe('Fetch Bootstrap Configuration (Requirement 1.1)', () => {
    it('should fetch bootstrap config from JSON:API endpoints on mount', async () => {
      const mockFetch = createMockFetch()
      global.fetch = mockFetch

      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Verify fetch was called with the 4 JSON:API endpoints (tenant-scoped)
      const calledUrls = mockFetch.mock.calls.map((call: unknown[]) => call[0] as string)
      expect(calledUrls.some((u: string) => u.includes('/api/ui-pages'))).toBe(true)
      expect(calledUrls.some((u: string) => u.includes('/api/ui-menus'))).toBe(true)
      expect(calledUrls.some((u: string) => u.includes('/api/oidc-providers'))).toBe(true)
      expect(calledUrls.some((u: string) => u.includes('/api/tenants'))).toBe(true)
    })

    it('should load config successfully', async () => {
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('has-config')).toHaveTextContent('has-config')
      expect(screen.getByTestId('error')).toHaveTextContent('no-error')
    })
  })

  describe('Configure Routes (Requirement 1.2)', () => {
    it('should provide page configurations for routing', async () => {
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('pages-count')).toHaveTextContent('2')
    })

    it('should include page path, title, and component in config', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      const pages = configValue?.config?.pages
      expect(pages).toBeDefined()
      expect(pages?.[0]).toMatchObject({
        id: 'page-1',
        path: '/dashboard',
        title: 'Dashboard',
        component: 'DashboardPage',
      })
    })
  })

  describe('Configure Menus (Requirement 1.3)', () => {
    it('should provide menu configurations for navigation', async () => {
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('menus-count')).toHaveTextContent('1')
    })

    it('should include menu items with labels and paths', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      const menus = configValue?.config?.menus
      expect(menus).toBeDefined()
      expect(menus?.[0].items).toHaveLength(2)
      expect(menus?.[0].items[0]).toMatchObject({
        label: 'Dashboard',
        path: '/dashboard',
        icon: 'home',
      })
    })
  })

  describe('Apply Theme (Requirement 1.4)', () => {
    it('should provide theme configuration', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      const theme = configValue?.config?.theme
      expect(theme).toBeDefined()
      expect(theme).toMatchObject({
        primaryColor: '#1976d2',
        secondaryColor: '#dc004e',
        fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
        borderRadius: '4px',
      })
    })
  })

  describe('Apply Branding (Requirement 1.5)', () => {
    it('should provide branding configuration', async () => {
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('app-name')).toHaveTextContent('EMF Platform')
    })

    it('should include logo, app name, and favicon in branding', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      const branding = configValue?.config?.branding
      expect(branding).toBeDefined()
      expect(branding).toMatchObject({
        logoUrl: '/logo.svg',
        applicationName: 'EMF Platform',
        faviconUrl: '/favicon.ico',
      })
    })
  })

  describe('Error Handling - Unavailable Endpoint (Requirement 1.6)', () => {
    it('should set error when bootstrap endpoint returns error status', async () => {
      global.fetch = createMockFetch({
        shouldFail: true,
        failStatus: 500,
        failStatusText: 'Internal Server Error',
      })

      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('error')).toHaveTextContent(
        'Failed to fetch bootstrap configuration: 500 Internal Server Error'
      )
      expect(screen.getByTestId('has-config')).toHaveTextContent('no-config')
    })

    it('should set error when bootstrap endpoint returns 404', async () => {
      global.fetch = createMockFetch({
        shouldFail: true,
        failStatus: 404,
        failStatusText: 'Not Found',
      })

      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('error')).toHaveTextContent(
        'Failed to fetch bootstrap configuration: 404 Not Found'
      )
    })

    it('should provide reload function for retry', async () => {
      let reloadCalled = false
      global.fetch = createMockFetch({ shouldFail: true })

      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      expect(typeof configValue?.reload).toBe('function')

      // Now make fetch succeed and reload
      global.fetch = createMockFetch()

      await act(async () => {
        await configValue?.reload()
        reloadCalled = true
      })

      expect(reloadCalled).toBe(true)
      expect(screen.getByTestId('has-config')).toHaveTextContent('has-config')
      expect(screen.getByTestId('error')).toHaveTextContent('no-error')
    })
  })

  describe('Error Handling - Invalid Config (Requirement 1.7)', () => {
    it('should set error when config response has invalid JSON', async () => {
      global.fetch = createMockFetch({ invalidJson: true })

      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // When JSON parsing fails, the SyntaxError propagates through bootstrapCache
      expect(screen.getByTestId('error')).not.toHaveTextContent('no-error')
    })

    it('should use defaults when pages config is empty', async () => {
      // With JSON:API bootstrap, pages/menus come from endpoints while
      // theme/branding are provided as defaults by bootstrapCache.
      // Empty pages endpoint returns [], which is valid.
      global.fetch = createMockFetch({
        config: {
          pages: [],
          menus: [],
          oidcProviders: [],
          theme: mockBootstrapConfig.theme,
          branding: mockBootstrapConfig.branding,
        } as unknown as BootstrapConfig,
      })

      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // bootstrapCache always provides defaults for theme/branding,
      // so validation passes even with empty pages/menus
      expect(screen.getByTestId('has-config')).toHaveTextContent('has-config')
      expect(screen.getByTestId('pages-count')).toHaveTextContent('0')
    })

    it('should use default theme when endpoints return empty data', async () => {
      // bootstrapCache provides DEFAULT_THEME, so theme is always present
      global.fetch = createMockFetch({
        config: {
          pages: [],
          menus: [],
          oidcProviders: [],
        } as unknown as BootstrapConfig,
      })

      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      // bootstrapCache always provides default theme and branding
      expect(configValue?.config?.theme).toBeDefined()
      expect(configValue?.config?.branding).toBeDefined()
      expect(configValue?.error).toBeNull()
    })

    it('should use default branding when endpoints return empty data', async () => {
      global.fetch = createMockFetch({
        config: {
          pages: [],
          menus: [],
          oidcProviders: [],
        } as unknown as BootstrapConfig,
      })

      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      expect(configValue?.config?.branding).toBeDefined()
      expect(configValue?.config?.branding.applicationName).toBe('EMF Platform')
    })

    it('should set error when network failure prevents config loading', async () => {
      global.fetch = createMockFetch({ shouldFail: true })

      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Network errors propagate through bootstrapCache as error messages
      expect(screen.getByTestId('error')).not.toHaveTextContent('no-error')
      expect(screen.getByTestId('has-config')).toHaveTextContent('no-config')
    })
  })

  describe('Reload Configuration (Requirement 1.8)', () => {
    it('should provide reload function', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      expect(typeof configValue?.reload).toBe('function')
    })

    it('should reload config when reload is called', async () => {
      const mockFetch = createMockFetch()
      global.fetch = mockFetch

      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Initial fetch makes 4 parallel JSON:API calls
      expect(mockFetch).toHaveBeenCalledTimes(4)

      // Click reload button
      await user.click(screen.getByText('Reload'))

      await waitFor(() => {
        // Reload fires another 4 JSON:API calls
        expect(mockFetch).toHaveBeenCalledTimes(8)
      })
    })

    it('should update config after reload with new data', async () => {
      // Start with initial config
      global.fetch = createMockFetch()

      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('pages-count')).toHaveTextContent('2')
      })

      // Update fetch to return different page config
      const updatedConfig: BootstrapConfig = {
        ...mockBootstrapConfig,
        pages: [
          ...mockBootstrapConfig.pages,
          {
            id: 'page-3',
            path: '/settings',
            title: 'Settings',
            component: 'SettingsPage',
          },
        ],
      }
      global.fetch = createMockFetch({ config: updatedConfig })

      // Click reload
      await user.click(screen.getByText('Reload'))

      await waitFor(() => {
        // Pages count should increase from 2 to 3 after reload
        expect(screen.getByTestId('pages-count')).toHaveTextContent('3')
      })
    })

    it('should clear error after successful reload', async () => {
      // Start with error
      global.fetch = createMockFetch({ shouldFail: true })

      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderWithConfig()

      await waitFor(() => {
        expect(screen.getByTestId('error')).not.toHaveTextContent('no-error')
      })

      // Make fetch succeed
      global.fetch = createMockFetch()

      // Click reload
      await user.click(screen.getByText('Reload'))

      await waitFor(() => {
        expect(screen.getByTestId('error')).toHaveTextContent('no-error')
        expect(screen.getByTestId('has-config')).toHaveTextContent('has-config')
      })
    })
  })

  describe('useConfig Hook', () => {
    it('should throw error when used outside ConfigProvider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      expect(() => {
        render(<TestComponent />)
      }).toThrow('useConfig must be used within a ConfigProvider')

      consoleSpy.mockRestore()
    })

    it('should provide config context value', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue).toBeDefined()
        expect(configValue?.isLoading).toBe(false)
      })

      expect(configValue?.config).not.toBeNull()
      expect(configValue?.error).toBeNull()
      expect(typeof configValue?.reload).toBe('function')
    })
  })

  describe('OIDC Providers', () => {
    it('should provide OIDC providers in config', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config
          }}
        />
      )

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false)
      })

      const providers = configValue?.config?.oidcProviders
      expect(providers).toBeDefined()
      expect(providers).toHaveLength(1)
      expect(providers?.[0]).toMatchObject({
        id: 'provider-1',
        name: 'Test Provider',
        issuer: 'https://auth.example.com',
      })
    })
  })

  describe('Polling for Config Changes', () => {
    it('should poll for config changes when pollInterval is set', async () => {
      const mockFetch = createMockFetch()
      global.fetch = mockFetch

      renderWithConfig(<TestComponent />, { pollInterval: 5000 })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Initial fetch makes 4 parallel JSON:API calls
      expect(mockFetch).toHaveBeenCalledTimes(4)

      // Advance time by poll interval
      await act(async () => {
        vi.advanceTimersByTime(5000)
      })

      await waitFor(() => {
        // Second poll: another 4 calls
        expect(mockFetch).toHaveBeenCalledTimes(8)
      })

      // Advance again
      await act(async () => {
        vi.advanceTimersByTime(5000)
      })

      await waitFor(() => {
        // Third poll: another 4 calls
        expect(mockFetch).toHaveBeenCalledTimes(12)
      })
    })

    it('should not poll when pollInterval is 0', async () => {
      const mockFetch = createMockFetch()
      global.fetch = mockFetch

      renderWithConfig(<TestComponent />, { pollInterval: 0 })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Initial fetch makes 4 parallel JSON:API calls
      expect(mockFetch).toHaveBeenCalledTimes(4)

      // Advance time
      await act(async () => {
        vi.advanceTimersByTime(10000)
      })

      // Should still be 4 calls (no additional polling)
      expect(mockFetch).toHaveBeenCalledTimes(4)
    })

    it('should not poll when in error state', async () => {
      const mockFetch = createMockFetch({ shouldFail: true })
      global.fetch = mockFetch

      renderWithConfig(<TestComponent />, { pollInterval: 5000 })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Initial fetch (failed) — still makes 4 calls (all fail)
      expect(mockFetch).toHaveBeenCalledTimes(4)

      // Advance time
      await act(async () => {
        vi.advanceTimersByTime(10000)
      })

      // Should still be 4 calls (no polling during error)
      expect(mockFetch).toHaveBeenCalledTimes(4)
    })
  })
})
