/**
 * RolesPage Tests
 *
 * Unit tests for the RolesPage component.
 * Tests cover:
 * - Rendering the roles list
 * - Create role action
 * - Edit role action
 * - Delete role with confirmation
 * - Loading and error states
 * - Empty state
 * - Form validation
 * - Accessibility
 *
 * Requirements tested:
 * - 5.1: Display a list of all defined roles
 * - 5.2: Create role action with form
 * - 5.3: Create role via API and update list
 * - 5.4: Edit role with pre-populated form
 * - 5.5: Delete role with confirmation dialog
 */

import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createTestWrapper, setupAuthMocks, wrapFetchMock } from '../../test/testUtils';
import { RolesPage } from './RolesPage';
import type { Role } from './RolesPage';

// Mock roles data
const mockRoles: Role[] = [
  {
    id: '1',
    name: 'admin',
    description: 'Administrator with full access',
    createdAt: '2024-01-15T10:00:00Z',
  },
  {
    id: '2',
    name: 'editor',
    description: 'Can edit content',
    createdAt: '2024-01-10T08:00:00Z',
  },
  {
    id: '3',
    name: 'viewer',
    description: undefined,
    createdAt: '2024-01-05T09:00:00Z',
  },
];

// Mock fetch function with proper Response objects
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

/**
 * Create a wrapper component with all required providers
 */

