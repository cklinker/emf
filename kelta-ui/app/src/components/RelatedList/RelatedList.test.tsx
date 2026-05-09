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

// Mock collection store context
vi.mock('@/context/CollectionStoreContext', () => ({
  useCollectionStore: vi.fn(() => ({
    isLoading: false,
    collections: [],
    getCollectionByName: vi.fn(() => undefined),
    getCollectionById: vi.fn(() => undefined),
    getFieldById: vi.fn(() => undefined),
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
    // Drop any queued mockResolvedValueOnce values left over from a prior test.
    // clearAllMocks only clears call history, not the implementation queue.
    mockGet.mockReset()
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

  describe('column resolution', () => {
    function mockSchemaWith(fieldNames: string[]) {
      // useCollectionSchema fetches `/api/collections/{name}?include=fields` and
      // expects a JSON:API envelope; field records arrive in the `included` array.
      mockGet.mockResolvedValueOnce({
        data: {
          id: 'col-1',
          type: 'collections',
          attributes: { name: 'contacts', displayName: 'Contacts' },
        },
        included: fieldNames.map((name, i) => ({
          id: `f-${i}`,
          type: 'fields',
          attributes: {
            name,
            displayName: name,
            type: 'STRING',
            required: false,
            active: true,
            fieldOrder: i,
          },
        })),
      })
    }

    function mockOneRecord(values: Record<string, unknown>) {
      mockGet.mockResolvedValueOnce({
        data: [{ id: 'rec-1', accountId: 'rec-123', ...values }],
        metadata: { totalCount: 1, currentPage: 1, pageSize: 5, totalPages: 1 },
      })
    }

    it('renders only configured displayColumns, in given order', async () => {
      mockSchemaWith(['name', 'email', 'phone', 'title'])
      mockOneRecord({ name: 'Ada', email: 'a@x', phone: '555', title: 'Eng' })

      render(
        <TestWrapper>
          <RelatedList
            collectionName="contacts"
            foreignKeyField="accountId"
            parentRecordId="rec-123"
            tenantSlug="test-tenant"
            displayColumns={['title', 'email']}
          />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByText('Eng')).toBeDefined()
      })

      const headers = screen.getAllByRole('columnheader').map((el) => el.textContent)
      expect(headers).toEqual(['title', 'email'])
      // 'Ada' is the `name` field — not in displayColumns, so should NOT render
      expect(screen.queryByText('Ada')).toBeNull()
    })

    it('falls back to first MAX_COLUMNS schema fields when displayColumns is empty', async () => {
      mockSchemaWith(['name', 'email', 'phone', 'title', 'notes'])
      mockOneRecord({ name: 'Ada' })

      render(
        <TestWrapper>
          <RelatedList
            collectionName="contacts"
            foreignKeyField="accountId"
            parentRecordId="rec-123"
            tenantSlug="test-tenant"
            displayColumns={[]}
          />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByText('Ada')).toBeDefined()
      })

      const headers = screen.getAllByRole('columnheader').map((el) => el.textContent)
      expect(headers).toEqual(['name', 'email', 'phone', 'title'])
    })

    it('passes sortField as JSON:API sort param (asc default)', async () => {
      mockSchemaWith(['name'])
      mockOneRecord({ name: 'Ada' })

      render(
        <TestWrapper>
          <RelatedList
            collectionName="contacts"
            foreignKeyField="accountId"
            parentRecordId="rec-123"
            tenantSlug="test-tenant"
            displayColumns={['name']}
            sortField="name"
          />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByText('Ada')).toBeDefined()
      })

      const recordsCall = mockGet.mock.calls.find(([url]) =>
        String(url).startsWith('/api/contacts')
      )
      expect(recordsCall).toBeDefined()
      expect(String(recordsCall![0])).toContain('sort=name')
      expect(String(recordsCall![0])).not.toContain('sort=-name')
    })

    it('prefixes sort param with - for desc direction', async () => {
      mockSchemaWith(['name'])
      mockOneRecord({ name: 'Ada' })

      render(
        <TestWrapper>
          <RelatedList
            collectionName="contacts"
            foreignKeyField="accountId"
            parentRecordId="rec-123"
            tenantSlug="test-tenant"
            displayColumns={['name']}
            sortField="name"
            sortDirection="desc"
          />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByText('Ada')).toBeDefined()
      })

      const recordsCall = mockGet.mock.calls.find(([url]) =>
        String(url).startsWith('/api/contacts')
      )
      expect(String(recordsCall![0])).toContain('sort=-name')
    })

    it('omits sort param when sortField is not provided', async () => {
      mockSchemaWith(['name'])
      mockOneRecord({ name: 'Ada' })

      render(
        <TestWrapper>
          <RelatedList
            collectionName="contacts"
            foreignKeyField="accountId"
            parentRecordId="rec-123"
            tenantSlug="test-tenant"
            displayColumns={['name']}
          />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByText('Ada')).toBeDefined()
      })

      const recordsCall = mockGet.mock.calls.find(([url]) =>
        String(url).startsWith('/api/contacts')
      )
      expect(String(recordsCall![0])).not.toContain('sort=')
    })

    it('silently drops unknown names from displayColumns', async () => {
      mockSchemaWith(['name', 'email'])
      mockOneRecord({ name: 'Ada', email: 'a@x' })

      render(
        <TestWrapper>
          <RelatedList
            collectionName="contacts"
            foreignKeyField="accountId"
            parentRecordId="rec-123"
            tenantSlug="test-tenant"
            displayColumns={['ghost', 'name', 'phantom']}
          />
        </TestWrapper>
      )

      await waitFor(() => {
        expect(screen.getByText('Ada')).toBeDefined()
      })

      const headers = screen.getAllByRole('columnheader').map((el) => el.textContent)
      expect(headers).toEqual(['name'])
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
