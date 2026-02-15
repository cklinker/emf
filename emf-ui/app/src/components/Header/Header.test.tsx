/**
 * Header Component Tests
 *
 * Tests for the Header component including branding display,
 * user menu functionality, and responsive behavior.
 *
 * Requirements tested:
 * - 1.5: Apply branding including logo, application name
 * - 2.6: User menu with logout option
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Header, type HeaderProps } from './Header'
import type { BrandingConfig } from '../../types/config'
import type { User } from '../../types/auth'

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

// Mock Gravatar utility — return null by default so initials tests work;
// individual tests can override via mockGetGravatarUrl.mockReturnValue(...)
vi.mock('../../utils/gravatar', () => ({
  getGravatarUrl: vi.fn(() => null),
}))

import { getGravatarUrl } from '../../utils/gravatar'
const mockGetGravatarUrl = vi.mocked(getGravatarUrl)

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
    // Default: Gravatar returns null so initials are shown
    mockGetGravatarUrl.mockReturnValue(null)
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

  describe('User Menu', () => {
    it('should display user menu button when user is authenticated', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu-button')).toBeInTheDocument()
    })

    it('should not display user menu when user is null', () => {
      render(<Header {...defaultProps} user={null} />)
      expect(screen.queryByTestId('user-menu-button')).not.toBeInTheDocument()
    })

    it('should display user initials when no picture is provided', () => {
      render(<Header {...defaultProps} />)
      const initials = screen.getByTestId('user-avatar-initials')
      expect(initials).toBeInTheDocument()
      expect(initials).toHaveTextContent('JD') // John Doe
    })

    it('should display user avatar image when picture is provided', () => {
      const userWithPicture = { ...defaultUser, picture: '/avatar.jpg' }
      render(<Header {...defaultProps} user={userWithPicture} />)
      const avatarImage = screen.getByTestId('user-avatar-image')
      expect(avatarImage).toBeInTheDocument()
      expect(avatarImage).toHaveAttribute('src', '/avatar.jpg')
    })

    it('should display user name on desktop', () => {
      render(<Header {...defaultProps} />)
      const userName = screen.getByTestId('user-name')
      expect(userName).toBeInTheDocument()
      expect(userName).toHaveTextContent('John Doe')
    })

    it('should hide user name on mobile', () => {
      mockUseAppShell.mockReturnValue({
        screenSize: 'mobile',
        sidebarCollapsed: false,
        sidebarOpen: false,
        toggleSidebar: vi.fn(),
        toggleMobileSidebar: vi.fn(),
        closeMobileSidebar: vi.fn(),
      })

      render(<Header {...defaultProps} />)
      expect(screen.queryByTestId('user-name')).not.toBeInTheDocument()
    })

    it('should display email as fallback when name is not provided', () => {
      const userWithoutName = { ...defaultUser, name: undefined }
      render(<Header {...defaultProps} user={userWithoutName} />)
      const userName = screen.getByTestId('user-name')
      expect(userName).toHaveTextContent('john.doe@example.com')
    })

    it('should display Gravatar image when available', () => {
      mockGetGravatarUrl.mockReturnValue('https://www.gravatar.com/avatar/abc123?s=64&d=404')
      render(<Header {...defaultProps} />)
      const avatarImage = screen.getByTestId('user-avatar-image')
      expect(avatarImage).toBeInTheDocument()
      expect(avatarImage).toHaveAttribute(
        'src',
        'https://www.gravatar.com/avatar/abc123?s=64&d=404'
      )
    })

    it('should prefer OIDC picture over Gravatar', () => {
      mockGetGravatarUrl.mockReturnValue('https://www.gravatar.com/avatar/abc123?s=64&d=404')
      const userWithPicture = { ...defaultUser, picture: '/oidc-avatar.jpg' }
      render(<Header {...defaultProps} user={userWithPicture} />)
      const avatarImage = screen.getByTestId('user-avatar-image')
      expect(avatarImage).toHaveAttribute('src', '/oidc-avatar.jpg')
    })

    it('should fall back to initials when Gravatar image fails to load', async () => {
      mockGetGravatarUrl.mockReturnValue('https://www.gravatar.com/avatar/abc123?s=64&d=404')
      render(<Header {...defaultProps} />)

      // Initially shows Gravatar image
      const avatarImage = screen.getByTestId('user-avatar-image')
      expect(avatarImage).toBeInTheDocument()

      // Simulate image load error (Gravatar returned 404)
      fireEvent.error(avatarImage)

      // Should now show initials
      await waitFor(() => {
        expect(screen.getByTestId('user-avatar-initials')).toBeInTheDocument()
        expect(screen.getByTestId('user-avatar-initials')).toHaveTextContent('JD')
      })
    })
  })

  describe('Dropdown Menu', () => {
    it('should not show dropdown menu initially', () => {
      render(<Header {...defaultProps} />)
      expect(screen.queryByTestId('user-dropdown-menu')).not.toBeInTheDocument()
    })

    it('should open dropdown menu when user button is clicked', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      expect(screen.getByTestId('user-dropdown-menu')).toBeInTheDocument()
    })

    it('should close dropdown menu when clicked again', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      const menuButton = screen.getByTestId('user-menu-button')
      await user.click(menuButton)
      expect(screen.getByTestId('user-dropdown-menu')).toBeInTheDocument()

      await user.click(menuButton)
      expect(screen.queryByTestId('user-dropdown-menu')).not.toBeInTheDocument()
    })

    it('should display user info in dropdown menu', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      const dropdown = screen.getByTestId('user-dropdown-menu')
      expect(dropdown).toHaveTextContent('John Doe')
      expect(dropdown).toHaveTextContent('john.doe@example.com')
    })

    it('should display logout button in dropdown menu', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      expect(screen.getByTestId('logout-button')).toBeInTheDocument()
      expect(screen.getByTestId('logout-button')).toHaveTextContent('Logout')
    })

    it('should close dropdown when Escape key is pressed', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))
      expect(screen.getByTestId('user-dropdown-menu')).toBeInTheDocument()

      await user.keyboard('{Escape}')
      expect(screen.queryByTestId('user-dropdown-menu')).not.toBeInTheDocument()
    })

    it('should close dropdown when clicking outside', async () => {
      const user = userEvent.setup()
      render(
        <div>
          <Header {...defaultProps} />
          <div data-testid="outside">Outside</div>
        </div>
      )

      await user.click(screen.getByTestId('user-menu-button'))
      expect(screen.getByTestId('user-dropdown-menu')).toBeInTheDocument()

      // Click outside the menu
      fireEvent.mouseDown(screen.getByTestId('outside'))

      await waitFor(() => {
        expect(screen.queryByTestId('user-dropdown-menu')).not.toBeInTheDocument()
      })
    })
  })

  describe('Logout Functionality', () => {
    it('should call onLogout when logout button is clicked', async () => {
      const onLogout = vi.fn()
      const user = userEvent.setup()
      render(<Header {...defaultProps} onLogout={onLogout} />)

      await user.click(screen.getByTestId('user-menu-button'))
      await user.click(screen.getByTestId('logout-button'))

      expect(onLogout).toHaveBeenCalledTimes(1)
    })

    it('should close dropdown after logout is clicked', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))
      await user.click(screen.getByTestId('logout-button'))

      expect(screen.queryByTestId('user-dropdown-menu')).not.toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have role="banner" on header element', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('header')).toHaveAttribute('role', 'banner')
    })

    it('should have proper aria-expanded attribute on user button', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      const menuButton = screen.getByTestId('user-menu-button')
      expect(menuButton).toHaveAttribute('aria-expanded', 'false')

      await user.click(menuButton)
      expect(menuButton).toHaveAttribute('aria-expanded', 'true')
    })

    it('should have aria-haspopup attribute on user button', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu-button')).toHaveAttribute('aria-haspopup', 'menu')
    })

    it('should have aria-controls attribute when menu is open', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      const menuButton = screen.getByTestId('user-menu-button')
      expect(menuButton).not.toHaveAttribute('aria-controls')

      await user.click(menuButton)
      expect(menuButton).toHaveAttribute('aria-controls', 'user-dropdown-menu')
    })

    it('should have aria-label on user button', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-menu-button')).toHaveAttribute(
        'aria-label',
        'User menu for John Doe'
      )
    })

    it('should have role="menu" on dropdown', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      expect(screen.getByTestId('user-dropdown-menu')).toHaveAttribute('role', 'menu')
    })

    it('should have id matching aria-controls on dropdown', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      expect(screen.getByTestId('user-dropdown-menu')).toHaveAttribute('id', 'user-dropdown-menu')
    })

    it('should have role="menuitem" on logout button', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      expect(screen.getByTestId('logout-button')).toHaveAttribute('role', 'menuitem')
    })

    it('should have role="separator" on menu divider', async () => {
      const user = userEvent.setup()
      render(<Header {...defaultProps} />)

      await user.click(screen.getByTestId('user-menu-button'))

      const dropdown = screen.getByTestId('user-dropdown-menu')
      const separator = dropdown.querySelector('[role="separator"]')
      expect(separator).toBeInTheDocument()
      expect(separator).toHaveAttribute('aria-hidden', 'true')
    })
  })

  describe('User Initials Generation', () => {
    it('should generate initials from full name', () => {
      render(<Header {...defaultProps} />)
      expect(screen.getByTestId('user-avatar-initials')).toHaveTextContent('JD')
    })

    it('should generate initials from single name', () => {
      const userWithSingleName = { ...defaultUser, name: 'John' }
      render(<Header {...defaultProps} user={userWithSingleName} />)
      expect(screen.getByTestId('user-avatar-initials')).toHaveTextContent('JO')
    })

    it('should generate initials from email when name is not provided', () => {
      const userWithoutName = { ...defaultUser, name: undefined }
      render(<Header {...defaultProps} user={userWithoutName} />)
      expect(screen.getByTestId('user-avatar-initials')).toHaveTextContent('JO')
    })

    it('should generate initials from three-part name', () => {
      const userWithThreePartName = { ...defaultUser, name: 'John Michael Doe' }
      render(<Header {...defaultProps} user={userWithThreePartName} />)
      expect(screen.getByTestId('user-avatar-initials')).toHaveTextContent('JM')
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
      expect(screen.getByTestId('user-name')).toBeInTheDocument()
    })

    it('should show full layout on tablet', () => {
      mockUseAppShell.mockReturnValue({
        screenSize: 'tablet',
        sidebarCollapsed: false,
        sidebarOpen: false,
        toggleSidebar: vi.fn(),
        toggleMobileSidebar: vi.fn(),
        closeMobileSidebar: vi.fn(),
      })

      render(<Header {...defaultProps} />)

      expect(screen.getByTestId('header-app-name')).toBeInTheDocument()
      expect(screen.getByTestId('user-name')).toBeInTheDocument()
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
      expect(screen.queryByTestId('user-name')).not.toBeInTheDocument()
      // Avatar should still be visible
      expect(screen.getByTestId('user-avatar-initials')).toBeInTheDocument()
    })
  })
})
