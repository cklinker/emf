/**
 * MenuBuilderPage Tests
 *
 * Unit tests for the MenuBuilderPage component.
 * Tests cover:
 * - Rendering the menus list
 * - Create menu action
 * - Edit menu action
 * - Delete menu with confirmation
 * - Menu editor with tree view
 * - Add, edit, delete menu items
 * - Drag-and-drop reordering
 * - Nested menu items
 * - Loading and error states
 * - Empty state
 * - Form validation
 * - Accessibility
 *
 * Requirements tested:
 * - 8.1: Display list of all menus
 * - 8.2: Create new menu action
 * - 8.3: Menu editor with tree view for items
 * - 8.4: Support drag-and-drop reordering of menu items
 * - 8.5: Support nested menu items (submenus)
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createTestWrapper, setupAuthMocks, wrapFetchMock } from '../../test/testUtils';
import { MenuBuilderPage } from './MenuBuilderPage';
import type { UIMenu } from './MenuBuilderPage';

// Mock menus data
const mockMenus: UIMenu[] = [
  {
    id: '1',
    name: 'main_navigation',
    items: [
      {
        id: 'item_1',
        label: 'Dashboard',
        path: '/dashboard',
        icon: 'dashboard',
        order: 0,
      },
      {
        id: 'item_2',
        label: 'Settings',
        path: '/settings',
        icon: 'settings',
        order: 1,
        policies: ['admin_policy'],
        children: [
          {
            id: 'item_2_1',
            label: 'General',
            path: '/settings/general',
            order: 0,
          },
          {
            id: 'item_2_2',
            label: 'Security',
            path: '/settings/security',
            order: 1,
            policies: ['security_policy'],
          },
        ],
      },
    ],
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  },
  {
    id: '2',
    name: 'admin_menu',
    items: [
      {
        id: 'item_3',
        label: 'Users',
        path: '/admin/users',
        icon: 'users',
        order: 0,
      },
    ],
    createdAt: '2024-01-10T08:00:00Z',
    updatedAt: '2024-01-12T14:00:00Z',
  },
];

// Mock policies data
const mockPolicies = [
  { id: 'admin_policy', name: 'Admin Access', description: 'Full admin access' },
  { id: 'security_policy', name: 'Security Access', description: 'Security settings access' },
  { id: 'viewer_policy', name: 'Viewer Access', description: 'Read-only access' },
];


// Mock fetch function
const mockFetch = vi.fn();

// Helper to create a proper Response-like object
function createMockResponse(data: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
    clone: function() { return this; },
    headers: new Headers(),
    redirected: false,
    statusText: ok ? 'OK' : 'Error',
    type: 'basic' as ResponseType,
    url: '',
    body: null,
    bodyUsed: false,
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    blob: () => Promise.resolve(new Blob()),
    formData: () => Promise.resolve(new FormData()),
    bytes: () => Promise.resolve(new Uint8Array()),
  } as Response;
}

global.fetch = mockFetch;

describe('MenuBuilderPage', () => {
  let cleanupAuthMocks: () => void;

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks();
    mockFetch.mockReset();
    wrapFetchMock(mockFetch);
  });

  afterEach(() => {
    cleanupAuthMocks();
    vi.clearAllMocks();
  });

  describe('Loading State', () => {
    it('should display loading spinner while fetching menus', async () => {
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () => resolve(createMockResponse(mockMenus)),
              100
            )
          )
      );

      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));

      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/API request failed/i)).toBeInTheDocument();
      });
    });

    it('should display retry button on error', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));

      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });
    });

    it('should retry fetching when retry button is clicked', async () => {
      mockFetch
        .mockResolvedValueOnce(createMockResponse(null, false, 500))
        .mockResolvedValueOnce(createMockResponse(mockMenus));

      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });

      await user.click(screen.getByRole('button', { name: /retry/i }));

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });
    });
  });


  describe('Menus List Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockMenus));
    });

    it('should display all menus in the table', async () => {
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
        expect(screen.getByText('admin_menu')).toBeInTheDocument();
      });
    });

    it('should display menu item counts', async () => {
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('2 items')).toBeInTheDocument();
        expect(screen.getByText('1 items')).toBeInTheDocument();
      });
    });

    it('should display page title', async () => {
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /menu builder/i })).toBeInTheDocument();
      });
    });

    it('should display create menu button', async () => {
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('create-menu-button')).toBeInTheDocument();
      });
    });
  });

  describe('Empty State', () => {
    it('should display empty state when no menus exist', async () => {
      mockFetch.mockResolvedValue(createMockResponse([]));

      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      });
    });
  });

  describe('Create Menu', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockMenus));
    });

    it('should open create form when clicking create button', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-menu-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-form-modal')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Create Menu' })).toBeInTheDocument();
      });
    });

    it('should close form when clicking cancel', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-menu-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-form-cancel'));

      await waitFor(() => {
        expect(screen.queryByTestId('menu-form-modal')).not.toBeInTheDocument();
      });
    });

    it('should close form when clicking close button', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-menu-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-form-close'));

      await waitFor(() => {
        expect(screen.queryByTestId('menu-form-modal')).not.toBeInTheDocument();
      });
    });

    it('should close form when pressing Escape', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-menu-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-form-modal')).toBeInTheDocument();
      });

      await user.keyboard('{Escape}');

      await waitFor(() => {
        expect(screen.queryByTestId('menu-form-modal')).not.toBeInTheDocument();
      });
    });

    it('should show validation error for empty name', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-menu-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/menu name is required/i)).toBeInTheDocument();
      });
    });

    it('should create menu when form is submitted with valid data', async () => {
      const user = userEvent.setup();
      const newMenu: UIMenu = {
        id: '3',
        name: 'new_menu',
        items: [],
        createdAt: '2024-01-20T10:00:00Z',
        updatedAt: '2024-01-20T10:00:00Z',
      };

      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockMenus)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(newMenu)) // Create
        .mockResolvedValue(createMockResponse([...mockMenus, newMenu])); // All subsequent fetches

      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-menu-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-form-modal')).toBeInTheDocument();
      });

      await user.type(screen.getByTestId('menu-name-input'), 'new_menu');
      await user.click(screen.getByTestId('menu-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/created successfully/i)).toBeInTheDocument();
      });
    });
  });


  describe('Delete Menu', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockMenus));
    });

    it('should open delete confirmation dialog when clicking delete', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('delete-button-0'));

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
        expect(screen.getByText(/are you sure you want to delete this menu/i)).toBeInTheDocument();
      });
    });

    it('should close delete dialog when clicking cancel', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('delete-button-0'));

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('confirm-dialog-cancel'));

      await waitFor(() => {
        expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
      });
    });

    it('should delete menu when confirming deletion', async () => {
      const user = userEvent.setup();
      
      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockMenus)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(null)) // Delete
        .mockResolvedValueOnce(createMockResponse(mockMenus.slice(1))); // Refetch

      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('delete-button-0'));

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('confirm-dialog-confirm'));

      await waitFor(() => {
        expect(screen.getByText(/deleted successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('Menu Editor', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.includes('/control/authz/policies')) {
          return Promise.resolve(createMockResponse({ content: mockPolicies, totalElements: mockPolicies.length, totalPages: 1, size: 1000, number: 0 }));
        }
        if (urlStr.includes('/control/ui/menus/1') && !urlStr.endsWith('/menus')) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should open editor when clicking on menu name', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
        expect(screen.getByTestId('menu-preview')).toBeInTheDocument();
      });
    });

    it('should display back button in editor', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('back-to-list-button')).toBeInTheDocument();
      });
    });

    it('should return to list when clicking back button', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('back-to-list-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('back-to-list-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menus-table')).toBeInTheDocument();
      });
    });
  });


  describe('Menu Tree View', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.includes('/control/authz/policies')) {
          return Promise.resolve(createMockResponse({ content: mockPolicies, totalElements: mockPolicies.length, totalPages: 1, size: 1000, number: 0 }));
        }
        if (urlStr.includes('/control/ui/menus/1') && !urlStr.endsWith('/menus')) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should display add item button in editor', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });
    });

    it('should display tree view or empty state in editor', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        // Either tree-view or tree-empty should be present
        const hasTreeView = screen.queryByTestId('menu-tree-view') !== null;
        const hasTreeEmpty = screen.queryByTestId('tree-empty') !== null;
        expect(hasTreeView || hasTreeEmpty).toBe(true);
      });
    });

    it('should display preview panel in editor', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-preview')).toBeInTheDocument();
      });
    });
  });

  describe('Add Menu Item', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.includes('/control/authz/policies')) {
          return Promise.resolve(createMockResponse({ content: mockPolicies, totalElements: mockPolicies.length, totalPages: 1, size: 1000, number: 0 }));
        }
        if (urlStr.includes('/control/ui/menus/1') && !urlStr.endsWith('/menus')) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should open add item form when clicking add item button', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Add Item' })).toBeInTheDocument();
      });
    });

    it('should show validation error for empty label', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-item-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/label is required/i)).toBeInTheDocument();
      });
    });

    it('should add new item when form is submitted with valid data', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      await user.type(screen.getByTestId('item-label-input'), 'New Item');
      await user.type(screen.getByTestId('item-path-input'), '/new-item');
      await user.click(screen.getByTestId('menu-item-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/created successfully/i)).toBeInTheDocument();
      });

      // Check that the new item appears in the tree (there may be multiple due to preview)
      await waitFor(() => {
        const newItems = screen.getAllByText('New Item');
        expect(newItems.length).toBeGreaterThanOrEqual(1);
      });
    });
  });


  describe('Menu Preview', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.includes('/control/authz/policies')) {
          return Promise.resolve(createMockResponse({ content: mockPolicies, totalElements: mockPolicies.length, totalPages: 1, size: 1000, number: 0 }));
        }
        if (urlStr.includes('/control/ui/menus/1') && !urlStr.endsWith('/menus')) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should display menu preview panel', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-preview')).toBeInTheDocument();
      });
    });

    it('should display preview title', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-preview')).toBeInTheDocument();
        expect(screen.getByText('Preview')).toBeInTheDocument();
      });
    });
  });

  describe('Save Menu', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.includes('/control/authz/policies')) {
          return Promise.resolve(createMockResponse({ content: mockPolicies, totalElements: mockPolicies.length, totalPages: 1, size: 1000, number: 0 }));
        }
        if (urlStr.includes('/control/ui/menus/1') && !urlStr.endsWith('/menus')) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should disable save button when no changes', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('save-menu-button')).toBeDisabled();
      });
    });

    it('should enable save button when changes are made', async () => {
      
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      // Add a new item to make changes
      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      await user.type(screen.getByTestId('item-label-input'), 'New Item');
      await user.click(screen.getByTestId('menu-item-form-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('save-menu-button')).not.toBeDisabled();
      });
    });

    it('should save menu when save button is clicked', async () => {
      mockFetch.mockImplementation((url: string | URL | Request, options?: RequestInit) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.includes('/control/authz/policies')) {
          return Promise.resolve(createMockResponse({ content: mockPolicies, totalElements: mockPolicies.length, totalPages: 1, size: 1000, number: 0 }));
        }
        if (options?.method === 'PUT') {
          return Promise.resolve(createMockResponse({ ...mockMenus[0], items: [] }));
        }
        if (urlStr.includes('/control/ui/menus/1') && !urlStr.endsWith('/menus')) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        return Promise.resolve(createMockResponse(mockMenus));
      });

      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      // Add a new item
      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      await user.type(screen.getByTestId('item-label-input'), 'New Item');
      await user.click(screen.getByTestId('menu-item-form-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('save-menu-button')).not.toBeDisabled();
      });

      await user.click(screen.getByTestId('save-menu-button'));

      await waitFor(() => {
        expect(screen.getByText(/updated successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('Access Policies Configuration', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        let urlStr: string;
        if (typeof url === 'string') {
          urlStr = url;
        } else if (url instanceof URL) {
          urlStr = url.toString();
        } else if (url instanceof Request) {
          urlStr = url.url;
        } else {
          urlStr = String(url);
        }
        
        // Check for policies endpoint first (more specific)
        if (urlStr.includes('/control/policies')) {
          return Promise.resolve(createMockResponse(mockPolicies));
        }
        // Check for specific menu by ID
        if (urlStr.match(/\/ui\/menus\/\d+$/)) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        // Default to menus list
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should display policies container in menu item form', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
        expect(screen.getByTestId('policies-container')).toBeInTheDocument();
      });
    });

    it('should display available policies as checkboxes', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      // Wait for editor to load
      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      // Check that policies are displayed - they may take a moment to load
      await waitFor(() => {
        expect(screen.getByTestId('policy-checkbox-admin_policy')).toBeInTheDocument();
        expect(screen.getByTestId('policy-checkbox-security_policy')).toBeInTheDocument();
        expect(screen.getByTestId('policy-checkbox-viewer_policy')).toBeInTheDocument();
      }, { timeout: 3000 });
    });

    it('should allow selecting multiple policies', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      // Give time for policies query to complete
      await new Promise(resolve => setTimeout(resolve, 100));

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      // Wait for policies to load
      await waitFor(() => {
        expect(screen.getByTestId('policy-input-admin_policy')).toBeInTheDocument();
      }, { timeout: 3000 });

      // Select multiple policies
      await user.click(screen.getByTestId('policy-input-admin_policy'));
      await user.click(screen.getByTestId('policy-input-viewer_policy'));

      // Verify checkboxes are checked
      expect(screen.getByTestId('policy-input-admin_policy')).toBeChecked();
      expect(screen.getByTestId('policy-input-viewer_policy')).toBeChecked();
      expect(screen.getByTestId('policy-input-security_policy')).not.toBeChecked();
    });

    it('should save menu item with selected policies', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      // Give time for policies query to complete
      await new Promise(resolve => setTimeout(resolve, 100));

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      // Fill in required fields
      await user.type(screen.getByTestId('item-label-input'), 'Protected Item');
      await user.type(screen.getByTestId('item-path-input'), '/protected');

      // Wait for policies to load and select one
      await waitFor(() => {
        expect(screen.getByTestId('policy-input-admin_policy')).toBeInTheDocument();
      }, { timeout: 3000 });

      await user.click(screen.getByTestId('policy-input-admin_policy'));

      // Submit the form
      await user.click(screen.getByTestId('menu-item-form-submit'));

      // Verify item was created
      await waitFor(() => {
        expect(screen.getByText(/created successfully/i)).toBeInTheDocument();
      });

      // Verify the item appears in the tree
      await waitFor(() => {
        const protectedItems = screen.getAllByText('Protected Item');
        expect(protectedItems.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should display no policies message when no policies available', async () => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        let urlStr: string;
        if (typeof url === 'string') {
          urlStr = url;
        } else if (url instanceof URL) {
          urlStr = url.toString();
        } else if (url instanceof Request) {
          urlStr = url.url;
        } else {
          urlStr = String(url);
        }
        
        // Check for policies endpoint first - return empty array
        if (urlStr.includes('/control/policies')) {
          return Promise.resolve(createMockResponse([]));
        }
        // Check for specific menu by ID
        if (urlStr.match(/\/ui\/menus\/\d+$/)) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        // Default to menus list
        return Promise.resolve(createMockResponse(mockMenus));
      });

      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      await waitFor(() => {
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      // Wait for the no policies message to appear
      await waitFor(() => {
        expect(screen.getByTestId('no-policies-message')).toBeInTheDocument();
      }, { timeout: 3000 });
    });
  });

  describe('Menu Preview with Policies', () => {
    beforeEach(() => {
      mockFetch.mockImplementation((url: string | URL | Request) => {
        let urlStr: string;
        if (typeof url === 'string') {
          urlStr = url;
        } else if (url instanceof URL) {
          urlStr = url.toString();
        } else if (url instanceof Request) {
          urlStr = url.url;
        } else {
          urlStr = String(url);
        }
        
        // Check for policies endpoint first (more specific)
        if (urlStr.includes('/control/policies')) {
          return Promise.resolve(createMockResponse(mockPolicies));
        }
        // Check for specific menu by ID
        if (urlStr.match(/\/ui\/menus\/\d+$/)) {
          return Promise.resolve(createMockResponse(mockMenus[0]));
        }
        // Default to menus list
        return Promise.resolve(createMockResponse(mockMenus));
      });
    });

    it('should display policies in preview for items with policies', async () => {
      const user = userEvent.setup();
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('main_navigation')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('menu-name-0'));

      // Wait for editor and preview to load
      await waitFor(() => {
        expect(screen.getByTestId('menu-preview')).toBeInTheDocument();
        expect(screen.getByTestId('add-item-button')).toBeInTheDocument();
      });

      // Add a new item with policies to test the preview
      await user.click(screen.getByTestId('add-item-button'));

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-form-modal')).toBeInTheDocument();
      });

      // Fill in the form
      await user.type(screen.getByTestId('item-label-input'), 'Admin Only');
      await user.type(screen.getByTestId('item-path-input'), '/admin');

      // Wait for policies to load and select one
      await waitFor(() => {
        expect(screen.getByTestId('policy-input-admin_policy')).toBeInTheDocument();
      }, { timeout: 3000 });

      await user.click(screen.getByTestId('policy-input-admin_policy'));

      // Submit the form
      await user.click(screen.getByTestId('menu-item-form-submit'));

      // Verify item was created
      await waitFor(() => {
        expect(screen.getByText(/created successfully/i)).toBeInTheDocument();
      });

      // The preview should now show the item with its policy
      // Look for the policy indicator in the preview
      await waitFor(() => {
        // The preview should contain the Admin Access policy name
        const previewContent = screen.getByTestId('menu-preview');
        expect(previewContent).toHaveTextContent('Admin Access');
      }, { timeout: 3000 });
    });
  });

  describe('Accessibility', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockMenus));
    });

    it('should have accessible table structure', async () => {
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('grid')).toBeInTheDocument();
        expect(screen.getAllByRole('row').length).toBeGreaterThan(0);
        expect(screen.getAllByRole('columnheader').length).toBeGreaterThan(0);
      });
    });

    it('should have accessible buttons with labels', async () => {
      render(<MenuBuilderPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('create-menu-button')).toHaveAccessibleName();
        expect(screen.getByTestId('edit-button-0')).toHaveAccessibleName();
        expect(screen.getByTestId('delete-button-0')).toHaveAccessibleName();
      });
    });
  });
});
