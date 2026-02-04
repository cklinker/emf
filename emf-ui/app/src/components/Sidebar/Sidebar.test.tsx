/**
 * Sidebar Component Tests
 * 
 * Tests for the Sidebar component including menu rendering,
 * nested menu items, collapsed state, and accessibility.
 * 
 * Requirements tested:
 * - 1.3: Configure navigation menus based on menu definitions
 * - 17.4: Collapse navigation menu on mobile
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar, type SidebarProps } from './Sidebar';
import type { MenuConfig } from '../../types/config';

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
      };
      return translations[key] || key;
    },
    formatDate: vi.fn(),
    formatNumber: vi.fn(),
    direction: 'ltr' as const,
  })),
}));

// Helper to render Sidebar with router
function renderSidebar(
  props: Partial<SidebarProps> = {},
  initialRoute: string = '/'
) {
  const defaultProps: SidebarProps = {
    menus: [],
    collapsed: false,
    onToggle: vi.fn(),
    ...props,
  };

  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <Sidebar {...defaultProps} />
    </MemoryRouter>
  );
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
];

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
];

const multipleMenus: MenuConfig[] = [
  {
    id: 'main',
    name: 'Main',
    items: [
      { id: 'home', label: 'Home', path: '/', icon: 'home' },
    ],
  },
  {
    id: 'admin',
    name: 'Admin',
    items: [
      { id: 'users', label: 'Users', path: '/users', icon: 'users' },
    ],
  },
];

describe('Sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render the sidebar component', () => {
      renderSidebar({ menus: sampleMenus });
      expect(screen.getByTestId('sidebar')).toBeInTheDocument();
    });

    it('should have proper aria-label for navigation', () => {
      renderSidebar({ menus: sampleMenus });
      expect(screen.getByTestId('sidebar')).toHaveAttribute(
        'aria-label',
        'Main navigation'
      );
    });

    it('should render empty state when no menus provided', () => {
      renderSidebar({ menus: [] });
      expect(screen.getByTestId('sidebar-empty')).toBeInTheDocument();
      expect(screen.getByText('No menus configured')).toBeInTheDocument();
    });
  });

  describe('Menu Rendering', () => {
    it('should render menu sections', () => {
      renderSidebar({ menus: sampleMenus });
      expect(screen.getByTestId('menu-section-main')).toBeInTheDocument();
    });

    it('should render menu section title', () => {
      renderSidebar({ menus: sampleMenus });
      expect(screen.getByText('Main Menu')).toBeInTheDocument();
    });

    it('should render menu items', () => {
      renderSidebar({ menus: sampleMenus });
      expect(screen.getByTestId('menu-item-dashboard')).toBeInTheDocument();
      expect(screen.getByTestId('menu-item-collections')).toBeInTheDocument();
    });

    it('should render menu item labels', () => {
      renderSidebar({ menus: sampleMenus });
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Collections')).toBeInTheDocument();
    });

    it('should render menu item icons', () => {
      renderSidebar({ menus: sampleMenus });
      // Icons are rendered as emoji/unicode
      expect(screen.getByText('📊')).toBeInTheDocument(); // dashboard
      expect(screen.getByText('📁')).toBeInTheDocument(); // collections
    });

    it('should render multiple menu sections', () => {
      renderSidebar({ menus: multipleMenus });
      expect(screen.getByTestId('menu-section-main')).toBeInTheDocument();
      expect(screen.getByTestId('menu-section-admin')).toBeInTheDocument();
      expect(screen.getByText('Main')).toBeInTheDocument();
      expect(screen.getByText('Admin')).toBeInTheDocument();
    });
  });

  describe('Navigation Links', () => {
    it('should render menu items as links when path is provided', () => {
      renderSidebar({ menus: sampleMenus });
      const dashboardItem = screen.getByTestId('menu-item-dashboard');
      const link = within(dashboardItem).getByRole('link');
      expect(link).toHaveAttribute('href', '/dashboard');
    });

    it('should highlight active menu item based on current route', () => {
      renderSidebar({ menus: sampleMenus }, '/dashboard');
      const dashboardItem = screen.getByTestId('menu-item-dashboard');
      const link = within(dashboardItem).getByRole('link');
      // CSS Modules hash class names, so check for partial match
      expect(link.className).toMatch(/menuItemContent--active/);
    });

    it('should call onItemClick when menu item is clicked', async () => {
      const onItemClick = vi.fn();
      const user = userEvent.setup();
      renderSidebar({ menus: sampleMenus, onItemClick });
      
      const dashboardItem = screen.getByTestId('menu-item-dashboard');
      const link = within(dashboardItem).getByRole('link');
      await user.click(link);
      
      expect(onItemClick).toHaveBeenCalledTimes(1);
    });
  });

  describe('Nested Menu Items', () => {
    it('should render parent items with children as buttons', () => {
      renderSidebar({ menus: nestedMenus });
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      expect(button).toBeInTheDocument();
    });

    it('should not show nested items initially', () => {
      renderSidebar({ menus: nestedMenus });
      // Submenu should be collapsed (max-height: 0)
      const submenu = screen.getByRole('group', { name: 'Security submenu' });
      expect(submenu).not.toHaveClass('submenu--expanded');
    });

    it('should expand nested items when parent is clicked', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: nestedMenus });
      
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      await user.click(button);
      
      const submenu = screen.getByRole('group', { name: 'Security submenu' });
      // CSS Modules hash class names, so check for partial match
      expect(submenu.className).toMatch(/submenu--expanded/);
    });

    it('should collapse nested items when parent is clicked again', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: nestedMenus });
      
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      
      // Expand
      await user.click(button);
      // CSS Modules hash class names, so check for partial match
      expect(screen.getByRole('group', { name: 'Security submenu' }).className).toMatch(/submenu--expanded/);
      
      // Collapse
      await user.click(button);
      expect(screen.getByRole('group', { name: 'Security submenu' }).className).not.toMatch(/submenu--expanded/);
    });

    it('should render nested menu items', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: nestedMenus });
      
      // Expand the parent
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      await user.click(button);
      
      // Check nested items are rendered
      expect(screen.getByTestId('menu-item-roles')).toBeInTheDocument();
      expect(screen.getByTestId('menu-item-policies')).toBeInTheDocument();
    });

    it('should have proper aria-expanded attribute on expandable items', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: nestedMenus });
      
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      
      expect(button).toHaveAttribute('aria-expanded', 'false');
      
      await user.click(button);
      expect(button).toHaveAttribute('aria-expanded', 'true');
    });

    it('should have aria-controls linking to submenu', () => {
      renderSidebar({ menus: nestedMenus });
      
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      
      expect(button).toHaveAttribute('aria-controls', 'submenu-security');
      expect(screen.getByRole('group', { name: 'Security submenu' })).toHaveAttribute('id', 'submenu-security');
    });
  });

  describe('Collapsed State', () => {
    it('should hide menu section titles when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true });
      expect(screen.queryByText('Main Menu')).not.toBeInTheDocument();
    });

    it('should hide menu item labels when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true });
      expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
      expect(screen.queryByText('Collections')).not.toBeInTheDocument();
    });

    it('should still show icons when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true });
      expect(screen.getByText('📊')).toBeInTheDocument();
      expect(screen.getByText('📁')).toBeInTheDocument();
    });

    it('should add title attribute for tooltip when collapsed', () => {
      renderSidebar({ menus: sampleMenus, collapsed: true });
      const dashboardItem = screen.getByTestId('menu-item-dashboard');
      const link = within(dashboardItem).getByRole('link');
      expect(link).toHaveAttribute('title', 'Dashboard');
    });

    it('should hide nested items when collapsed', () => {
      renderSidebar({ menus: nestedMenus, collapsed: true });
      // Submenu should not be rendered when collapsed
      expect(screen.queryByRole('group', { name: 'Security submenu' })).not.toBeInTheDocument();
    });

    it('should hide empty state text when collapsed', () => {
      renderSidebar({ menus: [], collapsed: true });
      expect(screen.queryByText('No menus configured')).not.toBeInTheDocument();
    });
  });

  describe('Active State', () => {
    it('should mark current route as active', () => {
      renderSidebar({ menus: sampleMenus }, '/collections');
      const collectionsItem = screen.getByTestId('menu-item-collections');
      // CSS Modules hash class names, so check for partial match
      expect(collectionsItem.className).toMatch(/menuItem--active/);
    });

    it('should set aria-current on active link', () => {
      renderSidebar({ menus: sampleMenus }, '/dashboard');
      const dashboardItem = screen.getByTestId('menu-item-dashboard');
      const link = within(dashboardItem).getByRole('link');
      expect(link).toHaveAttribute('aria-current', 'page');
    });

    it('should not set aria-current on inactive links', () => {
      renderSidebar({ menus: sampleMenus }, '/dashboard');
      const collectionsItem = screen.getByTestId('menu-item-collections');
      const link = within(collectionsItem).getByRole('link');
      expect(link).not.toHaveAttribute('aria-current');
    });
  });

  describe('Accessibility', () => {
    it('should have role="menubar" on menu lists', () => {
      renderSidebar({ menus: sampleMenus });
      const menuList = screen.getByRole('menubar', { name: 'Main Menu' });
      expect(menuList).toBeInTheDocument();
    });

    it('should have role="group" on submenus', () => {
      renderSidebar({ menus: nestedMenus });
      const submenu = screen.getByRole('group', { name: 'Security submenu' });
      expect(submenu).toBeInTheDocument();
    });

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: sampleMenus });
      
      const dashboardItem = screen.getByTestId('menu-item-dashboard');
      const link = within(dashboardItem).getByRole('link');
      
      // Tab to the link
      await user.tab();
      expect(link).toHaveFocus();
    });

    it('should support keyboard expansion of nested items', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: nestedMenus });
      
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      
      // Focus and press Enter
      button.focus();
      await user.keyboard('{Enter}');
      
      expect(button).toHaveAttribute('aria-expanded', 'true');
    });

    it('should support Space key for expanding nested items', async () => {
      const user = userEvent.setup();
      renderSidebar({ menus: nestedMenus });
      
      const securityItem = screen.getByTestId('menu-item-security');
      const button = within(securityItem).getByRole('button');
      
      // Focus and press Space
      button.focus();
      await user.keyboard(' ');
      
      expect(button).toHaveAttribute('aria-expanded', 'true');
    });
  });

  describe('Menu Items Without Path', () => {
    it('should render items without path as non-interactive', () => {
      const menusWithNoPath: MenuConfig[] = [
        {
          id: 'test',
          name: 'Test',
          items: [
            { id: 'label-only', label: 'Label Only', icon: 'home' },
          ],
        },
      ];
      
      renderSidebar({ menus: menusWithNoPath });
      const item = screen.getByTestId('menu-item-label-only');
      
      // Should not have a link or button
      expect(within(item).queryByRole('link')).not.toBeInTheDocument();
      expect(within(item).queryByRole('button')).not.toBeInTheDocument();
    });
  });

  describe('Icon Mapping', () => {
    it('should map known icon names to emoji', () => {
      const menusWithIcons: MenuConfig[] = [
        {
          id: 'icons',
          name: 'Icons',
          items: [
            { id: 'home', label: 'Home', path: '/home', icon: 'home' },
            { id: 'users', label: 'Users', path: '/users', icon: 'users' },
            { id: 'settings', label: 'Settings', path: '/settings', icon: 'settings' },
          ],
        },
      ];
      
      renderSidebar({ menus: menusWithIcons });
      
      expect(screen.getByText('🏠')).toBeInTheDocument(); // home
      expect(screen.getByText('👥')).toBeInTheDocument(); // users
      expect(screen.getByText('⚙️')).toBeInTheDocument(); // settings
    });

    it('should use icon name as fallback for unknown icons', () => {
      const menusWithUnknownIcon: MenuConfig[] = [
        {
          id: 'test',
          name: 'Test',
          items: [
            { id: 'custom', label: 'Custom', path: '/custom', icon: 'custom-icon' },
          ],
        },
      ];
      
      renderSidebar({ menus: menusWithUnknownIcon });
      expect(screen.getByText('custom-icon')).toBeInTheDocument();
    });

    it('should handle items without icons', () => {
      const menusWithoutIcons: MenuConfig[] = [
        {
          id: 'test',
          name: 'Test',
          items: [
            { id: 'no-icon', label: 'No Icon', path: '/no-icon' },
          ],
        },
      ];
      
      renderSidebar({ menus: menusWithoutIcons });
      expect(screen.getByText('No Icon')).toBeInTheDocument();
    });
  });

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
      ];
      
      const user = userEvent.setup();
      renderSidebar({ menus: deeplyNestedMenus });
      
      // Expand level 1 - use getByRole with name to be more specific
      const level1Item = screen.getByTestId('menu-item-level1');
      const level1Button = within(level1Item).getByRole('button', { name: /Level 1/ });
      await user.click(level1Button);
      
      // Expand level 2 - use getByRole with name to be more specific
      const level2Item = screen.getByTestId('menu-item-level2');
      const level2Button = within(level2Item).getByRole('button', { name: /Level 2/ });
      await user.click(level2Button);
      
      // Level 3 should be visible
      expect(screen.getByTestId('menu-item-level3')).toBeInTheDocument();
      expect(screen.getByText('Level 3')).toBeInTheDocument();
    });
  });
});
