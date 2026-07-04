import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useQuickActions } from './useQuickActions'

const mockGetList = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: vi.fn(() => ({ apiClient: { getList: (...a: unknown[]) => mockGetList(...a) } })),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: qc }, children)
}

beforeEach(() => vi.clearAllMocks())

describe('useQuickActions', () => {
  it('fetches active actions for the collection and maps actionType → type, sorted', async () => {
    mockGetList.mockResolvedValue([
      {
        id: '2',
        label: 'B',
        actionType: 'run_script',
        context: 'record',
        sortOrder: 2,
        config: null,
      },
      {
        id: '1',
        label: 'A',
        actionType: 'update_field',
        context: 'record',
        sortOrder: 1,
        config: null,
      },
    ])

    const { result } = renderHook(() => useQuickActions({ collectionName: 'orders' }), { wrapper })

    await waitFor(() => expect(result.current.actions).toHaveLength(2))
    expect(mockGetList).toHaveBeenCalledWith(
      expect.stringContaining('/api/quick-actions?filter[collectionName][eq]=orders')
    )
    expect(result.current.actions.map((a) => a.id)).toEqual(['1', '2']) // sorted by sortOrder
    expect(result.current.actions[0].type).toBe('update_field')
  })

  it('filters by context (record/list/both)', async () => {
    mockGetList.mockResolvedValue([
      { id: '1', label: 'R', actionType: 'run_script', context: 'record', sortOrder: 1 },
      { id: '2', label: 'L', actionType: 'run_script', context: 'list', sortOrder: 2 },
      { id: '3', label: 'B', actionType: 'run_script', context: 'both', sortOrder: 3 },
    ])

    const { result } = renderHook(
      () => useQuickActions({ collectionName: 'orders', context: 'list' }),
      { wrapper }
    )

    await waitFor(() => expect(result.current.actions.length).toBeGreaterThan(0))
    expect(result.current.actions.map((a) => a.id)).toEqual(['2', '3'])
  })

  it('treats a fetch error as no actions (collection may be unprovisioned)', async () => {
    mockGetList.mockRejectedValue(new Error('404'))

    const { result } = renderHook(() => useQuickActions({ collectionName: 'orders' }), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.actions).toEqual([])
  })
})
