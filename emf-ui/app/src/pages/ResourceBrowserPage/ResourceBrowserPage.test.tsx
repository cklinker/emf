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

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createTestWrapper, setupAuthMocks, wrapFetchMock } from '../../test/testUtils';
import { ResourceBrowserPage, CollectionSummary } from './ResourceBrowserPage';

// Mock navigate function
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock collections data - format matches API response with fields array
const mockCollections = [
  {
    id: '1',
    name: 'users',
    displayName: 'Users',
    description: 'User accounts and profiles',
    active: true,
    fields: [{}, {}, {}, {}, {}], // 5 fields
  },
  {
    id: '2',
    name: 'products',
    displayName: 'Products',
    description: 'Product catalog',
    active: true,
    fields: [{}, {}, {}, {}, {}, {}, {}, {}], // 8 fields
  },
  {
    id: '3',
    name: 'orders',
    displayName: 'Orders',
    description: 'Customer orders',
    active: true,
    fields: [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}], // 12 fields
  },
  {
    id: '4',
    name: 'archived_data',
    displayName: 'Archived Data',
    description: 'Old archived records',
    active: false,
    fields: [{}, {}, {}], // 3 fields
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
    clone: function () {
      return this;
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
  } as Response;
}

global.fetch = mockFetch;

/**
 * Create a wrapper component with all required providers
 */

