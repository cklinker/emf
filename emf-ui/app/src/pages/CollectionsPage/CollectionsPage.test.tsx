/**
 * CollectionsPage Tests
 *
 * Unit tests for the CollectionsPage component.
 * Tests cover:
 * - Rendering the collections list
 * - Filtering by name and status
 * - Sorting by name, created date, modified date
 * - Create, edit, delete actions
 * - Pagination
 * - Loading and error states
 * - Empty state
 *
 * Requirements tested:
 * - 3.1: Display a paginated list of all collections
 * - 3.2: Support filtering collections by name and status
 * - 3.3: Support sorting collections by name, creation date, and modification date
 * - 3.4: Create collection action
 * - 3.10: Display confirmation dialog before deletion
 * - 3.11: Soft-delete collection and remove from list
 */

import React from 'react'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createTestWrapper,
  setupAuthMocks,
  mockAxios,
  resetMockAxios,
  createAxiosError,
} from '../../test/testUtils'
import { CollectionsPage, Collection } from './CollectionsPage'

/**
 * Convert flat Collection objects to a JSON:API list response shape.
 * This matches the format returned by the DynamicCollectionRouter.
 */
function toJsonApiResponse(collections: Collection[]) {
  return {
    data: {
      data: collections.map((c) => ({
        type: 'collections',
        id: c.id,
        attributes: {
          name: c.name,
          displayName: c.displayName,
          description: c.description,
          storageMode: c.storageMode,
          active: c.active,
          systemCollection: c.systemCollection ?? false,
          currentVersion: c.currentVersion,
          createdAt: c.createdAt,
          updatedAt: c.updatedAt,
        },
      })),
      metadata: {
        totalCount: collections.length,
        totalPages: Math.ceil(collections.length / 1000),
        pageSize: 1000,
        currentPage: 1,
      },
    },
  }
}

// Mock navigate function
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Mock collections data
const mockCollections: Collection[] = [
  {
    id: '1',
    name: 'users',
    displayName: 'Users',
    description: 'User accounts',
    storageMode: 'PHYSICAL_TABLE',
    active: true,
    currentVersion: 1,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-20T15:30:00Z',
  },
  {
    id: '2',
    name: 'products',
    displayName: 'Products',
    description: 'Product catalog',
    storageMode: 'JSONB',
    active: true,
    currentVersion: 2,
    createdAt: '2024-01-10T08:00:00Z',
    updatedAt: '2024-01-18T12:00:00Z',
  },
  {
    id: '3',
    name: 'orders',
    displayName: 'Orders',
    description: 'Customer orders',
    storageMode: 'PHYSICAL_TABLE',
    active: false,
    currentVersion: 1,
    createdAt: '2024-01-05T09:00:00Z',
    updatedAt: '2024-01-12T11:00:00Z',
  },
]

/**
 * Create a wrapper component with all required providers
 */

