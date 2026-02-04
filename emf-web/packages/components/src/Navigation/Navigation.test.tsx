import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { Navigation } from './Navigation';
import type { MenuItem } from './types';
import type { User } from '@emf/sdk';

// Helper component to display current location for testing
function LocationDisplay() {
  const location = useLocation();
  return <div data-testid="location-display">{location.pathname}</div>;
}

// Test wrapper with MemoryRouter
const createWrapper = (initialPath = '/') => {
  return ({ children }: { children: React.ReactNode }) => (
    <MemoryRouter initialEntries={[initialPath]}>
      {children}
      <LocationDisplay />
    </MemoryRouter>
  );
};

// Mock menu items
const mockMenuItems: MenuItem[] = [
  { id: 'home', label: 'Home', path: '/' },
  { id: 'users', label: 'Users', path: '/users' },
  { id: 'products', label: 'Products', path: '/products' },
];

const mockMenuItemsWithRoles: MenuItem[] = [
  { id: 'home', label: 'Home', path: '/' },
  { id: 'users', label: 'Users', path: '/users', roles: ['admin'] },
  { id: 'settings', label: 'Settings', path: '/settings', roles: ['admin', 'manager'] },
  { id: 'public', label: 'Public', path: '/public' },
];

const mockNestedMenuItems: MenuItem[] = [
  { id: 'home', label: 'Home', path: '/' },
  {
    id: 'settings',
    label: 'Settings',
    children: [
      { id: 'profile', label: 'Profile', path: '/settings/profile' },
      { id: 'security', label: 'Security', path: '/settings/security' },
    ],
  },
  { id: 'help', label: 'Help', path: '/help' },
];

const mockDeeplyNestedItems: MenuItem[] = [
  {
    id: 'admin',
    label: 'Admin',
    children: [
      {
        id: 'users-admin',
        label: 'Users',
        children: [
          { id: 'create-user', label: 'Create User', path: '/admin/users/create' },
          { id: 'list-users', label: 'List Users', path: '/admin/users/list' },
        ],
      },
      { id: 'settings-admin', label: 'Settings', path: '/admin/settings' },
    ],
  },
];

