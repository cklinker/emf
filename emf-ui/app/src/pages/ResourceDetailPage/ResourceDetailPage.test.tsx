/**
 * ResourceDetailPage Tests
 *
 * Tests for the ResourceDetailPage component including:
 * - Loading states
 * - Error states
 * - Field value display with formatting
 * - Edit and delete actions
 * - Navigation
 *
 * Requirements:
 * - 11.7: Resource browser displays resource detail view
 * - 11.8: Resource browser allows viewing all field values
 * - 11.10: Resource browser allows deleting resources with confirmation
 */

import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { MemoryRouter, Routes, Route, BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthWrapper, setupAuthMocks, wrapFetchMock } from '../../test/testUtils'
import { ResourceDetailPage } from './ResourceDetailPage'
import type { CollectionSchema, Resource } from './ResourceDetailPage'

// Mock the I18nContext
vi.mock('../../context/I18nContext', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, string>) => {
      const translations: Record<string, string> = {
        'common.loading': 'Loading...',
        'common.back': 'Back',
        'common.edit': 'Edit',
        'common.delete': 'Delete',
        'common.cancel': 'Cancel',
        'common.yes': 'Yes',
        'common.no': 'No',
        'common.noData': 'No data available',
        'resources.title': 'Resource Browser',
        'resources.viewRecord': 'View Record',
        'resources.editRecord': 'Edit Record',
        'resources.deleteRecord': 'Delete Record',
        'resources.confirmDelete': 'Are you sure you want to delete this record?',
        'resources.record': 'Record',
        'collections.fields': 'Fields',
        'collections.created': 'Created',
        'collections.updated': 'Updated',
        'fields.types.string': 'String',
        'fields.types.number': 'Number',
        'fields.types.boolean': 'Boolean',
        'fields.types.date': 'Date',
        'fields.types.datetime': 'Date & Time',
        'fields.types.json': 'JSON',
        'fields.types.reference': 'Reference',
        'errors.generic': 'An error occurred',
        'errors.notFound': 'Not found',
        'success.deleted': params?.item
          ? `${params.item} deleted successfully`
          : 'Deleted successfully',
      }
      return translations[key] || key
    },
    formatDate: (date: Date, options?: Intl.DateTimeFormatOptions) => {
      return date.toLocaleDateString('en-US', options)
    },
    formatNumber: (num: number) => num.toLocaleString('en-US'),
    direction: 'ltr' as const,
    locale: 'en',
    setLocale: vi.fn(),
  }),
  I18nProvider: ({ children }: { children: React.ReactNode }) => children,
}))

// Mock the Toast hook
const mockShowToast = vi.fn()
vi.mock('../../components', () => ({
  useToast: () => ({
    showToast: mockShowToast,
  }),
  ConfirmDialog: ({
    open,
    title,
    message,
    onConfirm,
    onCancel,
  }: {
    open: boolean
    title: string
    message: string
    confirmLabel?: string
    cancelLabel?: string
    onConfirm: () => void
    onCancel: () => void
    variant?: string
  }) =>
    open ? (
      <div data-testid="confirm-dialog" role="dialog">
        <h2>{title}</h2>
        <p>{message}</p>
        <button onClick={onCancel}>Cancel</button>
        <button onClick={onConfirm}>Confirm</button>
      </div>
    ) : null,
  LoadingSpinner: ({ label }: { label?: string; size?: string }) => (
    <div data-testid="loading-spinner">{label || 'Loading...'}</div>
  ),
  ErrorMessage: ({ error, onRetry }: { error: Error; onRetry?: () => void }) => (
    <div data-testid="error-message">
      <p>{error.message}</p>
      {onRetry && <button onClick={onRetry}>Retry</button>}
    </div>
  ),
}))

