/**
 * ProtectedRoute Component Tests
 *
 * Tests for the route guard component that checks authentication
 * and authorization before rendering protected routes.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 * - 2.2: Display provider selection page for multiple providers
 */

import React from 'react'
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { ProtectedRoute, hasRequiredPolicies } from './ProtectedRoute'
import type { AuthContextValue } from '../../types/auth'

// Mock the AuthContext
const mockAuthContext: AuthContextValue = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
  getAccessToken: vi.fn(),
}

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => mockAuthContext,
}))

// Helper component to display current location
function LocationDisplay(): React.ReactElement {
  const location = useLocation()
  return (
    <div data-testid="location-display">
      <span data-testid="pathname">{location.pathname}</span>
      <span data-testid="state">{JSON.stringify(location.state)}</span>
    </div>
  )
}

// Test wrapper with router
interface TestWrapperProps {
  children: React.ReactNode
  initialEntries?: string[]
}

function TestWrapper({
  children,
  initialEntries = ['/protected'],
}: TestWrapperProps): React.ReactElement {
  return (
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/protected" element={children} />
        <Route path="/login" element={<LocationDisplay />} />
        <Route path="/unauthorized" element={<LocationDisplay />} />
        <Route path="/custom-login" element={<LocationDisplay />} />
        <Route path="/custom-unauthorized" element={<LocationDisplay />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Reset mock auth context to default values
    mockAuthContext.user = null
    mockAuthContext.isAuthenticated = false
    mockAuthContext.isLoading = false
    mockAuthContext.error = null
  })

  describe('Loading State', () => {
    it('should show loading spinner while checking authentication', () => {
      mockAuthContext.isLoading = true

      render(
        <TestWrapper>
          <ProtectedRoute>
            <div>Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      const loadingContainer = screen.getByTestId('protected-route-loading')
      expect(loadingContainer).toBeInTheDocument()
      expect(loadingContainer).toHaveAttribute('aria-label', 'Checking authentication')
      expect(loadingContainer).toHaveAttribute('role', 'status')
      expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
    })

    it('should show custom loading component when provided', () => {
      mockAuthContext.isLoading = true

      render(
        <TestWrapper>
          <ProtectedRoute
            loadingComponent={<div data-testid="custom-loader">Custom Loading...</div>}
          >
            <div>Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByTestId('custom-loader')).toBeInTheDocument()
      expect(screen.getByText('Custom Loading...')).toBeInTheDocument()
    })
  })

  describe('Authentication Check', () => {
    it('should redirect to login when user is not authenticated', () => {
      mockAuthContext.isAuthenticated = false
      mockAuthContext.user = null

      render(
        <TestWrapper>
          <ProtectedRoute>
            <div>Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByTestId('pathname')).toHaveTextContent('/login')
      expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
    })

    it('should store original location in state when redirecting to login', () => {
      mockAuthContext.isAuthenticated = false
      mockAuthContext.user = null

      render(
        <TestWrapper>
          <ProtectedRoute>
            <div>Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      const stateElement = screen.getByTestId('state')
      const state = JSON.parse(stateElement.textContent || '{}')
      expect(state.from.pathname).toBe('/protected')
    })

    it('should redirect to custom login path when provided', () => {
      mockAuthContext.isAuthenticated = false
      mockAuthContext.user = null

      render(
        <TestWrapper>
          <ProtectedRoute loginPath="/custom-login">
            <div>Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByTestId('pathname')).toHaveTextContent('/custom-login')
    })

    it('should render children when user is authenticated', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'test@example.com',
        name: 'Test User',
      }

      render(
        <TestWrapper>
          <ProtectedRoute>
            <div>Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByText('Protected Content')).toBeInTheDocument()
    })
  })

  describe('Policy-Based Authorization', () => {
    it('should render children when user has required policy', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'User With Policies',
        claims: {
          policies: ['collections:read', 'collections:write'],
        },
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['collections:read']}>
            <div>Collections Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByText('Collections Content')).toBeInTheDocument()
    })

    it('should render children when user has any of the required policies', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'User With Policies',
        claims: {
          policies: ['collections:read'],
        },
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['collections:read', 'collections:write']}>
            <div>Collections Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByText('Collections Content')).toBeInTheDocument()
    })

    it('should redirect to unauthorized when user lacks required policy', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'User Without Policies',
        claims: {
          policies: ['other:read'],
        },
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['collections:write']}>
            <div>Collections Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByTestId('pathname')).toHaveTextContent('/unauthorized')
    })

    it('should redirect to unauthorized when user has no policies', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'User Without Policies',
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['collections:read']}>
            <div>Collections Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByTestId('pathname')).toHaveTextContent('/unauthorized')
    })

    it('should include required policies in unauthorized state', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'User Without Policies',
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['collections:read', 'collections:write']}>
            <div>Collections Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      const stateElement = screen.getByTestId('state')
      const state = JSON.parse(stateElement.textContent || '{}')
      expect(state.requiredPolicies).toEqual(['collections:read', 'collections:write'])
    })
  })

  describe('Custom Paths and Callbacks', () => {
    it('should redirect to custom unauthorized path when provided', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'Regular User',
      }

      render(
        <TestWrapper>
          <ProtectedRoute
            requiredPolicies={['admin:access']}
            unauthorizedPath="/custom-unauthorized"
          >
            <div>Admin Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByTestId('pathname')).toHaveTextContent('/custom-unauthorized')
    })

    it('should call onUnauthorized callback when authorization fails', () => {
      const onUnauthorized = vi.fn()
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'Regular User',
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['admin:access']} onUnauthorized={onUnauthorized}>
            <div>Admin Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(onUnauthorized).toHaveBeenCalledTimes(1)
    })

    it('should not call onUnauthorized when authorization succeeds', () => {
      const onUnauthorized = vi.fn()
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'admin@example.com',
        name: 'Admin User',
        claims: { policies: ['admin:access'] },
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={['admin:access']} onUnauthorized={onUnauthorized}>
            <div>Admin Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(onUnauthorized).not.toHaveBeenCalled()
    })
  })

  describe('No Authorization Requirements', () => {
    it('should render children when no roles or policies are required', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'Regular User',
      }

      render(
        <TestWrapper>
          <ProtectedRoute>
            <div>Public Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByText('Public Protected Content')).toBeInTheDocument()
    })

    it('should render children with empty policy array', () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'user@example.com',
        name: 'Regular User',
      }

      render(
        <TestWrapper>
          <ProtectedRoute requiredPolicies={[]}>
            <div>Public Protected Content</div>
          </ProtectedRoute>
        </TestWrapper>
      )

      expect(screen.getByText('Public Protected Content')).toBeInTheDocument()
    })
  })
})

describe('hasRequiredPolicies', () => {
  it('should return true when no policies are required', () => {
    expect(hasRequiredPolicies(['collections:read'], [])).toBe(true)
    expect(hasRequiredPolicies(undefined, [])).toBe(true)
  })

  it('should return true when user has required policy', () => {
    expect(
      hasRequiredPolicies(['collections:read', 'collections:write'], ['collections:read'])
    ).toBe(true)
  })

  it('should return true when user has any of the required policies', () => {
    expect(
      hasRequiredPolicies(['collections:read'], ['collections:read', 'collections:write'])
    ).toBe(true)
  })

  it('should return false when user lacks required policy', () => {
    expect(hasRequiredPolicies(['other:read'], ['collections:read'])).toBe(false)
  })

  it('should return false when user has no policies', () => {
    expect(hasRequiredPolicies(undefined, ['collections:read'])).toBe(false)
    expect(hasRequiredPolicies([], ['collections:read'])).toBe(false)
  })
})