describe('RolesPage', () => {
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
    it('should display loading spinner while fetching roles', async () => {
      // Mock a delayed response
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () => resolve(createMockResponse(mockRoles)),
              100
            )
          )
      );

      render(<RolesPage />, { wrapper: createTestWrapper() });

      // Look for the loading spinner component
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));

      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/API request failed/i)).toBeInTheDocument();
      });
    });

    it('should display retry button on error', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));

      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });
    });

    it('should retry fetching when retry button is clicked', async () => {
      mockFetch
        .mockResolvedValueOnce(createMockResponse(null, false, 500))
        .mockResolvedValueOnce(createMockResponse(mockRoles));

      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });

      await user.click(screen.getByRole('button', { name: /retry/i }));

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });
    });
  });

  describe('Roles List Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockRoles));
    });

    it('should display all roles in the table', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
        expect(screen.getByText('editor')).toBeInTheDocument();
        expect(screen.getByText('viewer')).toBeInTheDocument();
      });
    });

    it('should display role descriptions', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Administrator with full access')).toBeInTheDocument();
        expect(screen.getByText('Can edit content')).toBeInTheDocument();
      });
    });

    it('should display dash for roles without description', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // viewer role has no description
        const rows = screen.getAllByTestId(/role-row-/);
        const viewerRow = rows.find(row => within(row).queryByText('viewer'));
        expect(viewerRow).toBeInTheDocument();
        expect(within(viewerRow!).getByText('—')).toBeInTheDocument();
      });
    });

    it('should display page title', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /roles/i })).toBeInTheDocument();
      });
    });

    it('should display create role button', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('create-role-button')).toBeInTheDocument();
      });
    });
  });

  describe('Empty State', () => {
    it('should display empty state when no roles exist', async () => {
      mockFetch.mockResolvedValue(createMockResponse([]));

      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      });
    });
  });

  describe('Create Role', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockRoles));
    });

    it('should open create form when clicking create button', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
        // Use getByRole to specifically target the heading in the modal
        expect(screen.getByRole('heading', { name: 'Create Role' })).toBeInTheDocument();
      });
    });

    it('should close form when clicking cancel', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('role-form-cancel'));

      await waitFor(() => {
        expect(screen.queryByTestId('role-form-modal')).not.toBeInTheDocument();
      });
    });

    it('should close form when clicking close button', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('role-form-close'));

      await waitFor(() => {
        expect(screen.queryByTestId('role-form-modal')).not.toBeInTheDocument();
      });
    });

    it('should close form when pressing Escape', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.keyboard('{Escape}');

      await waitFor(() => {
        expect(screen.queryByTestId('role-form-modal')).not.toBeInTheDocument();
      });
    });

    it('should create role when form is submitted with valid data', async () => {
      const user = userEvent.setup();
      const newRole: Role = {
        id: '4',
        name: 'moderator',
        description: 'Can moderate content',
        createdAt: '2024-01-20T10:00:00Z',
      };

      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockRoles)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(newRole)) // Create
        .mockResolvedValueOnce(createMockResponse([...mockRoles, newRole])); // Refetch

      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.type(screen.getByTestId('role-name-input'), 'moderator');
      await user.type(screen.getByTestId('role-description-input'), 'Can moderate content');
      await user.click(screen.getByTestId('role-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/created successfully/i)).toBeInTheDocument();
      });
    });

    it('should show validation error for empty name', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      // Submit without entering name
      await user.click(screen.getByTestId('role-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/role name is required/i)).toBeInTheDocument();
      });
    });

    it('should show validation error for invalid name format', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.type(screen.getByTestId('role-name-input'), 'Invalid Name!');
      await user.click(screen.getByTestId('role-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/must be lowercase/i)).toBeInTheDocument();
      });
    });
  });

  describe('Edit Role', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockRoles));
    });

    it('should open edit form with pre-populated values when clicking edit', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('edit-button-0'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
        expect(screen.getByText('Edit Role')).toBeInTheDocument();
        expect(screen.getByTestId('role-name-input')).toHaveValue('admin');
        expect(screen.getByTestId('role-description-input')).toHaveValue('Administrator with full access');
      });
    });

    it('should update role when form is submitted with valid data', async () => {
      const user = userEvent.setup();
      const updatedRole: Role = {
        ...mockRoles[0],
        description: 'Updated description',
      };

      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockRoles)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(updatedRole)) // Update
        .mockResolvedValueOnce(createMockResponse([updatedRole, ...mockRoles.slice(1)])); // Refetch

      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('edit-button-0'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.clear(screen.getByTestId('role-description-input'));
      await user.type(screen.getByTestId('role-description-input'), 'Updated description');
      await user.click(screen.getByTestId('role-form-submit'));

      await waitFor(() => {
        expect(screen.getByText(/updated successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('Delete Role', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockRoles));
    });

    it('should open delete confirmation dialog when clicking delete', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('delete-button-0'));

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
        expect(screen.getByText(/are you sure you want to delete this role/i)).toBeInTheDocument();
      });
    });

    it('should close delete dialog when clicking cancel', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
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

    it('should delete role when confirming deletion', async () => {
      const user = userEvent.setup();
      
      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockRoles)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(null)) // Delete
        .mockResolvedValueOnce(createMockResponse(mockRoles.slice(1))); // Refetch

      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
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

  describe('Accessibility', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockRoles));
    });

    it('should have accessible table structure', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('grid')).toBeInTheDocument();
      });

      expect(screen.getByRole('grid')).toHaveAttribute('aria-label', 'Roles');
    });

    it('should have accessible action buttons', async () => {
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      const editButton = screen.getByTestId('edit-button-0');
      expect(editButton).toHaveAttribute('aria-label', 'Edit admin');

      const deleteButton = screen.getByTestId('delete-button-0');
      expect(deleteButton).toHaveAttribute('aria-label', 'Delete admin');
    });

    it('should have accessible form modal', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        const modal = screen.getByTestId('role-form-modal');
        expect(modal).toHaveAttribute('role', 'dialog');
        expect(modal).toHaveAttribute('aria-modal', 'true');
        expect(modal).toHaveAttribute('aria-labelledby', 'role-form-title');
      });
    });

    it('should have accessible form inputs', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        const nameInput = screen.getByTestId('role-name-input');
        expect(nameInput).toHaveAttribute('aria-required', 'true');
      });
    });

    it('should show validation errors with proper ARIA attributes', async () => {
      const user = userEvent.setup();
      render(<RolesPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('create-role-button'));

      await waitFor(() => {
        expect(screen.getByTestId('role-form-modal')).toBeInTheDocument();
      });

      await user.click(screen.getByTestId('role-form-submit'));

      await waitFor(() => {
        const nameInput = screen.getByTestId('role-name-input');
        expect(nameInput).toHaveAttribute('aria-invalid', 'true');
        expect(nameInput).toHaveAttribute('aria-describedby', 'role-name-error');
        
        const errorMessage = screen.getByRole('alert');
        expect(errorMessage).toBeInTheDocument();
      });
    });
  });
});
