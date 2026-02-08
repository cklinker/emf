/**
 * AuthContext Unit Tests
 *
 * Tests for the authentication context and useAuth hook.
 * Validates requirements 2.1-2.8 for authentication flow.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from './AuthContext'
import type { ReactNode } from 'react'

// Store original fetch
const originalFetch = global.fetch

// Mock sessionStorage
const mockSessionStorage: Record<string, string> = {}
const sessionStorageMock = {
  getItem: vi.fn((key: string) => mockSessionStorage[key] || null),
  setItem: vi.fn((key: string, value: string) => {
    mockSessionStorage[key] = value
  }),
  removeItem: vi.fn((key: string) => {
    delete mockSessionStorage[key]
  }),
  clear: vi.fn(() => {
    Object.keys(mockSessionStorage).forEach((key) => delete mockSessionStorage[key])
  }),
  length: 0,
  key: vi.fn(),
}

// Mock window.location
const originalLocation = window.location
let mockLocationHref = 'http://localhost:3000/dashboard'

// Mock bootstrap config response
const mockBootstrapConfig = {
  oidcProviders: [
    {
      id: 'provider-1',
      name: 'Test Provider',
      issuer: 'https://auth.example.com',
    },
  ],
  pages: [],
  menus: [],
  theme: {
    primaryColor: '#000',
    secondaryColor: '#fff',
    fontFamily: 'sans-serif',
    borderRadius: '4px',
  },
  branding: {
    logoUrl: '/logo.png',
    applicationName: 'Test App',
    faviconUrl: '/favicon.ico',
  },
  features: {
    enableBuilder: true,
    enableResourceBrowser: true,
    enablePackages: true,
    enableMigrations: true,
    enableDashboard: true,
  },
}

// Mock OIDC discovery document
const mockDiscoveryDoc = {
  issuer: 'https://auth.example.com',
  authorization_endpoint: 'https://auth.example.com/authorize',
  token_endpoint: 'https://auth.example.com/token',
  userinfo_endpoint: 'https://auth.example.com/userinfo',
  end_session_endpoint: 'https://auth.example.com/logout',
  jwks_uri: 'https://auth.example.com/.well-known/jwks.json',
  response_types_supported: ['code'],
}

// Create mock fetch function
function createMockFetch(overrides: Record<string, unknown> = {}) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url =
      typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url

    if (url.includes('/control/ui-bootstrap')) {
      const config = overrides.bootstrapConfig || mockBootstrapConfig
      return {
        ok: true,
        json: async () => config,
      } as Response
    }

    if (url.includes('.well-known/openid-configuration')) {
      return {
        ok: true,
        json: async () => mockDiscoveryDoc,
      } as Response
    }

    if (url === mockDiscoveryDoc.token_endpoint) {
      if (overrides.tokenError) {
        return {
          ok: false,
          statusText: 'Unauthorized',
        } as Response
      }
      return {
        ok: true,
        json: async () =>
          overrides.tokenResponse || {
            access_token: 'new-access-token',
            id_token: 'new-id-token',
            refresh_token: 'new-refresh-token',
            token_type: 'Bearer',
            expires_in: 3600,
          },
      } as Response
    }

    return {
      ok: false,
      statusText: 'Not Found',
    } as Response
  })
}

// Test component that uses useAuth
function TestComponent({ onRender }: { onRender?: (auth: ReturnType<typeof useAuth>) => void }) {
  const auth = useAuth()
  onRender?.(auth)
  return (
    <div>
      <div data-testid="loading">{auth.isLoading ? 'loading' : 'not-loading'}</div>
      <div data-testid="authenticated">
        {auth.isAuthenticated ? 'authenticated' : 'not-authenticated'}
      </div>
      <div data-testid="user">{auth.user ? auth.user.email : 'no-user'}</div>
      <div data-testid="error">{auth.error ? auth.error.message : 'no-error'}</div>
      <button onClick={() => auth.login().catch(() => {})}>Login</button>
      <button onClick={() => auth.logout()}>Logout</button>
    </div>
  )
}

// Helper to render with AuthProvider
function renderWithAuth(ui: ReactNode = <TestComponent />) {
  return render(<AuthProvider>{ui}</AuthProvider>)
}

// Helper to create a valid JWT token
function createMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()

    // Clear mock storage
    Object.keys(mockSessionStorage).forEach((key) => delete mockSessionStorage[key])

    // Reset location mock
    mockLocationHref = 'http://localhost:3000/dashboard'

    // Setup sessionStorage mock
    Object.defineProperty(window, 'sessionStorage', {
      value: sessionStorageMock,
      writable: true,
    })

    // Setup location mock
    delete (window as { location?: Location }).location
    Object.defineProperty(window, 'location', {
      value: {
        origin: 'http://localhost:3000',
        pathname: '/dashboard',
        search: '',
        get href() {
          return mockLocationHref
        },
        set href(value: string) {
          mockLocationHref = value
        },
      },
      writable: true,
      configurable: true,
    })

    // Setup history mock
    Object.defineProperty(window, 'history', {
      value: {
        replaceState: vi.fn(),
      },
      writable: true,
    })

    // Setup default fetch mock
    global.fetch = createMockFetch()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    global.fetch = originalFetch

    // Restore original location
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
      configurable: true,
    })
  })

  describe('Initial State', () => {
    it('should start in loading state', async () => {
      renderWithAuth()

      // Initially loading
      expect(screen.getByTestId('loading')).toHaveTextContent('loading')

      // Wait for initialization to complete
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })
    })

    it('should be unauthenticated initially when no tokens stored', async () => {
      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('authenticated')).toHaveTextContent('not-authenticated')
      expect(screen.getByTestId('user')).toHaveTextContent('no-user')
    })

    it('should fetch bootstrap config on mount', async () => {
      const mockFetch = createMockFetch()
      global.fetch = mockFetch

      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Verify fetch was called
      expect(mockFetch).toHaveBeenCalled()
      const calls = mockFetch.mock.calls
      const bootstrapCall = calls.find((call) => {
        const url = typeof call[0] === 'string' ? call[0] : call[0]?.url
        return url?.includes('/control/ui-bootstrap')
      })
      expect(bootstrapCall).toBeDefined()
    })
  })

  describe('useAuth Hook', () => {
    it('should throw error when used outside AuthProvider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      expect(() => {
        render(<TestComponent />)
      }).toThrow('useAuth must be used within an AuthProvider')

      consoleSpy.mockRestore()
    })

    it('should provide auth context value', async () => {
      let authValue: ReturnType<typeof useAuth> | undefined

      renderWithAuth(
        <TestComponent
          onRender={(auth) => {
            authValue = auth
          }}
        />
      )

      await waitFor(() => {
        expect(authValue).toBeDefined()
        expect(authValue?.isLoading).toBe(false)
      })

      expect(authValue?.user).toBeNull()
      expect(authValue?.isAuthenticated).toBe(false)
      expect(typeof authValue?.login).toBe('function')
      expect(typeof authValue?.logout).toBe('function')
      expect(typeof authValue?.getAccessToken).toBe('function')
    })
  })

  describe('Token Storage (Requirement 2.3)', () => {
    it('should restore user from stored tokens on mount', async () => {
      // Create a valid JWT token
      const payload = {
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }
      const mockToken = createMockJwt(payload)

      // Store tokens before rendering
      const storedTokens = {
        accessToken: mockToken,
        idToken: mockToken,
        expiresAt: Date.now() + 3600000,
      }
      mockSessionStorage['emf_auth_tokens'] = JSON.stringify(storedTokens)

      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('authenticated')).toHaveTextContent('authenticated')
      expect(screen.getByTestId('user')).toHaveTextContent('test@example.com')
    })
  })

  describe('Logout (Requirement 2.6)', () => {
    it('should clear tokens and redirect on logout', async () => {
      // Setup authenticated state
      const payload = {
        sub: 'user-123',
        email: 'test@example.com',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }
      const mockToken = createMockJwt(payload)

      const storedTokens = {
        accessToken: mockToken,
        idToken: mockToken,
        expiresAt: Date.now() + 3600000,
      }
      mockSessionStorage['emf_auth_tokens'] = JSON.stringify(storedTokens)
      mockSessionStorage['emf_auth_provider_id'] = 'provider-1'

      const user = userEvent.setup()
      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('authenticated')).toHaveTextContent('authenticated')
      })

      // Click logout
      await user.click(screen.getByText('Logout'))

      // Should have cleared storage
      expect(sessionStorageMock.removeItem).toHaveBeenCalled()
    })
  })

  describe('getAccessToken (Requirement 2.7)', () => {
    it('should return access token when valid', async () => {
      const payload = {
        sub: 'user-123',
        email: 'test@example.com',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }
      const mockToken = createMockJwt(payload)

      const storedTokens = {
        accessToken: mockToken,
        idToken: mockToken,
        expiresAt: Date.now() + 3600000,
      }
      mockSessionStorage['emf_auth_tokens'] = JSON.stringify(storedTokens)

      let authValue: ReturnType<typeof useAuth> | undefined

      renderWithAuth(
        <TestComponent
          onRender={(auth) => {
            authValue = auth
          }}
        />
      )

      await waitFor(() => {
        expect(authValue?.isLoading).toBe(false)
      })

      const token = await authValue?.getAccessToken()
      expect(token).toBe(mockToken)
    })

    it('should throw error when no tokens available', async () => {
      let authValue: ReturnType<typeof useAuth> | undefined

      renderWithAuth(
        <TestComponent
          onRender={(auth) => {
            authValue = auth
          }}
        />
      )

      await waitFor(() => {
        expect(authValue?.isLoading).toBe(false)
      })

      await expect(authValue?.getAccessToken()).rejects.toThrow('No tokens available')
    })
  })

  describe('Error Handling', () => {
    it('should handle bootstrap config fetch failure gracefully', async () => {
      global.fetch = vi.fn(async () => ({
        ok: false,
        statusText: 'Internal Server Error',
      })) as typeof fetch

      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Should still render but with no providers
      expect(screen.getByTestId('authenticated')).toHaveTextContent('not-authenticated')
    })

    it('should handle invalid stored tokens gracefully', async () => {
      // Store invalid JSON
      mockSessionStorage['emf_auth_tokens'] = 'invalid-json'

      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Should be unauthenticated
      expect(screen.getByTestId('authenticated')).toHaveTextContent('not-authenticated')
    })
  })

  describe('Token Expiration', () => {
    it('should clear expired tokens without refresh token', async () => {
      // Create expired tokens without refresh token
      const payload = {
        sub: 'user-123',
        email: 'test@example.com',
        exp: Math.floor(Date.now() / 1000) - 3600, // Expired
      }
      const mockToken = createMockJwt(payload)

      const storedTokens = {
        accessToken: mockToken,
        idToken: mockToken,
        expiresAt: Date.now() - 3600000, // Expired
      }
      mockSessionStorage['emf_auth_tokens'] = JSON.stringify(storedTokens)

      renderWithAuth()

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      // Should be unauthenticated after expired tokens are cleared
      expect(screen.getByTestId('authenticated')).toHaveTextContent('not-authenticated')
    })
  })
})
