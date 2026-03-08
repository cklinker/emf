/**
 * ResourceBrowserPage Tests
 *
 * Unit tests for the ResourceBrowserPage component.
 * Tests cover:
 * - Rendering the collections list
 * - Filtering collections by search
 * - Navigating to collection data view
 * - Loading and error states
 * - Empty state
 * - Accessibility
 *
 * Requirements tested:
 * - 11.1: Resource browser allows selecting a collection
 */

import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createTestWrapper,
  setupAuthMocks,
  mockAxios,
  resetMockAxios,
  createAxiosError,
} from '../../test/testUtils'
import { ResourceBrowserPage } from './ResourceBrowserPage'

// Mock navigate function
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Mock collection summaries - format matches /api/collections API response
// The summary endpoint only returns active collections as a flat array
const mockCollectionSummaries = [
  { id: '1', name: 'users', displayName: 'Users' },
  { id: '2', name: 'products', displayName: 'Products' },
  { id: '3', name: 'orders', displayName: 'Orders' },
]

describe('ResourceBrowserPage', () => {
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
            setTimeout(() => resolve({ data: mockCollectionSummaries }), 100)
          )
      )

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      // Look for the loading spinner component
      expect(screen.getByRole('status')).toBeInTheDocument()
    })
  })

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockAxios.get.mockRejectedValue(createAxiosError(500))

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument()
      })
    })

    it('should display retry button on error', async () => {
      mockAxios.get.mockRejectedValue(createAxiosError(500))

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })

    it('should retry fetch when clicking retry button', async () => {
      // The component calls window.location.reload() on retry
      const reloadMock = vi.fn()
      Object.defineProperty(window, 'location', {
        configurable: true,
        value: { ...window.location, reload: reloadMock },
      })

      mockAxios.get.mockRejectedValue(createAxiosError(500))

      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: /retry/i }))

      expect(reloadMock).toHaveBeenCalled()
    })
  })

  describe('Collections Display', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue({ data: mockCollectionSummaries })
    })

    it('should display page title', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /resource browser/i })).toBeInTheDocument()
      })
    })

    it('should display all collections from summary endpoint', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
        expect(screen.getByText('Products')).toBeInTheDocument()
        expect(screen.getByText('Orders')).toBeInTheDocument()
      })
    })

    it('should display collection display names', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
        expect(screen.getByText('Products')).toBeInTheDocument()
        expect(screen.getByText('Orders')).toBeInTheDocument()
      })
    })

    it('should display collection slugs', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument()
        expect(screen.getByText('products')).toBeInTheDocument()
        expect(screen.getByText('orders')).toBeInTheDocument()
      })
    })

    it('should display results count', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('results-count')).toHaveTextContent('3 collections')
      })
    })
  })

  describe('Search Filtering', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue({ data: mockCollectionSummaries })
    })

    it('should filter collections by name', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const searchInput = screen.getByTestId('collection-search')
      await user.type(searchInput, 'user')

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
        expect(screen.queryByText('Products')).not.toBeInTheDocument()
        expect(screen.queryByText('Orders')).not.toBeInTheDocument()
      })
    })

    it('should filter collections by display name', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const searchInput = screen.getByTestId('collection-search')
      await user.type(searchInput, 'Product')

      await waitFor(() => {
        expect(screen.queryByText('Users')).not.toBeInTheDocument()
        expect(screen.getByText('Products')).toBeInTheDocument()
        expect(screen.queryByText('Orders')).not.toBeInTheDocument()
      })
    })

    it('should show empty state when no collections match search', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const searchInput = screen.getByTestId('collection-search')
      await user.type(searchInput, 'nonexistent')

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })

    it('should update results count when filtering', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('results-count')).toHaveTextContent('3 collections')
      })

      const searchInput = screen.getByTestId('collection-search')
      await user.type(searchInput, 'user')

      await waitFor(() => {
        expect(screen.getByTestId('results-count')).toHaveTextContent('1 collection')
        expect(screen.getByTestId('results-count')).toHaveTextContent('matching "user"')
      })
    })

    it('should clear search when clicking clear button', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const searchInput = screen.getByTestId('collection-search')
      await user.type(searchInput, 'user')

      await waitFor(() => {
        expect(screen.queryByText('Products')).not.toBeInTheDocument()
      })

      const clearButton = screen.getByTestId('clear-search')
      await user.click(clearButton)

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
        expect(screen.getByText('Products')).toBeInTheDocument()
        expect(screen.getByText('Orders')).toBeInTheDocument()
      })
    })

    it('should be case insensitive', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const searchInput = screen.getByTestId('collection-search')
      await user.type(searchInput, 'USER')

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })
    })
  })

  describe('Navigation', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue({ data: mockCollectionSummaries })
    })

    it('should navigate to collection data view when clicking a collection card', async () => {
      const user = userEvent.setup()
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const usersCard = screen.getByTestId('collection-card-0')
      await user.click(usersCard)

      expect(mockNavigate).toHaveBeenCalledWith('/default/resources/users')
    })

    it('should navigate to collection data view when pressing Enter on a collection card', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const usersCard = screen.getByTestId('collection-card-0')
      usersCard.focus()
      fireEvent.keyDown(usersCard, { key: 'Enter' })

      expect(mockNavigate).toHaveBeenCalledWith('/default/resources/users')
    })

    it('should navigate to collection data view when pressing Space on a collection card', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const usersCard = screen.getByTestId('collection-card-0')
      usersCard.focus()
      fireEvent.keyDown(usersCard, { key: ' ' })

      expect(mockNavigate).toHaveBeenCalledWith('/default/resources/users')
    })
  })

  describe('Empty State', () => {
    it('should show empty state when no collections exist', async () => {
      mockAxios.get.mockResolvedValue({ data: [] })

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })

    it('should show empty state when summary endpoint returns empty array', async () => {
      // The summary endpoint only returns active collections,
      // so an empty array means no active collections exist
      mockAxios.get.mockResolvedValue({ data: [] })

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })
  })

  describe('Accessibility', () => {
    beforeEach(() => {
      mockAxios.get.mockResolvedValue({ data: mockCollectionSummaries })
    })

    it('should have accessible search input', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const searchInput = screen.getByTestId('collection-search')
      expect(searchInput).toHaveAttribute('aria-label')
    })

    it('should have accessible collection cards', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const usersCard = screen.getByTestId('collection-card-0')
      expect(usersCard).toHaveAttribute('role', 'listitem')
      expect(usersCard).toHaveAttribute('tabIndex', '0')
      expect(usersCard).toHaveAttribute('aria-label')
    })

    it('should have accessible collections grid', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const grid = screen.getByTestId('collections-grid')
      expect(grid).toHaveAttribute('role', 'list')
      expect(grid).toHaveAttribute('aria-label')
    })

    it('should have live region for results count', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const resultsCount = screen.getByTestId('results-count')
      expect(resultsCount).toHaveAttribute('aria-live', 'polite')
    })

    it('should support keyboard navigation between cards', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument()
      })

      const cards = screen.getAllByTestId(/collection-card-/)
      cards.forEach((card) => {
        expect(card).toHaveAttribute('tabIndex', '0')
      })
    })
  })
})