describe('CollectionsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    mockNavigate.mockReset()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.clearAllMocks()
  })

  describe('Loading State', () => {
    it('should display loading spinner while fetching collections', async () => {
      // Mock a delayed response
      mockAxios.get.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(() => resolve(toJsonApiResponse(mockCollections)), 100)
          )
      )

      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      // Look for the loading spinner component specifically
      expect(screen.getByRole('status')).toBeInTheDocument()
    })
  })

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockAxios.get.mockRejectedValue(createAxiosError(500))

      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument()
      })
    })

    it('should display retry button on error', async () => {
      mockAxios.get.mockRejectedValue(createAxiosError(500))

      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })
  })

  describe('Collections List Display', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue(toJsonApiResponse(mockCollections))
    })

    it('should display all collections in the table', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
        expect(screen.getByText('products')).toBeInTheDocument()
        expect(screen.getByText('orders')).toBeInTheDocument()
      })
    })

    it('should display collection display names', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
        expect(screen.getByText('Products')).toBeInTheDocument()
        expect(screen.getByText('Orders')).toBeInTheDocument()
      })
    })

    it('should display status badges for each collection', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        // Get status badges by test ID pattern
        const statusBadges = screen.getAllByTestId(/status-badge-/)
        expect(statusBadges.length).toBe(3) // 3 collections = 3 badges

        // Check that we have the right mix of active/inactive
        const activeCount = statusBadges.filter((badge) => badge.textContent === 'Active').length
        const inactiveCount = statusBadges.filter(
          (badge) => badge.textContent === 'Inactive'
        ).length
        expect(activeCount).toBe(2)
        expect(inactiveCount).toBe(1)
      })
    })

    it('should display page title', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /collections/i })).toBeInTheDocument()
      })
    })
  })

  describe('Filtering', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue(toJsonApiResponse(mockCollections))
    })

    it('should filter collections by name', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameFilter = screen.getByTestId('name-filter')
      await user.type(nameFilter, 'user')

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
        expect(screen.queryByText('products')).not.toBeInTheDocument()
        expect(screen.queryByText('orders')).not.toBeInTheDocument()
      })
    })

    it('should filter collections by display name', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameFilter = screen.getByTestId('name-filter')
      await user.type(nameFilter, 'Product')

      await waitFor(() => {
        expect(screen.queryByText('users')).not.toBeInTheDocument()
        expect(screen.getByText('products')).toBeInTheDocument()
        expect(screen.queryByText('orders')).not.toBeInTheDocument()
      })
    })

    it('should filter collections by active status', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const statusFilter = screen.getByTestId('status-filter')
      await user.selectOptions(statusFilter, 'active')

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
        expect(screen.getByText('products')).toBeInTheDocument()
        expect(screen.queryByText('orders')).not.toBeInTheDocument()
      })
    })

    it('should filter collections by inactive status', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const statusFilter = screen.getByTestId('status-filter')
      await user.selectOptions(statusFilter, 'inactive')

      await waitFor(() => {
        expect(screen.queryByText('users')).not.toBeInTheDocument()
        expect(screen.queryByText('products')).not.toBeInTheDocument()
        expect(screen.getByText('orders')).toBeInTheDocument()
      })
    })

    it('should show empty state when no collections match filter', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameFilter = screen.getByTestId('name-filter')
      await user.type(nameFilter, 'nonexistent')

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })
  })

  describe('Sorting', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue(toJsonApiResponse(mockCollections))
    })

    it('should sort collections by name ascending by default', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        const rows = screen.getAllByTestId(/collection-row-/)
        expect(rows.length).toBe(3)
      })

      // Check order: orders, products, users (alphabetical)
      const rows = screen.getAllByTestId(/collection-row-/)
      expect(within(rows[0]).getByText('orders')).toBeInTheDocument()
      expect(within(rows[1]).getByText('products')).toBeInTheDocument()
      expect(within(rows[2]).getByText('users')).toBeInTheDocument()
    })

    it('should toggle sort direction when clicking same column header', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameHeader = screen.getByTestId('header-name')
      await user.click(nameHeader)

      // After clicking, should be descending
      await waitFor(() => {
        const rows = screen.getAllByTestId(/collection-row-/)
        expect(within(rows[0]).getByText('users')).toBeInTheDocument()
        expect(within(rows[1]).getByText('products')).toBeInTheDocument()
        expect(within(rows[2]).getByText('orders')).toBeInTheDocument()
      })
    })

    it('should sort by created date when clicking created header', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const createdHeader = screen.getByTestId('header-created')
      await user.click(createdHeader)

      // Should sort by createdAt ascending
      await waitFor(() => {
        const rows = screen.getAllByTestId(/collection-row-/)
        // orders (Jan 5), products (Jan 10), users (Jan 15)
        expect(within(rows[0]).getByText('orders')).toBeInTheDocument()
        expect(within(rows[1]).getByText('products')).toBeInTheDocument()
        expect(within(rows[2]).getByText('users')).toBeInTheDocument()
      })
    })

    it('should sort by updated date when clicking updated header', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const updatedHeader = screen.getByTestId('header-updated')
      await user.click(updatedHeader)

      // Should sort by updatedAt ascending
      await waitFor(() => {
        const rows = screen.getAllByTestId(/collection-row-/)
        // orders (Jan 12), products (Jan 18), users (Jan 20)
        expect(within(rows[0]).getByText('orders')).toBeInTheDocument()
        expect(within(rows[1]).getByText('products')).toBeInTheDocument()
        expect(within(rows[2]).getByText('users')).toBeInTheDocument()
      })
    })

    it('should display sort indicator on sorted column', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameHeader = screen.getByTestId('header-name')
      expect(nameHeader).toHaveAttribute('aria-sort', 'ascending')
    })
  })

  describe('Actions', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue(toJsonApiResponse(mockCollections))
    })

    it('should navigate to create page when clicking create button', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const createButton = screen.getByTestId('create-collection-button')
      await user.click(createButton)

      expect(mockNavigate).toHaveBeenCalledWith('/default/collections/new')
    })

    it('should navigate to edit page when clicking edit button', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      // Find the edit button for the first row (orders after sorting)
      const editButton = screen.getByTestId('edit-button-0')
      await user.click(editButton)

      expect(mockNavigate).toHaveBeenCalledWith('/default/collections/3/edit')
    })

    it('should navigate to detail page when clicking on a row', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const row = screen.getByTestId('collection-row-0')
      await user.click(row)

      expect(mockNavigate).toHaveBeenCalledWith('/default/collections/3')
    })

    it('should open delete confirmation dialog when clicking delete button', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const deleteButton = screen.getByTestId('delete-button-0')
      await user.click(deleteButton)

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
        expect(screen.getByText(/are you sure you want to delete/i)).toBeInTheDocument()
      })
    })

    it('should close delete dialog when clicking cancel', async () => {
      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const deleteButton = screen.getByTestId('delete-button-0')
      await user.click(deleteButton)

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      })

      const cancelButton = screen.getByTestId('confirm-dialog-cancel')
      await user.click(cancelButton)

      await waitFor(() => {
        expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
      })
    })

    it('should call delete API when confirming deletion', async () => {
      const user = userEvent.setup()
      // First call returns collections (already set in beforeEach)

      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      // Clear mock calls after initial load
      resetMockAxios()

      // Mock the delete response, then the refetch
      mockAxios.delete.mockResolvedValueOnce({ data: null })
      mockAxios.get.mockResolvedValue(
        toJsonApiResponse(mockCollections.filter((c) => c.name !== 'orders'))
      )

      // Click delete on the first row (orders after sorting)
      const deleteButton = screen.getByTestId('delete-button-0')
      await user.click(deleteButton)

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      })

      const confirmButton = screen.getByTestId('confirm-dialog-confirm')
      await user.click(confirmButton)

      // Wait for the success toast to appear (indicates delete was successful)
      await waitFor(() => {
        expect(screen.getByText(/deleted successfully/i)).toBeInTheDocument()
      })
    })
  })

  describe('Pagination', () => {
    it('should display pagination when there are more items than page size', async () => {
      // Create more than 10 collections
      const manyCollections: Collection[] = Array.from({ length: 15 }, (_, i) => ({
        id: String(i + 1),
        name: `collection-${i + 1}`,
        displayName: `Collection ${i + 1}`,
        storageMode: 'JSONB' as const,
        active: true,
        currentVersion: 1,
        createdAt: new Date(2024, 0, i + 1).toISOString(),
        updatedAt: new Date(2024, 0, i + 1).toISOString(),
      }))

      mockAxios.get.mockResolvedValue(toJsonApiResponse(manyCollections))

      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('pagination')).toBeInTheDocument()
      })

      expect(screen.getByText(/page 1 of 2/i)).toBeInTheDocument()
    })

    it('should navigate to next page when clicking next button', async () => {
      const manyCollections: Collection[] = Array.from({ length: 15 }, (_, i) => ({
        id: String(i + 1),
        name: `collection-${String(i + 1).padStart(2, '0')}`,
        displayName: `Collection ${i + 1}`,
        storageMode: 'JSONB' as const,
        active: true,
        currentVersion: 1,
        createdAt: new Date(2024, 0, i + 1).toISOString(),
        updatedAt: new Date(2024, 0, i + 1).toISOString(),
      }))

      mockAxios.get.mockResolvedValue(toJsonApiResponse(manyCollections))

      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('pagination')).toBeInTheDocument()
      })

      const nextButton = screen.getByRole('button', { name: /next/i })
      await user.click(nextButton)

      await waitFor(() => {
        expect(screen.getByText(/page 2 of 2/i)).toBeInTheDocument()
      })
    })

    it('should disable previous button on first page', async () => {
      const manyCollections: Collection[] = Array.from({ length: 15 }, (_, i) => ({
        id: String(i + 1),
        name: `collection-${i + 1}`,
        displayName: `Collection ${i + 1}`,
        storageMode: 'JSONB' as const,
        active: true,
        currentVersion: 1,
        createdAt: new Date(2024, 0, i + 1).toISOString(),
        updatedAt: new Date(2024, 0, i + 1).toISOString(),
      }))

      mockAxios.get.mockResolvedValue(toJsonApiResponse(manyCollections))

      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('pagination')).toBeInTheDocument()
      })

      const prevButton = screen.getByRole('button', { name: /previous/i })
      expect(prevButton).toBeDisabled()
    })

    it('should disable next button on last page', async () => {
      const manyCollections: Collection[] = Array.from({ length: 15 }, (_, i) => ({
        id: String(i + 1),
        name: `collection-${String(i + 1).padStart(2, '0')}`,
        displayName: `Collection ${i + 1}`,
        storageMode: 'JSONB' as const,
        active: true,
        currentVersion: 1,
        createdAt: new Date(2024, 0, i + 1).toISOString(),
        updatedAt: new Date(2024, 0, i + 1).toISOString(),
      }))

      mockAxios.get.mockResolvedValue(toJsonApiResponse(manyCollections))

      const user = userEvent.setup()
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('pagination')).toBeInTheDocument()
      })

      const nextButton = screen.getByRole('button', { name: /next/i })
      await user.click(nextButton)

      await waitFor(() => {
        expect(nextButton).toBeDisabled()
      })
    })
  })

  describe('Accessibility', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue(toJsonApiResponse(mockCollections))
    })

    it('should have accessible table structure', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('grid')).toBeInTheDocument()
      })

      expect(screen.getByRole('grid')).toHaveAttribute('aria-label', 'Collections')
    })

    it('should have accessible filter controls', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameFilter = screen.getByTestId('name-filter')
      expect(nameFilter).toHaveAttribute('aria-label')

      const statusFilter = screen.getByTestId('status-filter')
      expect(statusFilter).toHaveAttribute('aria-label')
    })

    it('should have accessible action buttons', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const editButton = screen.getByTestId('edit-button-0')
      expect(editButton).toHaveAttribute('aria-label')

      const deleteButton = screen.getByTestId('delete-button-0')
      expect(deleteButton).toHaveAttribute('aria-label')
    })

    it('should support keyboard navigation for sorting', async () => {
      render(<CollectionsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
      })

      const nameHeader = screen.getByTestId('header-name')
      nameHeader.focus()
      fireEvent.keyDown(nameHeader, { key: 'Enter' })

      await waitFor(() => {
        expect(nameHeader).toHaveAttribute('aria-sort', 'descending')
      })
    })
  })
})
