/**
 * PoliciesPage Tests
 *
 * Unit tests for the PoliciesPage component.
 * Tests cover:
 * - Rendering the policies list
 * - Create policy action
 * - Edit policy action
 * - Delete policy with confirmation
 * - Loading and error states
 * - Empty state
 * - Form validation
 * - Accessibility
 *
 * Requirements tested:
 * - 5.6: Display a list of all authorization policies
 * - 5.7: Create policy action with form (name and expression)
 * - 5.8: Edit and delete policy actions
 */

import React from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, wrapFetchMock } from '../../test/testUtils'
import { PoliciesPage } from './PoliciesPage'
import type { Policy } from './PoliciesPage'

// Mock policies data
const mockPolicies: Policy[] = [
  {
    id: '1',
    name: 'admin_access',
    description: 'Full administrative access',
    expression: 'user.role == "admin"',
    createdAt: '2024-01-15T10:00:00Z',
  },
  {
    id: '2',
    name: 'read_only',
    description: 'Read-only access for viewers',
    expression: 'user.role in ["viewer", "guest"]',
    createdAt: '2024-01-10T08:00:00Z',
  },
  {
    id: '3',
    name: 'owner_access',
    description: undefined,
    expression: 'resource.owner_id == user.id',
    createdAt: '2024-01-05T09:00:00Z',
  },
]

// Mock fetch function with proper Response objects
const mockFetch = vi.fn()

// Helper to create a proper Response-like object
function createMockResponse(data: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
    clone: function () {
      return this
    },
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
  } as Response
}

global.fetch = mockFetch

/**
 * Create a wrapper component with all required providers
 */

