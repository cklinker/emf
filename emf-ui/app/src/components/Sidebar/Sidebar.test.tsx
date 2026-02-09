/**
 * Sidebar Component Tests
 *
 * Tests for the Sidebar component including the three-section layout
 * (My Workspace, Tools, Setup), menu rendering inside Setup,
 * nested menu items, collapsed state, and accessibility.
 *
 * Requirements tested:
 * - 1.3: Configure navigation menus based on menu definitions
 * - 17.4: Collapse navigation menu on mobile
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { Sidebar, type SidebarProps } from './Sidebar'
import type { MenuConfig } from '../../types/config'

// Mock the I18nContext
vi.mock('../../context/I18nContext', () => ({
  useI18n: vi.fn(() => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string) => {
      const translations: Record<string, string> = {
        'navigation.main': 'Main navigation',
        'navigation.menu': 'Menu',
        'navigation.noMenus': 'No menus configured',
        'navigation.home': 'Home',
        'sidebar.workspace': 'My Workspace',
        'sidebar.allCollections': 'All Collections',
        'sidebar.tools': 'Tools',
        'sidebar.reports': 'Reports',
        'sidebar.dashboards': 'Dashboards',
        'sidebar.setup': 'Setup',
        'sidebar.systemHealth': 'System Health',
        'sidebar.monitoring': 'Monitoring',
      }
      return translations[key] || key
    },
    formatDate: vi.fn(),
    formatNumber: vi.fn(),
    direction: 'ltr' as const,
  })),
}))

// Mock AuthContext
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({
    user: { id: 'test-user', email: 'test@example.com' },
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  })),
}))

// Mock ApiContext
vi.mock('../../context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: vi.fn().mockResolvedValue([]),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  })),
}))

// Mock useFavorites
vi.mock('../../hooks/useFavorites', () => ({
  useFavorites: vi.fn(() => ({
    favorites: [],
    addFavorite: vi.fn(),
    removeFavorite: vi.fn(),
    isFavorite: vi.fn(() => false),
    clearFavorites: vi.fn(),
  })),
}))

// Mock react-query
vi.mock('@tanstack/react-query', () => ({
  useQuery: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
  useQueryClient: vi.fn(() => ({
    invalidateQueries: vi.fn(),
  })),
}))

// Helper to render Sidebar with router
function renderSidebar(props: Partial<SidebarProps> = {}, initialRoute: string = '/') {
  const defaultProps: SidebarProps = {
    menus: [],
    collapsed: false,
    onToggle: vi.fn(),
    ...props,
  }

  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <Sidebar {...defaultProps} />
    </MemoryRouter>
  )
}

/** Expand the Setup section by clicking its toggle button */
async function expandSetup(user: ReturnType<typeof userEvent.setup>) {
  const setupSection = screen.getByTestId('setup-section')
  const toggle = within(setupSection).getByRole('button', { name: /Setup/ })
  await user.click(toggle)
}

// Sample menu configurations for testing
const sampleMenus: MenuConfig[] = [
  {
    id: 'main',
    name: 'Main Menu',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        path: '/dashboard',
        icon: 'dashboard',
      },
      {
        id: 'collections',
        label: 'Collections',
        path: '/collections',
        icon: 'collections',
      },
    ],
  },
]

const nestedMenus: MenuConfig[] = [
  {
    id: 'admin',
    name: 'Administration',
    items: [
      {
        id: 'security',
        label: 'Security',
        icon: 'security',
        children: [
          {
            id: 'roles',
            label: 'Roles',
            path: '/admin/roles',
            icon: 'roles',
          },
          {
            id: 'policies',
            label: 'Policies',
            path: '/admin/policies',
            icon: 'policies',
          },
        ],
      },
      {
        id: 'settings',
        label: 'Settings',
        path: '/admin/settings',
        icon: 'settings',
      },
    ],
  },
]

