/**
 * Header Component Tests
 *
 * Tests for the Header component including branding display,
 * user menu rendering, and responsive behavior.
 *
 * Requirements tested:
 * - 1.5: Apply branding including logo, application name
 * - 2.6: User menu with logout option
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Header, type HeaderProps } from './Header'
import type { BrandingConfig } from '../../types/config'
import type { User } from '../../types/auth'

// Mock react-router-dom
const mockNavigate = vi.fn()
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useParams: () => ({ tenantSlug: 'default' }),
}))

// Mock TenantContext
vi.mock('../../context/TenantContext', () => ({
  getTenantSlug: () => 'default',
}))

// Mock the AppShell context
vi.mock('../AppShell', () => ({
  useAppShell: vi.fn(() => ({
    screenSize: 'desktop',
    sidebarCollapsed: false,
    sidebarOpen: false,
    toggleSidebar: vi.fn(),
    toggleMobileSidebar: vi.fn(),
    closeMobileSidebar: vi.fn(),
  })),
}))

// Mock SearchModal and RecentItemsDropdown to isolate Header tests
vi.mock('../SearchModal', () => ({
  SearchModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="search-modal">Search</div> : null,
}))

vi.mock('../RecentItemsDropdown', () => ({
  RecentItemsDropdown: () => <div data-testid="recent-items-dropdown">Recent</div>,
}))

// Mock UserMenu — Header delegates user menu to this shared component
vi.mock('../UserMenu', () => ({
  UserMenu: ({
    user,
    variant,
    compact,
  }: {
    user: User
    onLogout: () => void
    variant: string
    compact?: boolean
  }) => (
    <div data-testid="user-menu" data-variant={variant} data-compact={compact}>
      <span data-testid="user-menu-user-name">{user.name || user.email}</span>
    </div>
  ),
}))

// Import the mocked module to control it in tests
import { useAppShell } from '../AppShell'
const mockUseAppShell = vi.mocked(useAppShell)

describe('Header', () => {
  // Default test props
  const defaultBranding: BrandingConfig = {
    logoUrl: '/logo.png',
    applicationName: 'EMF Admin',
    faviconUrl: '/favicon.ico',
  }

  const defaultUser: User = {
    id: 'user-1',
    email: 'john.doe@example.com',
    name: 'John Doe',
    picture: undefined,
    roles: ['admin'],
  }

  const defaultProps: HeaderProps = {
    branding: defaultBranding,
    user: defaultUser,
    onLogout: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAppShell.mockReturnValue({
      screenSize: 'desktop',
      sidebarCollapsed: false,
      sidebarOpen: false,
      toggleSidebar: vi.fn(),
      toggleMobileSidebar: vi.fn(),
      closeMobileSidebar: vi.fn(),
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Branding Display', () => {
    it('should render the header component', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('header')).toBeInTheDocument()
    })

    it('should display the application logo when logoUrl is provided', () => {
      render(<Header {...defaultProps} />)
      const logo = screen.getByTestId('header-logo')
      expect(logo).toBeInTheDocument()
      expect(logo).toHaveAttribute('src', '/logo.png')
      expect(logo).toHaveAttribute('alt', 'EMF Admin logo')
    })

    it('should not display logo when logoUrl is empty', () => {
      const branding = { ...defaultBranding, logoUrl: '' }
      render(<Header {...defaultProps} branding={branding} />)
      expect(screen.queryByTestId('header-logo')).not.toBeInTheDocument()
    })

    it('should display the application name on desktop', () => {
      render(<Header {...defaultProps} />)
      const appName = screen.getByTestId('header-app-name')
      expect(appName).toBeInTheDocument()
      expect(appName).toHaveTextContent('EMF Admin')
    })

    it('should hide application name on mobile', () => {
      mockUseAppShell.mockReturnValue({
        screenSize: 'mobile',
        sidebarCollapsed: false,
        sidebarOpen: false,
        toggleSidebar: vi.fn(),
        toggleMobileSidebar: vi.fn(),
        closeMobileSidebar: vi.fn(),
      })

      render(<Header {...defaultProps} />)
      expect(screen.queryByTestId('header-app-name')).not.toBeInTheDocument()
    })
  })

  describe('User Menu Integration', () => {
    it('should render UserMenu when user is authenticated', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu')).toBeInTheDocument()
    })

    it('should not render UserMenu when user is null', () => {
      render(<Header {...defaultProps} user={null} />)
      expect(screen.queryByTestId('user-menu')).not.toBeInTheDocument()
    })

    it('should pass variant="admin" to UserMenu', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu')).toHaveAttribute('data-variant', 'admin')
    })

    it('should pass compact=true to UserMenu on mobile', () => {
      mockUseAppShell.mockReturnValue({
        screenSize: 'mobile',
        sidebarCollapsed: false,
        sidebarOpen: false,
        toggleSidebar: vi.fn(),
        toggleMobileSidebar: vi.fn(),
        closeMobileSidebar: vi.fn(),
      })

      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu')).toHaveAttribute('data-compact', 'true')
    })

    it('should pass compact=false to UserMenu on desktop', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu')).toHaveAttribute('data-compact', 'false')
    })

    it('should pass user to UserMenu', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu-user-name')).toHaveTextContent('John Doe')
    })
  })

  describe('Search', () => {
    it('should render search trigger', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('search-trigger')).toBeInTheDocument()
    })

    it('should render back to app button', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('back-to-app-button')).toBeInTheDocument()
    })

    it('should render recent items dropdown', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('recent-items-dropdown')).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have role="banner" on header element', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('header')).toHaveAttribute('role', 'banner')
    })
  })

  describe('Responsive Behavior', () => {
    it('should show full layout on desktop', () => {
      mockUseAppShell.mockReturnValue({
        screenSize: 'desktop',
        sidebarCollapsed: false,
        sidebarOpen: false,
        toggleSidebar: vi.fn(),
        toggleMobileSidebar: vi.fn(),
        closeMobileSidebar: vi.fn(),
      })

      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('header-app-name')).toBeInTheDocument()
    })

    it('should show compact layout on mobile', () => {
      mockUseAppShell.mockReturnValue({
        screenSize: 'mobile',
        sidebarCollapsed: false,
        sidebarOpen: false,
        toggleSidebar: vi.fn(),
        toggleMobileSidebar: vi.fn(),
        closeMobileSidebar: vi.fn(),
      })

      render(<Header {...defaultProps} />)
      expect(screen.queryByTestId('header-app-name')).not.toBeInTheDocument()
    })
  })
})
