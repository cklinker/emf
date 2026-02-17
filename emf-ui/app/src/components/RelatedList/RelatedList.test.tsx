import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RelatedList } from './RelatedList'

// Mock API context
const mockGet = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: mockGet,
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    },
  })),
}))

// Mock object permissions
vi.mock('@/hooks/useObjectPermissions', () => ({
  useObjectPermissions: vi.fn(() => ({
    permissions: { canCreate: true, canRead: true, canEdit: true, canDelete: true },
    isLoading: false,
  })),
}))

function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/test-tenant/app/o/accounts/rec-123']}>
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('RelatedList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows empty state when no related records found', async () => {
    // First call for schema
    mockGet.mockResolvedValueOnce({
      content: [{ id: 'col-1', name: 'contacts', displayName: 'Contacts', fields: [] }],
    })
    // Second call for full schema
    mockGet.mockResolvedValueOnce({
      id: 'col-1',
      name: 'contacts',
      displayName: 'Contacts',
      fields: [
        { id: 'f1', name: 'name', type: 'STRING', required: false },
        { id: 'f2', name: 'email', type: 'STRING', required: false },
      ],
    })
    // Third call for related records
    mockGet.mockResolvedValueOnce({
      data: [],
      metadata: { totalCount: 0, currentPage: 1, pageSize: 5, totalPages: 0 },
    })

    render(
      <TestWrapper>
        <RelatedList
          collectionName="contacts"
          foreignKeyField="accountId"
          parentRecordId="rec-123"
          tenantSlug="test-tenant"
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText(/No related/)).toBeDefined()
    })
  })

  it('renders collection label as title', async () => {
    // Schema calls
    mockGet.mockResolvedValueOnce({
      content: [{ id: 'col-1', name: 'contacts', displayName: 'Contacts', fields: [] }],
    })
    mockGet.mockResolvedValueOnce({
      id: 'col-1',
      name: 'contacts',
      displayName: 'Contacts',
      fields: [],
    })
    // Related records
    mockGet.mockResolvedValueOnce({
      data: [],
      metadata: { totalCount: 0, currentPage: 1, pageSize: 5, totalPages: 0 },
    })

    render(
      <TestWrapper>
        <RelatedList
          collectionName="contacts"
          foreignKeyField="accountId"
          parentRecordId="rec-123"
          tenantSlug="test-tenant"
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('Contacts')).toBeDefined()
    })
  })

  it('renders custom label when provided', async () => {
    mockGet.mockResolvedValueOnce({
      content: [{ id: 'col-1', name: 'contacts', displayName: 'Contacts', fields: [] }],
    })
    mockGet.mockResolvedValueOnce({
      id: 'col-1',
      name: 'contacts',
      displayName: 'Contacts',
      fields: [],
    })
    mockGet.mockResolvedValueOnce({
      data: [],
      metadata: { totalCount: 0, currentPage: 1, pageSize: 5, totalPages: 0 },
    })

    render(
      <TestWrapper>
        <RelatedList
          collectionName="contacts"
          foreignKeyField="accountId"
          parentRecordId="rec-123"
          tenantSlug="test-tenant"
          label="Team Members"
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('Team Members')).toBeDefined()
    })
  })

  it('shows "+ New" button when user has canCreate permission', async () => {
    mockGet.mockResolvedValueOnce({
      content: [{ id: 'col-1', name: 'contacts', displayName: 'Contacts', fields: [] }],
    })
    mockGet.mockResolvedValueOnce({
      id: 'col-1',
      name: 'contacts',
      displayName: 'Contacts',
      fields: [],
    })
    mockGet.mockResolvedValueOnce({
      data: [],
      metadata: { totalCount: 0, currentPage: 1, pageSize: 5, totalPages: 0 },
    })

    render(
      <TestWrapper>
        <RelatedList
          collectionName="contacts"
          foreignKeyField="accountId"
          parentRecordId="rec-123"
          tenantSlug="test-tenant"
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('New')).toBeDefined()
    })
  })
})
