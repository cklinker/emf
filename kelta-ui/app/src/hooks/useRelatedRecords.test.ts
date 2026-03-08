import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useRelatedRecords } from './useRelatedRecords'

// Mock API context
const mockGet = vi.fn()
vi.mock('../context/ApiContext', () => ({
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

function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return React.createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useRelatedRecords', () => {
  it('returns empty data when disabled', () => {
    const { result } = renderHook(
      () =>
        useRelatedRecords({
          collectionName: undefined,
          foreignKeyField: 'accountId',
          parentRecordId: 'rec-123',
        }),
      { wrapper: TestWrapper }
    )

    expect(result.current.data).toEqual([])
    expect(result.current.total).toBe(0)
    expect(result.current.isLoading).toBe(false)
  })

  it('returns empty data when parentRecordId is missing', () => {
    const { result } = renderHook(
      () =>
        useRelatedRecords({
          collectionName: 'contacts',
          foreignKeyField: 'accountId',
          parentRecordId: undefined,
        }),
      { wrapper: TestWrapper }
    )

    expect(result.current.data).toEqual([])
    expect(result.current.total).toBe(0)
    expect(result.current.isLoading).toBe(false)
  })

  it('fetches related records with correct filter params', async () => {
    mockGet.mockResolvedValueOnce({
      data: [
        { id: 'c1', type: 'contacts', attributes: { name: 'Jane Doe', accountId: 'rec-123' } },
        { id: 'c2', type: 'contacts', attributes: { name: 'John Smith', accountId: 'rec-123' } },
      ],
      metadata: { totalCount: 2, currentPage: 1, pageSize: 5, totalPages: 1 },
    })

    const { result } = renderHook(
      () =>
        useRelatedRecords({
          collectionName: 'contacts',
          foreignKeyField: 'accountId',
          parentRecordId: 'rec-123',
          limit: 5,
        }),
      { wrapper: TestWrapper }
    )

    await waitFor(() => {
      expect(result.current.data.length).toBe(2)
    })

    expect(result.current.total).toBe(2)
    expect(mockGet).toHaveBeenCalledWith(
      expect.stringContaining('filter%5BaccountId%5D%5Beq%5D=rec-123')
    )
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('page%5Bsize%5D=5'))
  })

  it('defaults limit to 5', async () => {
    mockGet.mockResolvedValueOnce({
      data: [],
      metadata: { totalCount: 0, currentPage: 1, pageSize: 5, totalPages: 0 },
    })

    renderHook(
      () =>
        useRelatedRecords({
          collectionName: 'contacts',
          foreignKeyField: 'accountId',
          parentRecordId: 'rec-123',
        }),
      { wrapper: TestWrapper }
    )

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalled()
    })

    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('page%5Bsize%5D=5'))
  })
})