// Mock navigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Mock the new T6-T9 components to isolate ResourceDetailPage tests
vi.mock('../../components/RecordHeader/RecordHeader', () => ({
  RecordHeader: ({
    record,
    schema,
  }: {
    record: { id: string }
    schema: { displayName: string }
  }) => (
    <div data-testid="record-header">
      <h1 data-testid="record-header-name">{schema.displayName}</h1>
      <span data-testid="resource-id">{record.id}</span>
    </div>
  ),
}))

vi.mock('../../components/RecordActionsBar/RecordActionsBar', () => ({
  RecordActionsBar: ({
    onEdit,
    onDelete,
    onBack,
    onToggleFavorite,
    isFavorite,
  }: {
    collectionName: string
    recordId: string
    onEdit: () => void
    onDelete: () => void
    onBack: () => void
    onToggleFavorite: () => void
    isFavorite: boolean
    apiClient: unknown
  }) => (
    <div data-testid="record-actions-bar">
      <button data-testid="back-button" aria-label="Back" onClick={onBack}>
        Back
      </button>
      <button data-testid="edit-button" aria-label="Edit Record" onClick={onEdit}>
        Edit
      </button>
      <button data-testid="delete-button" aria-label="Delete Record" onClick={onDelete}>
        Delete
      </button>
      <button data-testid="favorite-button" onClick={onToggleFavorite}>
        {isFavorite ? 'Unfav' : 'Fav'}
      </button>
    </div>
  ),
}))

vi.mock('../../components/RelatedRecordsSection/RelatedRecordsSection', () => ({
  RelatedRecordsSection: () => <div data-testid="related-records-section">Related Records</div>,
}))

vi.mock('../../components/ActivityTimeline/ActivityTimeline', () => ({
  ActivityTimeline: () => <div data-testid="activity-timeline">Activity</div>,
}))

vi.mock('../../components/NotesSection/NotesSection', () => ({
  NotesSection: () => <div data-testid="notes-section">Notes</div>,
}))

vi.mock('../../components/AttachmentsSection/AttachmentsSection', () => ({
  AttachmentsSection: () => <div data-testid="attachments-section">Attachments</div>,
}))

// Sample test data
const mockSchema: CollectionSchema = {
  id: 'col-1',
  name: 'users',
  displayName: 'Users',
  fields: [
    { id: 'f1', name: 'name', displayName: 'Full Name', type: 'string', required: true },
    { id: 'f2', name: 'email', displayName: 'Email Address', type: 'string', required: true },
    { id: 'f3', name: 'age', displayName: 'Age', type: 'number', required: false },
    { id: 'f4', name: 'active', displayName: 'Is Active', type: 'boolean', required: false },
    { id: 'f5', name: 'birthDate', displayName: 'Birth Date', type: 'date', required: false },
    { id: 'f6', name: 'lastLogin', displayName: 'Last Login', type: 'datetime', required: false },
    { id: 'f7', name: 'metadata', displayName: 'Metadata', type: 'json', required: false },
    {
      id: 'f8',
      name: 'organizationId',
      displayName: 'Organization',
      type: 'reference',
      required: false,
      referenceTarget: 'organizations',
    },
  ],
}

const mockResource: Resource = {
  id: 'res-123',
  name: 'John Doe',
  email: 'john@example.com',
  age: 30,
  active: true,
  birthDate: '1993-05-15',
  lastLogin: '2024-01-15T10:30:00Z',
  metadata: { role: 'admin', department: 'Engineering' },
  organizationId: 'org-456',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-15T12:00:00Z',
}

// Helper to create a fresh QueryClient for each test
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  })
}