describe('PoliciesPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    mockFetch.mockReset()
    wrapFetchMock(mockFetch)
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.clearAllMocks()
  })

  describe('Loading State', () => {
    it('should display loading spinner while fetching policies', async () => {
      // Mock a delayed response
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) => setTimeout(() => resolve(createMockResponse(mockPolicies)), 100))
      )

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      // Look for the loading spinner component
      expect(screen.getByRole('status')).toBeInTheDocument()
    })
  })

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500))

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText(/API request failed/i)).toBeInTheDocument()
      })
    })

    it('should display retry button on error', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500))

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })

    it('should retry fetching when retry button is clicked', async () => {
      mockFetch
        .mockResolvedValueOnce(createMockResponse(null, false, 500))
        .mockResolvedValueOnce(createMockResponse(mockPolicies))

      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: /retry/i }))

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })
    })
  })

  describe('Policies List Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockPolicies))
    })

    it('should display all policies in the table', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
        expect(screen.getByText('read_only')).toBeInTheDocument()
        expect(screen.getByText('owner_access')).toBeInTheDocument()
      })
    })

    it('should display policy descriptions', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Full administrative access')).toBeInTheDocument()
        expect(screen.getByText('Read-only access for viewers')).toBeInTheDocument()
      })
    })

    it('should display policy expressions', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('user.role == "admin"')).toBeInTheDocument()
        expect(screen.getByText('user.role in ["viewer", "guest"]')).toBeInTheDocument()
        expect(screen.getByText('resource.owner_id == user.id')).toBeInTheDocument()
      })
    })

    it('should display dash for policies without description', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        // owner_access policy has no description
        const rows = screen.getAllByTestId(/policy-row-/)
        const ownerRow = rows.find((row) => within(row).queryByText('owner_access'))
        expect(ownerRow).toBeInTheDocument()
        expect(within(ownerRow!).getByText('—')).toBeInTheDocument()
      })
    })

    it('should display page title', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /policies/i })).toBeInTheDocument()
      })
    })

    it('should display create policy button', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('create-policy-button')).toBeInTheDocument()
      })
    })
  })

  describe('Empty State', () => {
    it('should display empty state when no policies exist', async () => {
      mockFetch.mockResolvedValue(createMockResponse([]))

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })
  })

  describe('Create Policy', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockPolicies))
    })

    it('should open create form when clicking create button', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
        // Use getByRole to specifically target the heading in the modal
        expect(screen.getByRole('heading', { name: 'Create Policy' })).toBeInTheDocument()
      })
    })

    it('should close form when clicking cancel', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('policy-form-cancel'))

      await waitFor(() => {
        expect(screen.queryByTestId('policy-form-modal')).not.toBeInTheDocument()
      })
    })

    it('should close form when clicking close button', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('policy-form-close'))

      await waitFor(() => {
        expect(screen.queryByTestId('policy-form-modal')).not.toBeInTheDocument()
      })
    })

    it('should close form when pressing Escape', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.keyboard('{Escape}')

      await waitFor(() => {
        expect(screen.queryByTestId('policy-form-modal')).not.toBeInTheDocument()
      })
    })

    it('should create policy when form is submitted with valid data', async () => {
      const user = userEvent.setup()
      const newPolicy: Policy = {
        id: '4',
        name: 'editor_access',
        description: 'Editor access policy',
        expression: 'user.role == "editor"',
        createdAt: '2024-01-20T10:00:00Z',
      }

      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockPolicies)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(newPolicy)) // Create
        .mockResolvedValueOnce(createMockResponse([...mockPolicies, newPolicy])) // Refetch

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.type(screen.getByTestId('policy-name-input'), 'editor_access')
      await user.type(screen.getByTestId('policy-description-input'), 'Editor access policy')
      await user.type(screen.getByTestId('policy-expression-input'), 'user.role == "editor"')
      await user.click(screen.getByTestId('policy-form-submit'))

      await waitFor(() => {
        expect(screen.getByText(/created successfully/i)).toBeInTheDocument()
      })
    })

    it('should show validation error for empty name', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      // Fill expression but not name
      await user.type(screen.getByTestId('policy-expression-input'), 'user.role == "test"')
      await user.click(screen.getByTestId('policy-form-submit'))

      await waitFor(() => {
        expect(screen.getByText(/policy name is required/i)).toBeInTheDocument()
      })
    })

    it('should show validation error for empty expression', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      // Fill name but not expression
      await user.type(screen.getByTestId('policy-name-input'), 'test_policy')
      await user.click(screen.getByTestId('policy-form-submit'))

      await waitFor(() => {
        expect(screen.getByText(/expression is required/i)).toBeInTheDocument()
      })
    })

    it('should show validation error for invalid name format', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.type(screen.getByTestId('policy-name-input'), 'Invalid Name!')
      await user.type(screen.getByTestId('policy-expression-input'), 'user.role == "test"')
      await user.click(screen.getByTestId('policy-form-submit'))

      await waitFor(() => {
        expect(screen.getByText(/must be lowercase/i)).toBeInTheDocument()
      })
    })
  })

  describe('Edit Policy', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockPolicies))
    })

    it('should open edit form with pre-populated values when clicking edit', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
        expect(screen.getByText('Edit Policy')).toBeInTheDocument()
        expect(screen.getByTestId('policy-name-input')).toHaveValue('admin_access')
        expect(screen.getByTestId('policy-description-input')).toHaveValue(
          'Full administrative access'
        )
        expect(screen.getByTestId('policy-expression-input')).toHaveValue('user.role == "admin"')
      })
    })

    it('should update policy when form is submitted with valid data', async () => {
      const user = userEvent.setup()
      const updatedPolicy: Policy = {
        ...mockPolicies[0],
        description: 'Updated description',
        expression: 'user.role == "super_admin"',
      }

      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockPolicies)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(updatedPolicy)) // Update
        .mockResolvedValueOnce(createMockResponse([updatedPolicy, ...mockPolicies.slice(1)])) // Refetch

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.clear(screen.getByTestId('policy-description-input'))
      await user.type(screen.getByTestId('policy-description-input'), 'Updated description')
      await user.clear(screen.getByTestId('policy-expression-input'))
      await user.type(screen.getByTestId('policy-expression-input'), 'user.role == "super_admin"')
      await user.click(screen.getByTestId('policy-form-submit'))

      await waitFor(() => {
        expect(screen.getByText(/updated successfully/i)).toBeInTheDocument()
      })
    })
  })

  describe('Delete Policy', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockPolicies))
    })

    it('should open delete confirmation dialog when clicking delete', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
        expect(screen.getByText(/are you sure you want to delete this policy/i)).toBeInTheDocument()
      })
    })

    it('should close delete dialog when clicking cancel', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('confirm-dialog-cancel'))

      await waitFor(() => {
        expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
      })
    })

    it('should delete policy when confirming deletion', async () => {
      const user = userEvent.setup()

      mockFetch
        .mockResolvedValueOnce(createMockResponse(mockPolicies)) // Initial fetch
        .mockResolvedValueOnce(createMockResponse(null)) // Delete
        .mockResolvedValueOnce(createMockResponse(mockPolicies.slice(1))) // Refetch

      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('confirm-dialog-confirm'))

      await waitFor(() => {
        expect(screen.getByText(/deleted successfully/i)).toBeInTheDocument()
      })
    })
  })

  describe('Accessibility', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockPolicies))
    })

    it('should have accessible table structure', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('grid')).toBeInTheDocument()
      })

      expect(screen.getByRole('grid')).toHaveAttribute('aria-label', 'Policies')
    })

    it('should have accessible action buttons', async () => {
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      const editButton = screen.getByTestId('edit-button-0')
      expect(editButton).toHaveAttribute('aria-label', 'Edit admin_access')

      const deleteButton = screen.getByTestId('delete-button-0')
      expect(deleteButton).toHaveAttribute('aria-label', 'Delete admin_access')
    })

    it('should have accessible form modal', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        const modal = screen.getByTestId('policy-form-modal')
        expect(modal).toHaveAttribute('role', 'dialog')
        expect(modal).toHaveAttribute('aria-modal', 'true')
        expect(modal).toHaveAttribute('aria-labelledby', 'policy-form-title')
      })
    })

    it('should have accessible form inputs', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        const nameInput = screen.getByTestId('policy-name-input')
        expect(nameInput).toHaveAttribute('aria-required', 'true')

        const expressionInput = screen.getByTestId('policy-expression-input')
        expect(expressionInput).toHaveAttribute('aria-required', 'true')
      })
    })

    it('should show validation errors with proper ARIA attributes', async () => {
      const user = userEvent.setup()
      render(<PoliciesPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('admin_access')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-policy-button'))

      await waitFor(() => {
        expect(screen.getByTestId('policy-form-modal')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('policy-form-submit'))

      await waitFor(() => {
        const nameInput = screen.getByTestId('policy-name-input')
        expect(nameInput).toHaveAttribute('aria-invalid', 'true')
        expect(nameInput).toHaveAttribute('aria-describedby', 'policy-name-error')

        const errorMessages = screen.getAllByRole('alert')
        expect(errorMessages.length).toBeGreaterThan(0)
      })
    })
  })
})
