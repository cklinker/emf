/**
 * LoginPage Component Tests
 *
 * Tests for the login page with OIDC provider selection.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 * - 2.2: Display provider selection page for multiple providers
 */

import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { LoginPage } from './LoginPage'

// Mock auth context
const mockLogin = vi.fn()
const mockAuthContext = {
  user: null as { id: string; email: string; name: string } | null,
  isAuthenticated: false,
  isLoading: false,
  error: null as Error | null,
  login: mockLogin,
  logout: vi.fn(),
  getAccessToken: vi.fn(),
}

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => mockAuthContext,
}))

// Mock config context
const mockConfigContext = {
  config: {
    branding: {
      logoUrl: '/logo.svg',
      applicationName: 'EMF Admin',
      faviconUrl: '/favicon.ico',
    },
    oidcProviders: [
      { id: 'provider-1', name: 'Google', issuer: 'https://accounts.google.com' },
      { id: 'provider-2', name: 'Okta', issuer: 'https://okta.example.com' },
    ],
  },
  isLoading: false,
  error: null,
  reload: vi.fn(),
}

vi.mock('../../context/ConfigContext', () => ({
  useConfig: () => mockConfigContext,
}))

// Mock I18n context
vi.mock('../../context/I18nContext', () => ({
  useI18n: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'login.title': 'Sign In',
        'login.checking': 'Checking authentication...',
        'login.selectProvider': 'Sign in with your identity provider',
        'login.noProviders': 'No identity providers configured.',
        'login.footer': 'Secure authentication powered by OpenID Connect',
      }
      return translations[key] || key
    },
  }),
}))

// Test wrapper with router
function TestWrapper({ children }: { children: React.ReactNode }) {
  return (
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={children} />
        <Route path="/default" element={<div data-testid="dashboard">Dashboard</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuthContext.user = null
    mockAuthContext.isAuthenticated = false
    mockAuthContext.isLoading = false
    mockAuthContext.error = null
    mockConfigContext.isLoading = false
  })

  describe('Rendering', () => {
    it('should render the login page', () => {
      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByTestId('login-page')).toBeInTheDocument()
    })

    it('should display the application name from config', () => {
      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByText('EMF Admin')).toBeInTheDocument()
    })

    it('should display custom title when provided', () => {
      render(
        <TestWrapper>
          <LoginPage title="Custom Login" />
        </TestWrapper>
      )

      expect(screen.getByText('Custom Login')).toBeInTheDocument()
    })

    it('should display the logo from config', () => {
      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      const logo = screen.getByAltText('EMF Admin')
      expect(logo).toBeInTheDocument()
      expect(logo).toHaveAttribute('src', '/logo.svg')
    })
  })

  describe('Loading State', () => {
    it('should show loading spinner while checking auth', () => {
      mockAuthContext.isLoading = true

      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument()
    })

    it('should show loading spinner while loading config', () => {
      mockConfigContext.isLoading = true

      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument()
    })
  })

  describe('Provider Selection', () => {
    it('should display all configured providers', () => {
      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByText('Google')).toBeInTheDocument()
      expect(screen.getByText('Okta')).toBeInTheDocument()
    })

    it('should display provider issuer URLs', () => {
      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByText('https://accounts.google.com')).toBeInTheDocument()
      expect(screen.getByText('https://okta.example.com')).toBeInTheDocument()
    })

    it('should call login with provider ID when clicking a provider', async () => {
      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      const googleButton = screen.getByText('Google').closest('button')
      expect(googleButton).toBeInTheDocument()

      fireEvent.click(googleButton!)

      await waitFor(() => {
        expect(mockLogin).toHaveBeenCalledWith('provider-1')
      })
    })

    it('should show no providers message when none configured', () => {
      mockConfigContext.config = {
        ...mockConfigContext.config,
        oidcProviders: [],
      }

      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByText('No identity providers configured.')).toBeInTheDocument()
    })
  })

  describe('Error Handling', () => {
    it('should display auth error when present', () => {
      mockAuthContext.error = new Error('Authentication failed')

      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      expect(screen.getByText('Authentication failed')).toBeInTheDocument()
    })
  })

  describe('Authenticated Redirect', () => {
    it('should redirect to dashboard when already authenticated', async () => {
      mockAuthContext.isAuthenticated = true
      mockAuthContext.user = {
        id: 'user-1',
        email: 'test@example.com',
        name: 'Test User',
      }

      render(
        <TestWrapper>
          <LoginPage />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByTestId('dashboard')).toBeInTheDocument()
      })
    })
  })
})