// Helper to render with providers
function renderWithProviders(
  ui: React.ReactElement,
  { route = '/resources/users/res-123', queryClient = createTestQueryClient() } = {}
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthWrapper>
          <Routes>
            <Route path="/resources/:collection/:id" element={ui} />
            <Route path="/resources/:collection" element={<div>List Page</div>} />
            <Route path="/resources" element={<div>Browser Page</div>} />
          </Routes>
        </AuthWrapper>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('ResourceDetailPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  describe('Loading State', () => {
    it('should display loading spinner while fetching data', async () => {
      // Mock fetch to delay response
      const mockFetch = vi.fn().mockImplementation(
        () => new Promise(() => {}) // Never resolves
      )
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument()
    })
  })

  describe('Error States', () => {
    it('should display error message when schema fetch fails', async () => {
      const mockFetch = vi
        .fn()
        .mockRejectedValueOnce(new Error('Failed to fetch collection schema'))
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument()
      })
    })

    it('should display error message when resource fetch fails', async () => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockRejectedValueOnce(new Error('Failed to fetch resource'))
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument()
      })
    })

    it('should display not found error when resource returns 404', async () => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: false,
          status: 404,
        })
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument()
      })
    })
  })

  describe('Successful Data Display', () => {
    beforeEach(() => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockResource),
        })
      wrapFetchMock(mockFetch)
    })

    it('should display the record header', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('record-header')).toBeInTheDocument()
      })
    })

    it('should display the resource ID', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('resource-id')).toHaveTextContent('res-123')
      })
    })

    it('should display breadcrumb navigation', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        const breadcrumb = screen.getByRole('navigation', { name: 'Breadcrumb' })
        expect(breadcrumb).toBeInTheDocument()
        expect(screen.getByText('Resource Browser')).toBeInTheDocument()
      })
    })

    it('should display all field values - Requirement 11.8', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('fields-grid')).toBeInTheDocument()
      })

      // Check string field
      expect(screen.getByTestId('field-value-name')).toHaveTextContent('John Doe')
      expect(screen.getByTestId('field-value-email')).toHaveTextContent('john@example.com')

      // Check number field
      expect(screen.getByTestId('field-value-age')).toHaveTextContent('30')

      // Check boolean field
      expect(screen.getByTestId('field-value-active')).toHaveTextContent('Yes')

      // Check reference field
      expect(screen.getByTestId('field-value-organizationId')).toHaveTextContent('org-456')
    })

    it('should display field type badges', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('fields-grid')).toBeInTheDocument()
      })

      // Check that field types are displayed
      expect(screen.getAllByText('String').length).toBeGreaterThan(0)
      expect(screen.getByText('Number')).toBeInTheDocument()
      expect(screen.getByText('Boolean')).toBeInTheDocument()
    })

    it('should display metadata section with timestamps', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('created-at')).toBeInTheDocument()
        expect(screen.getByTestId('updated-at')).toBeInTheDocument()
      })
    })

    it('should format JSON fields properly', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        const jsonField = screen.getByTestId('field-value-metadata')
        expect(jsonField).toBeInTheDocument()
        // JSON should be formatted
        expect(jsonField.textContent).toContain('role')
        expect(jsonField.textContent).toContain('admin')
      })
    })

    it('should render reference fields as links', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        const referenceField = screen.getByTestId('field-value-organizationId')
        const link = referenceField.querySelector('a')
        expect(link).toBeInTheDocument()
        expect(link).toHaveAttribute('href', '/default/resources/organizations/org-456')
      })
    })
  })

  describe('Navigation', () => {
    beforeEach(() => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockResource),
        })
      wrapFetchMock(mockFetch)
    })

    it('should navigate back to list when back button is clicked', async () => {
      const user = userEvent.setup()
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('back-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('back-button'))

      expect(mockNavigate).toHaveBeenCalledWith('/default/resources/users')
    })

    it('should navigate to edit page when edit button is clicked', async () => {
      const user = userEvent.setup()
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('edit-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button'))

      expect(mockNavigate).toHaveBeenCalledWith('/default/resources/users/res-123/edit')
    })
  })

  describe('Delete Functionality - Requirement 11.10', () => {
    beforeEach(() => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockResource),
        })
      wrapFetchMock(mockFetch)
    })

    it('should open confirmation dialog when delete button is clicked', async () => {
      const user = userEvent.setup()
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('delete-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button'))

      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      expect(screen.getByText('Delete Record')).toBeInTheDocument()
    })

    it('should close dialog when cancel is clicked', async () => {
      const user = userEvent.setup()
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('delete-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button'))
      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()

      await user.click(screen.getByText('Cancel'))
      expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    })

    it('should delete resource and navigate when confirmed', async () => {
      const user = userEvent.setup()

      // Set up complete mock including sharing query and delete response
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockResource),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve([]),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({}),
        })
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('delete-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button'))
      await user.click(screen.getByText('Confirm'))

      await waitFor(() => {
        expect(mockShowToast).toHaveBeenCalledWith('Record deleted successfully', 'success')
        expect(mockNavigate).toHaveBeenCalledWith('/default/resources/users')
      })
    })

    it('should show error toast when delete fails', async () => {
      const user = userEvent.setup()

      // Set up complete mock including sharing query and failed delete response
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockResource),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve([]),
        })
        .mockRejectedValueOnce(new Error('Failed to delete resource'))
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('delete-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button'))
      await user.click(screen.getByText('Confirm'))

      await waitFor(() => {
        expect(mockShowToast).toHaveBeenCalledWith('Failed to delete resource', 'error')
      })
    })
  })

  describe('Empty/Null Field Values', () => {
    it('should display placeholder for null values', async () => {
      const resourceWithNulls: Resource = {
        id: 'res-456',
        name: 'Test User',
        email: null as unknown as string,
        age: undefined as unknown as number,
      }

      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(resourceWithNulls),
        })
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('fields-grid')).toBeInTheDocument()
      })

      // Null/undefined values should show placeholder
      expect(screen.getByTestId('field-value-email')).toHaveTextContent('-')
      expect(screen.getByTestId('field-value-age')).toHaveTextContent('-')
    })
  })

  describe('Boolean Field Display', () => {
    it('should display "No" for false boolean values', async () => {
      const resourceWithFalse: Resource = {
        ...mockResource,
        active: false,
      }

      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(resourceWithFalse),
        })
      wrapFetchMock(mockFetch)

      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('field-value-active')).toHaveTextContent('No')
      })
    })
  })

  describe('Props Override', () => {
    it('should use props over route params when provided', async () => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ ...mockSchema, name: 'products', displayName: 'Products' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ ...mockResource, id: 'prod-789' }),
        })
      wrapFetchMock(mockFetch)

      render(
        <QueryClientProvider client={createTestQueryClient()}>
          <BrowserRouter>
            <AuthWrapper>
              <ResourceDetailPage collectionName="products" resourceId="prod-789" />
            </AuthWrapper>
          </BrowserRouter>
        </QueryClientProvider>
      )

      await waitFor(() => {
        expect(screen.getByTestId('record-header')).toBeInTheDocument()
        expect(screen.getByTestId('resource-id')).toHaveTextContent('prod-789')
      })
    })
  })

  describe('Accessibility', () => {
    beforeEach(() => {
      const mockFetch = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockSchema),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve(mockResource),
        })
      wrapFetchMock(mockFetch)
    })

    it('should have proper aria labels on buttons', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByTestId('back-button')).toHaveAttribute('aria-label', 'Back')
        expect(screen.getByTestId('edit-button')).toHaveAttribute('aria-label', 'Edit Record')
        expect(screen.getByTestId('delete-button')).toHaveAttribute('aria-label', 'Delete Record')
      })
    })

    it('should have proper breadcrumb navigation', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        const breadcrumb = screen.getByRole('navigation', { name: 'Breadcrumb' })
        expect(breadcrumb).toBeInTheDocument()
      })
    })

    it('should have proper section headings', async () => {
      renderWithProviders(<ResourceDetailPage />)

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: 'Fields' })).toBeInTheDocument()
        expect(screen.getByRole('heading', { name: 'Metadata' })).toBeInTheDocument()
      })
    })
  })
})