const multipleMenus: MenuConfig[] = [
  {
    id: 'main',
    name: 'Main',
    items: [{ id: 'home-link', label: 'Home Link', path: '/home-link', icon: 'home' }],
  },
  {
    id: 'admin',
    name: 'Admin',
    items: [{ id: 'users', label: 'Users', path: '/users', icon: 'users' }],
  },
]

describe('Sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Basic Rendering', () => {
    it('should render the sidebar component', () => {
      renderSidebar({ menus: sampleMenus })
      expect(screen.getByTestId('sidebar')).toBeInTheDocument()
    })

    it('should have proper aria-label for navigation', () => {
      renderSidebar({ menus: sampleMenus })
      expect(screen.getByTestId('sidebar')).toHaveAttribute('aria-label', 'Main navigation')
    })

    it('should render all three sections', () => {
      renderSidebar({ menus: sampleMenus })
      expect(screen.getByTestId('workspace-section')).toBeInTheDocument()
      expect(screen.getByTestId('tools-section')).toBeInTheDocument()
      expect(screen.getByTestId('setup-section')).toBeInTheDocument()
    })

    it('should render section titles', () => {
      renderSidebar({ menus: sampleMenus })
      expect(screen.getByText('My Workspace')).toBeInTheDocument()
      expect(screen.getByText('Tools')).toBeInTheDocument()
      expect(screen.getByText('Setup')).toBeInTheDocument()
    })

    it('should render empty state when no menus and no collections', () => {
      renderSidebar({ menus: [] })
      expect(screen.getByTestId('sidebar-empty')).toBeInTheDocument()
      expect(screen.getByText('No menus configured')).toBeInTheDocument()
    })
  })

  describe('My Workspace Section', () => {
    it('should render Home link', () => {
      renderSidebar({ menus: [] })
      expect(screen.getByTestId('menu-item-home')).toBeInTheDocument()
      expect(screen.getByText('Home')).toBeInTheDocument()
    })

    it('should render All Collections link', () => {
      renderSidebar({ menus: [] })
      expect(screen.getByTestId('menu-item-all-collections')).toBeInTheDocument()
      expect(screen.getByText('All Collections')).toBeInTheDocument()
    })
  })

  describe('Tools Section', () => {
    it('should render Reports link', () => {
      renderSidebar({ menus: [] })
      expect(screen.getByTestId('menu-item-reports')).toBeInTheDocument()
      expect(screen.getByText('Reports')).toBeInTheDocument()
    })

    it('should render Dashboards link', () => {
      renderSidebar({ menus: [] })
      expect(screen.getByTestId('menu-item-dashboards')).toBeInTheDocument()
      expect(screen.getByText('Dashboards')).toBeInTheDocument()
    })
  })

  describe('Setup Section', () => {
    it('should have Setup collapsed by default', () => {
      renderSidebar({ menus: sampleMenus })
      // Menu items from menus prop should not be visible until Setup is expanded
      expect(screen.queryByTestId('menu-item-dashboard')).not.toBeInTheDocument()
    })

    it('should expand Setup when toggle is clicked', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      await expandSetup(user)

      expect(screen.getByTestId('menu-item-dashboard')).toBeInTheDocument()
      expect(screen.getByTestId('menu-item-collections')).toBeInTheDocument()
    })

    it('should render menu section titles inside Setup', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      await expandSetup(user)
      expect(screen.getByText('Main Menu')).toBeInTheDocument()
    })

    it('should render menu item labels inside Setup', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      await expandSetup(user)
      expect(screen.getByText('Dashboard')).toBeInTheDocument()
      expect(screen.getByText('Collections')).toBeInTheDocument()
    })

    it('should render menu item icons inside Setup', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      await expandSetup(user)
      // dashboard icon '📊' appears in both Tools (Dashboards) and Setup (Dashboard)
      expect(screen.getAllByText('📊').length).toBeGreaterThanOrEqual(2)
      expect(screen.getByText('📁')).toBeInTheDocument() // collections
    })

    it('should render multiple menu sections inside Setup', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: multipleMenus })

      await expandSetup(user)
      expect(screen.getByText('Main')).toBeInTheDocument()
      expect(screen.getByText('Admin')).toBeInTheDocument()
    })

    it('should render System Health link inside Setup', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      await expandSetup(user)
      expect(screen.getByTestId('menu-item-system-health')).toBeInTheDocument()
      expect(screen.getByText('System Health')).toBeInTheDocument()
    })

    it('should have aria-expanded on Setup toggle', () => {
      renderSidebar({ menus: sampleMenus })
      const setupSection = screen.getByTestId('setup-section')
      const toggle = within(setupSection).getByRole('button', { name: /Setup/ })
      expect(toggle).toHaveAttribute('aria-expanded', 'false')
    })

    it('should toggle aria-expanded when Setup is clicked', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      const setupSection = screen.getByTestId('setup-section')
      const toggle = within(setupSection).getByRole('button', { name: /Setup/ })

      await user.click(toggle)
      expect(toggle).toHaveAttribute('aria-expanded', 'true')

      await user.click(toggle)
      expect(toggle).toHaveAttribute('aria-expanded', 'false')
    })

    it('should auto-expand Setup when current route matches a setup path', () => {
      renderSidebar({ menus: sampleMenus }, '/collections')
      // Setup should auto-expand because /collections is a setup path
      expect(screen.getByTestId('menu-item-dashboard')).toBeInTheDocument()
    })
  })

  describe('Navigation Links', () => {
    it('should render menu items as links when path is provided', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus })

      await expandSetup(user)

      const dashboardItem = screen.getByTestId('menu-item-dashboard')
      const link = within(dashboardItem).getByRole('link')
      expect(link).toHaveAttribute('href', '/dashboard')
    })

    it('should highlight active menu item based on current route', () => {
      // /collections auto-expands Setup
      renderSidebar({ menus: sampleMenus }, '/collections')

      const collectionsItem = screen.getByTestId('menu-item-collections')
      const link = within(collectionsItem).getByRole('link')
      expect(link.className).toMatch(/menuItemContent--active/)
    })

    it('should call onItemClick when menu item is clicked', async () => {
      const onItemClick = vi.fn()
      const user = userEvent.setup()
      renderSidebar({ menus: sampleMenus, onItemClick })

      await expandSetup(user)

      const dashboardItem = screen.getByTestId('menu-item-dashboard')
      const link = within(dashboardItem).getByRole('link')
      await user.click(link)

      expect(onItemClick).toHaveBeenCalledTimes(1)
    })

    it('should call onItemClick for workspace links', async () => {
      const onItemClick = vi.fn()
      const user = userEvent.setup()
      renderSidebar({ menus: [], onItemClick })

      const homeItem = screen.getByTestId('menu-item-home')
      const link = within(homeItem).getByRole('link')
      await user.click(link)

      expect(onItemClick).toHaveBeenCalledTimes(1)
    })
  })

  describe('Nested Menu Items', () => {
    it('should render parent items with children as buttons', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')
      expect(button).toBeInTheDocument()
    })

    it('should not show nested items initially', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const submenu = screen.getByRole('group', { name: 'Security submenu' })
      expect(submenu).not.toHaveClass('submenu--expanded')
    })

    it('should expand nested items when parent is clicked', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')
      await user.click(button)

      const submenu = screen.getByRole('group', { name: 'Security submenu' })
      expect(submenu.className).toMatch(/submenu--expanded/)
    })

    it('should collapse nested items when parent is clicked again', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')

      await user.click(button)
      expect(screen.getByRole('group', { name: 'Security submenu' }).className).toMatch(
        /submenu--expanded/
      )

      await user.click(button)
      expect(screen.getByRole('group', { name: 'Security submenu' }).className).not.toMatch(
        /submenu--expanded/
      )
    })

    it('should render nested menu items', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')
      await user.click(button)

      expect(screen.getByTestId('menu-item-roles')).toBeInTheDocument()
      expect(screen.getByTestId('menu-item-policies')).toBeInTheDocument()
    })

    it('should have proper aria-expanded attribute on expandable items', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')

      expect(button).toHaveAttribute('aria-expanded', 'false')

      await user.click(button)
      expect(button).toHaveAttribute('aria-expanded', 'true')
    })

    it('should have aria-controls linking to submenu', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')

      expect(button).toHaveAttribute('aria-controls', 'submenu-security')
      expect(screen.getByRole('group', { name: 'Security submenu' })).toHaveAttribute(
        'id',
        'submenu-security'
      )
    })
  })

  describe('Collapsed State', () => {
    it('should hide section titles when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true })
      expect(screen.queryByText('My Workspace')).not.toBeInTheDocument()
      expect(screen.queryByText('Tools')).not.toBeInTheDocument()
    })

    it('should hide menu item labels when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true })
      expect(screen.queryByText('Home')).not.toBeInTheDocument()
      expect(screen.queryByText('Reports')).not.toBeInTheDocument()
    })

    it('should still show icons when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true })
      // Home icon should be visible in workspace section
      expect(screen.getByText('🏠')).toBeInTheDocument()
    })

    it('should add title attribute for tooltip when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true })
      const homeItem = screen.getByTestId('menu-item-home')
      const link = within(homeItem).getByRole('link')
      expect(link).toHaveAttribute('title', 'Home')
    })

    it('should hide nested items when collapsed', async () => {
      // Even with Setup "expanded", collapsed sidebar hides submenus
      localStorage.setItem('emf_sidebar_setup_collapsed', 'true')
      renderSidebar({ menus: nestedMenus, collapsed: true })
      expect(screen.queryByRole('group', { name: 'Security submenu' })).not.toBeInTheDocument()
    })

    it('should hide empty state text when collapsed', () => {
      renderSidebar({ menus: [], collapsed: true })
      expect(screen.queryByText('No menus configured')).not.toBeInTheDocument()
    })
  })

  describe('Active State', () => {
    it('should mark current route as active for workspace links', () => {
      renderSidebar({ menus: [] }, '/')
      const homeItem = screen.getByTestId('menu-item-home')
      expect(homeItem.className).toMatch(/menuItem--active/)
    })

    it('should set aria-current on active link', () => {
      renderSidebar({ menus: [] }, '/')
      const homeItem = screen.getByTestId('menu-item-home')
      const link = within(homeItem).getByRole('link')
      expect(link).toHaveAttribute('aria-current', 'page')
    })

    it('should not set aria-current on inactive links', () => {
      renderSidebar({ menus: [] }, '/some-other-page')
      const homeItem = screen.getByTestId('menu-item-home')
      const link = within(homeItem).getByRole('link')
      expect(link).not.toHaveAttribute('aria-current')
    })

    it('should mark setup menu item as active when route matches', () => {
      renderSidebar({ menus: sampleMenus }, '/collections')
      const collectionsItem = screen.getByTestId('menu-item-collections')
      expect(collectionsItem.className).toMatch(/menuItem--active/)
    })
  })

  describe('Accessibility', () => {
    it('should have role="menubar" on workspace menu list', () => {
      renderSidebar({ menus: sampleMenus })
      expect(screen.getByRole('menubar', { name: 'My Workspace' })).toBeInTheDocument()
    })

    it('should have role="menubar" on tools menu list', () => {
      renderSidebar({ menus: sampleMenus })
      expect(screen.getByRole('menubar', { name: 'Tools' })).toBeInTheDocument()
    })

    it('should have role="group" on submenus inside setup', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const submenu = screen.getByRole('group', { name: 'Security submenu' })
      expect(submenu).toBeInTheDocument()
    })

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: [] })

      const homeItem = screen.getByTestId('menu-item-home')
      const link = within(homeItem).getByRole('link')

      await user.tab()
      expect(link).toHaveFocus()
    })

    it('should support keyboard expansion of nested items', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')

      button.focus()
      await user.keyboard('{Enter}')

      expect(button).toHaveAttribute('aria-expanded', 'true')
    })

    it('should support Space key for expanding nested items', async () => {
      const user = userEvent.setup()
      renderSidebar({ menus: nestedMenus })

      await expandSetup(user)

      const securityItem = screen.getByTestId('menu-item-security')
      const button = within(securityItem).getByRole('button')

      button.focus()
      await user.keyboard(' ')

      expect(button).toHaveAttribute('aria-expanded', 'true')
    })
  })

  describe('Menu Items Without Path', () => {
    it('should render items without path as non-interactive', async () => {
      const menusWithNoPath: MenuConfig[] = [
        {
          id: 'test',
          name: 'Test',
          items: [{ id: 'label-only', label: 'Label Only', icon: 'home' }],
        },
      ]

      const user = userEvent.setup()
      renderSidebar({ menus: menusWithNoPath })

      await expandSetup(user)

      const item = screen.getByTestId('menu-item-label-only')
      expect(within(item).queryByRole('link')).not.toBeInTheDocument()
      expect(within(item).queryByRole('button')).not.toBeInTheDocument()
    })
  })

  describe('Icon Mapping', () => {
    it('should map known icon names to emoji', async () => {
      const menusWithIcons: MenuConfig[] = [
        {
          id: 'icons',
          name: 'Icons',
          items: [{ id: 'users-link', label: 'Users', path: '/users', icon: 'users' }],
        },
      ]

      const user = userEvent.setup()
      renderSidebar({ menus: menusWithIcons })

      await expandSetup(user)

      expect(screen.getByText('👥')).toBeInTheDocument() // users
    })

    it('should use icon name as fallback for unknown icons', async () => {
      const menusWithUnknownIcon: MenuConfig[] = [
        {
          id: 'test',
          name: 'Test',
          items: [{ id: 'custom', label: 'Custom', path: '/custom', icon: 'custom-icon' }],
        },
      ]

      const user = userEvent.setup()
      renderSidebar({ menus: menusWithUnknownIcon })

      await expandSetup(user)
      expect(screen.getByText('custom-icon')).toBeInTheDocument()
    })

    it('should handle items without icons', async () => {
      const menusWithoutIcons: MenuConfig[] = [
        {
          id: 'test',
          name: 'Test',
          items: [{ id: 'no-icon', label: 'No Icon', path: '/no-icon' }],
        },
      ]

      const user = userEvent.setup()
      renderSidebar({ menus: menusWithoutIcons })

      await expandSetup(user)
      expect(screen.getByText('No Icon')).toBeInTheDocument()
    })
  })

  describe('Deeply Nested Menus', () => {
    it('should support multiple levels of nesting', async () => {
      const deeplyNestedMenus: MenuConfig[] = [
        {
          id: 'deep',
          name: 'Deep',
          items: [
            {
              id: 'level1',
              label: 'Level 1',
              icon: 'home',
              children: [
                {
                  id: 'level2',
                  label: 'Level 2',
                  icon: 'settings',
                  children: [
                    {
                      id: 'level3',
                      label: 'Level 3',
                      path: '/deep/level3',
                      icon: 'users',
                    },
                  ],
                },
              ],
            },
          ],
        },
      ]

      const user = userEvent.setup()
      renderSidebar({ menus: deeplyNestedMenus })

      // First expand Setup
      await expandSetup(user)

      // Expand level 1
      const level1Item = screen.getByTestId('menu-item-level1')
      const level1Button = within(level1Item).getByRole('button', { name: /Level 1/ })
      await user.click(level1Button)

      // Expand level 2
      const level2Item = screen.getByTestId('menu-item-level2')
      const level2Button = within(level2Item).getByRole('button', { name: /Level 2/ })
      await user.click(level2Button)

      // Level 3 should be visible
      expect(screen.getByTestId('menu-item-level3')).toBeInTheDocument()
      expect(screen.getByText('Level 3')).toBeInTheDocument()
    })
  })
})