describe('Navigation', () => {
  describe('Menu Rendering (Requirement 15.1)', () => {
    it('should render all menu items', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByText('Home')).toBeInTheDocument();
      expect(screen.getByText('Users')).toBeInTheDocument();
      expect(screen.getByText('Products')).toBeInTheDocument();
    });

    it('should render correct number of menu items', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper() }
      );

      const menuItems = screen.getAllByRole('menuitem');
      expect(menuItems).toHaveLength(mockMenuItems.length);
    });

    it('should render menu items with icons', () => {
      const itemsWithIcons: MenuItem[] = [
        { id: 'home', label: 'Home', path: '/', icon: <span data-testid="home-icon">🏠</span> },
        { id: 'users', label: 'Users', path: '/users', icon: <span data-testid="users-icon">👥</span> },
      ];

      render(
        <Navigation items={itemsWithIcons} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByTestId('home-icon')).toBeInTheDocument();
      expect(screen.getByTestId('users-icon')).toBeInTheDocument();
    });
  });

  describe('Role-Based Filtering (Requirement 15.2)', () => {
    it('should hide items when user lacks required role', () => {
      const regularUser: User = {
        id: '1',
        username: 'regular',
        roles: ['user'],
      };

      render(
        <Navigation items={mockMenuItemsWithRoles} currentUser={regularUser} />,
        { wrapper: createWrapper() }
      );

      // Home and Public should be visible (no role restriction)
      expect(screen.getByText('Home')).toBeInTheDocument();
      expect(screen.getByText('Public')).toBeInTheDocument();

      // Users and Settings should be hidden (require admin/manager)
      expect(screen.queryByText('Users')).not.toBeInTheDocument();
      expect(screen.queryByText('Settings')).not.toBeInTheDocument();
    });

    it('should show items when user has required role', () => {
      const adminUser: User = {
        id: '1',
        username: 'admin',
        roles: ['admin'],
      };

      render(
        <Navigation items={mockMenuItemsWithRoles} currentUser={adminUser} />,
        { wrapper: createWrapper() }
      );

      // All items should be visible for admin
      expect(screen.getByText('Home')).toBeInTheDocument();
      expect(screen.getByText('Users')).toBeInTheDocument();
      expect(screen.getByText('Settings')).toBeInTheDocument();
      expect(screen.getByText('Public')).toBeInTheDocument();
    });

    it('should show items when user has one of multiple required roles', () => {
      const managerUser: User = {
        id: '1',
        username: 'manager',
        roles: ['manager'],
      };

      render(
        <Navigation items={mockMenuItemsWithRoles} currentUser={managerUser} />,
        { wrapper: createWrapper() }
      );

      // Settings requires admin OR manager, so should be visible
      expect(screen.getByText('Settings')).toBeInTheDocument();
      // Users requires only admin, so should be hidden
      expect(screen.queryByText('Users')).not.toBeInTheDocument();
    });

    it('should hide all restricted items when no user is logged in', () => {
      render(
        <Navigation items={mockMenuItemsWithRoles} currentUser={null} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByText('Home')).toBeInTheDocument();
      expect(screen.getByText('Public')).toBeInTheDocument();
      expect(screen.queryByText('Users')).not.toBeInTheDocument();
      expect(screen.queryByText('Settings')).not.toBeInTheDocument();
    });
  });

  describe('Navigation on Click (Requirement 15.3)', () => {
    it('should navigate to path when menu item is clicked', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper('/') }
      );

      await user.click(screen.getByText('Users'));

      expect(screen.getByTestId('location-display')).toHaveTextContent('/users');
    });

    it('should call onClick handler when provided', async () => {
      const user = userEvent.setup();
      const onClick = vi.fn();
      const itemsWithHandler: MenuItem[] = [
        { id: 'action', label: 'Action', onClick },
      ];

      render(
        <Navigation items={itemsWithHandler} />,
        { wrapper: createWrapper() }
      );

      await user.click(screen.getByText('Action'));

      expect(onClick).toHaveBeenCalled();
    });

    it('should not navigate when onClick is provided', async () => {
      const user = userEvent.setup();
      const onClick = vi.fn();
      const itemsWithHandler: MenuItem[] = [
        { id: 'action', label: 'Action', path: '/action', onClick },
      ];

      render(
        <Navigation items={itemsWithHandler} />,
        { wrapper: createWrapper('/') }
      );

      await user.click(screen.getByText('Action'));

      // Should call onClick instead of navigating
      expect(onClick).toHaveBeenCalled();
      expect(screen.getByTestId('location-display')).toHaveTextContent('/');
    });
  });

  describe('Active State (Requirement 15.4)', () => {
    it('should highlight active menu item based on current route', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper('/users') }
      );

      const usersItem = screen.getByText('Users').closest('li');
      expect(usersItem).toHaveClass('emf-navigation__item--active');

      const homeItem = screen.getByText('Home').closest('li');
      expect(homeItem).not.toHaveClass('emf-navigation__item--active');
    });

    it('should set aria-current on active item', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper('/users') }
      );

      const usersButton = screen.getByText('Users').closest('button');
      expect(usersButton).toHaveAttribute('aria-current', 'page');
    });

    it('should highlight parent when child route is active', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper('/settings/profile') }
      );

      // Expand the settings menu first
      await user.click(screen.getByText('Settings'));

      const settingsItem = screen.getByText('Settings').closest('li');
      expect(settingsItem).toHaveClass('emf-navigation__item--active');
    });

    it('should handle exact match for root path', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper('/') }
      );

      const homeItem = screen.getByText('Home').closest('li');
      expect(homeItem).toHaveClass('emf-navigation__item--active');

      // Users should not be active
      const usersItem = screen.getByText('Users').closest('li');
      expect(usersItem).not.toHaveClass('emf-navigation__item--active');
    });
  });

  describe('Nested Menus (Requirement 15.5, 15.6, 15.7)', () => {
    it('should render submenu items when parent has children', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      // Initially, submenu items should not be visible
      expect(screen.queryByText('Profile')).not.toBeInTheDocument();
      expect(screen.queryByText('Security')).not.toBeInTheDocument();

      // Click to expand
      await user.click(screen.getByText('Settings'));

      // Now submenu items should be visible
      expect(screen.getByText('Profile')).toBeInTheDocument();
      expect(screen.getByText('Security')).toBeInTheDocument();
    });

    it('should expand submenu when parent is clicked', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      const settingsButton = screen.getByText('Settings').closest('button');
      expect(settingsButton).toHaveAttribute('aria-expanded', 'false');

      await user.click(screen.getByText('Settings'));

      expect(settingsButton).toHaveAttribute('aria-expanded', 'true');
    });

    it('should collapse submenu when parent is clicked again', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      // Expand
      await user.click(screen.getByText('Settings'));
      expect(screen.getByText('Profile')).toBeInTheDocument();

      // Collapse
      await user.click(screen.getByText('Settings'));
      expect(screen.queryByText('Profile')).not.toBeInTheDocument();
    });

    it('should show expand/collapse arrow for items with children', () => {
      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      const settingsButton = screen.getByText('Settings').closest('button');
      expect(settingsButton).toHaveTextContent('▶');
    });

    it('should change arrow direction when expanded', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      await user.click(screen.getByText('Settings'));

      const settingsButton = screen.getByText('Settings').closest('button');
      expect(settingsButton).toHaveTextContent('▼');
    });

    it('should navigate to child item path when clicked', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      // Expand settings
      await user.click(screen.getByText('Settings'));

      // Click on Profile
      await user.click(screen.getByText('Profile'));

      expect(screen.getByTestId('location-display')).toHaveTextContent('/settings/profile');
    });

    it('should support deeply nested menus', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockDeeplyNestedItems} />,
        { wrapper: createWrapper() }
      );

      // Expand Admin
      await user.click(screen.getByText('Admin'));
      expect(screen.getByText('Users')).toBeInTheDocument();

      // Expand Users
      await user.click(screen.getByText('Users'));
      expect(screen.getByText('Create User')).toBeInTheDocument();
      expect(screen.getByText('List Users')).toBeInTheDocument();
    });
  });

  describe('Keyboard Navigation', () => {
    it('should navigate with arrow keys', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper() }
      );

      // Focus on first item
      const homeButton = screen.getByText('Home').closest('button')!;
      homeButton.focus();

      // Press down arrow
      await user.keyboard('{ArrowDown}');

      // Users should now be focused
      const usersButton = screen.getByText('Users').closest('button')!;
      expect(usersButton).toHaveClass('emf-navigation__link--focused');
    });

    it('should activate item with Enter key', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper('/') }
      );

      const usersButton = screen.getByText('Users').closest('button')!;
      usersButton.focus();

      await user.keyboard('{Enter}');

      expect(screen.getByTestId('location-display')).toHaveTextContent('/users');
    });

    it('should activate item with Space key', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper('/') }
      );

      const usersButton = screen.getByText('Users').closest('button')!;
      usersButton.focus();

      await user.keyboard(' ');

      expect(screen.getByTestId('location-display')).toHaveTextContent('/users');
    });

    it('should expand/collapse submenu with Enter key', async () => {
      const user = userEvent.setup();

      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      const settingsButton = screen.getByText('Settings').closest('button')!;
      settingsButton.focus();

      await user.keyboard('{Enter}');

      expect(screen.getByText('Profile')).toBeInTheDocument();
    });
  });

  describe('Orientation', () => {
    it('should apply horizontal orientation class by default', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByTestId('emf-navigation')).toHaveClass('emf-navigation--horizontal');
    });

    it('should apply vertical orientation class when specified', () => {
      render(
        <Navigation items={mockMenuItems} orientation="vertical" />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByTestId('emf-navigation')).toHaveClass('emf-navigation--vertical');
    });

    it('should set aria-orientation on menubar', () => {
      render(
        <Navigation items={mockMenuItems} orientation="vertical" />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByRole('menubar')).toHaveAttribute('aria-orientation', 'vertical');
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA attributes', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByRole('navigation')).toHaveAttribute('aria-label', 'Main navigation');
      expect(screen.getByRole('menubar')).toBeInTheDocument();
    });

    it('should have menuitem role for each item', () => {
      render(
        <Navigation items={mockMenuItems} />,
        { wrapper: createWrapper() }
      );

      const menuItems = screen.getAllByRole('menuitem');
      expect(menuItems).toHaveLength(mockMenuItems.length);
    });

    it('should have aria-haspopup for items with children', () => {
      render(
        <Navigation items={mockNestedMenuItems} />,
        { wrapper: createWrapper() }
      );

      const settingsButton = screen.getByText('Settings').closest('button');
      expect(settingsButton).toHaveAttribute('aria-haspopup', 'true');
    });

    it('should apply custom className', () => {
      render(
        <Navigation items={mockMenuItems} className="custom-nav" />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByTestId('emf-navigation')).toHaveClass('custom-nav');
    });

    it('should use custom testId', () => {
      render(
        <Navigation items={mockMenuItems} testId="my-nav" />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByTestId('my-nav')).toBeInTheDocument();
    });

    it('should apply collapsible class when enabled', () => {
      render(
        <Navigation items={mockMenuItems} collapsible />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByTestId('emf-navigation')).toHaveClass('emf-navigation--collapsible');
    });
  });
});