describe('ResourceBrowserPage', () => {
  let cleanupAuthMocks: () => void;

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks();
    mockFetch.mockReset();
    wrapFetchMock(mockFetch);
    mockNavigate.mockReset();
  });

  afterEach(() => {
    cleanupAuthMocks();
    vi.clearAllMocks();
  });

  describe('Loading State', () => {
    it('should display loading spinner while fetching collections', async () => {
      // Mock a delayed response
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(() => resolve(createMockResponse(mockCollections)), 100)
          )
      );

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      // Look for the loading spinner component
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));
      wrapFetchMock(mockFetch);

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/API request failed/i)).toBeInTheDocument();
      });
    });

    it('should display retry button on error', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));
      wrapFetchMock(mockFetch);

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });
    });

    it('should retry fetch when clicking retry button', async () => {
      mockFetch.mockResolvedValueOnce(createMockResponse(null, false, 500));
      mockFetch.mockResolvedValueOnce(createMockResponse({ content: mockCollections, totalElements: mockCollections.length, totalPages: 1, size: 1000, number: 0 }));
      wrapFetchMock(mockFetch);

      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });

      await user.click(screen.getByRole('button', { name: /retry/i }));

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });
    });
  });

  describe('Collections Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockCollections, totalElements: mockCollections.length, totalPages: 1, size: 1000, number: 0 }));
      wrapFetchMock(mockFetch);
    });

    it('should display page title', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /resource browser/i })).toBeInTheDocument();
      });
    });

    it('should display only active collections', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
        expect(screen.getByText('Products')).toBeInTheDocument();
        expect(screen.getByText('Orders')).toBeInTheDocument();
        // Inactive collection should not be displayed
        expect(screen.queryByText('Archived Data')).not.toBeInTheDocument();
      });
    });

    it('should display collection display names', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
        expect(screen.getByText('Products')).toBeInTheDocument();
        expect(screen.getByText('Orders')).toBeInTheDocument();
      });
    });

    it('should display collection slugs', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('users')).toBeInTheDocument();
        expect(screen.getByText('products')).toBeInTheDocument();
        expect(screen.getByText('orders')).toBeInTheDocument();
      });
    });

    it('should display collection descriptions', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('User accounts and profiles')).toBeInTheDocument();
        expect(screen.getByText('Product catalog')).toBeInTheDocument();
        expect(screen.getByText('Customer orders')).toBeInTheDocument();
      });
    });

    it('should display field counts', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Check that field count elements are present
        const fieldCounts = screen.getAllByText(/fields/i);
        expect(fieldCounts.length).toBeGreaterThan(0);
      });
    });

    it('should display results count', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('results-count')).toHaveTextContent('3 collections');
      });
    });
  });

  describe('Search Filtering', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockCollections, totalElements: mockCollections.length, totalPages: 1, size: 1000, number: 0 }));
    });

    it('should filter collections by name', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'user');

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
        expect(screen.queryByText('Products')).not.toBeInTheDocument();
        expect(screen.queryByText('Orders')).not.toBeInTheDocument();
      });
    });

    it('should filter collections by display name', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'Product');

      await waitFor(() => {
        expect(screen.queryByText('Users')).not.toBeInTheDocument();
        expect(screen.getByText('Products')).toBeInTheDocument();
        expect(screen.queryByText('Orders')).not.toBeInTheDocument();
      });
    });

    it('should filter collections by description', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'catalog');

      await waitFor(() => {
        expect(screen.queryByText('Users')).not.toBeInTheDocument();
        expect(screen.getByText('Products')).toBeInTheDocument();
        expect(screen.queryByText('Orders')).not.toBeInTheDocument();
      });
    });

    it('should show empty state when no collections match search', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'nonexistent');

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      });
    });

    it('should update results count when filtering', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('results-count')).toHaveTextContent('3 collections');
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'user');

      await waitFor(() => {
        expect(screen.getByTestId('results-count')).toHaveTextContent('1 collection');
        expect(screen.getByTestId('results-count')).toHaveTextContent('matching "user"');
      });
    });

    it('should clear search when clicking clear button', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'user');

      await waitFor(() => {
        expect(screen.queryByText('Products')).not.toBeInTheDocument();
      });

      const clearButton = screen.getByTestId('clear-search');
      await user.click(clearButton);

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
        expect(screen.getByText('Products')).toBeInTheDocument();
        expect(screen.getByText('Orders')).toBeInTheDocument();
      });
    });

    it('should be case insensitive', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      await user.type(searchInput, 'USER');

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });
    });
  });

  describe('Navigation', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockCollections, totalElements: mockCollections.length, totalPages: 1, size: 1000, number: 0 }));
    });

    it('should navigate to collection data view when clicking a collection card', async () => {
      const user = userEvent.setup();
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const usersCard = screen.getByTestId('collection-card-0');
      await user.click(usersCard);

      expect(mockNavigate).toHaveBeenCalledWith('/resources/users');
    });

    it('should navigate to collection data view when pressing Enter on a collection card', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const usersCard = screen.getByTestId('collection-card-0');
      usersCard.focus();
      fireEvent.keyDown(usersCard, { key: 'Enter' });

      expect(mockNavigate).toHaveBeenCalledWith('/resources/users');
    });

    it('should navigate to collection data view when pressing Space on a collection card', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const usersCard = screen.getByTestId('collection-card-0');
      usersCard.focus();
      fireEvent.keyDown(usersCard, { key: ' ' });

      expect(mockNavigate).toHaveBeenCalledWith('/resources/users');
    });
  });

  describe('Empty State', () => {
    it('should show empty state when no collections exist', async () => {
      mockFetch.mockResolvedValue(createMockResponse([]));

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      });
    });

    it('should show empty state when all collections are inactive', async () => {
      const inactiveCollections = mockCollections.map((c) => ({ ...c, active: false }));
      mockFetch.mockResolvedValue(createMockResponse({ content: inactiveCollections, totalElements: inactiveCollections.length, totalPages: 1, size: 1000, number: 0 }));

      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockCollections, totalElements: mockCollections.length, totalPages: 1, size: 1000, number: 0 }));
    });

    it('should have accessible search input', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('collection-search');
      expect(searchInput).toHaveAttribute('aria-label');
    });

    it('should have accessible collection cards', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const usersCard = screen.getByTestId('collection-card-0');
      expect(usersCard).toHaveAttribute('role', 'listitem');
      expect(usersCard).toHaveAttribute('tabIndex', '0');
      expect(usersCard).toHaveAttribute('aria-label');
    });

    it('should have accessible collections grid', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const grid = screen.getByTestId('collections-grid');
      expect(grid).toHaveAttribute('role', 'list');
      expect(grid).toHaveAttribute('aria-label');
    });

    it('should have live region for results count', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const resultsCount = screen.getByTestId('results-count');
      expect(resultsCount).toHaveAttribute('aria-live', 'polite');
    });

    it('should support keyboard navigation between cards', async () => {
      render(<ResourceBrowserPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Users')).toBeInTheDocument();
      });

      const cards = screen.getAllByTestId(/collection-card-/);
      cards.forEach((card) => {
        expect(card).toHaveAttribute('tabIndex', '0');
      });
    });
  });
});
