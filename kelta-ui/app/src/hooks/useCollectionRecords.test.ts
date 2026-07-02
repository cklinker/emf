/**
 * useCollectionRecords offline wiring (Rec 2B-2). Asserts online reads write through
 * to the replica, offline reads serve the replica (with client-side sort/filter), and
 * that without an OfflineProvider the hook keeps its online-only behavior.
 */
import React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useCollectionRecords } from './useCollectionRecords'
import { OfflineProvider } from '../offline'
import { InMemoryOfflineStore } from '../offline/store'

const mockGet = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: () => ({ apiClient: { get: (...a: unknown[]) => mockGet(...a) } }),
}))

function offlineWrapper(store: InMemoryOfflineStore) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(OfflineProvider, { store }, children)
    )
  }
}

function bareWrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: qc }, children)
}

beforeEach(() => mockGet.mockReset())
afterEach(() => vi.restoreAllMocks())

describe('useCollectionRecords — offline replica', () => {
  it('online: fetches and writes through to the replica', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    mockGet.mockResolvedValue({
      data: [{ id: '1', name: 'A' }],
      metadata: { totalCount: 1, currentPage: 1, pageSize: 25 },
    })
    const store = new InMemoryOfflineStore()

    const { result } = renderHook(() => useCollectionRecords({ collectionName: 'orders' }), {
      wrapper: offlineWrapper(store),
    })

    await waitFor(() => expect(result.current.data).toHaveLength(1))
    await waitFor(async () =>
      expect(await store.getAll('orders')).toEqual([{ id: '1', name: 'A' }])
    )
  })

  it('offline: serves cached rows without hitting the network', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()
    await store.putRecords('orders', [
      { id: 'x', name: 'Zed' },
      { id: 'y', name: 'Ann' },
    ])

    const { result } = renderHook(
      () =>
        useCollectionRecords({
          collectionName: 'orders',
          sort: { field: 'name', direction: 'asc' },
        }),
      { wrapper: offlineWrapper(store) }
    )

    await waitFor(() => expect(result.current.data).toHaveLength(2))
    expect(mockGet).not.toHaveBeenCalled()
    expect(result.current.total).toBe(2)
    expect(result.current.data.map((r) => r.name)).toEqual(['Ann', 'Zed']) // client-side sort
  })

  it('offline: applies a client-side filter over the replica', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()
    await store.putRecords('orders', [
      { id: '1', status: 'open' },
      { id: '2', status: 'closed' },
    ])

    const { result } = renderHook(
      () =>
        useCollectionRecords({
          collectionName: 'orders',
          filters: [{ id: 'f1', field: 'status', operator: 'equals', value: 'open' }],
        }),
      { wrapper: offlineWrapper(store) }
    )

    await waitFor(() => expect(result.current.total).toBe(1))
    expect(result.current.data[0].id).toBe('1')
  })

  it('no provider: behaves as online-only (admin pages unchanged)', async () => {
    mockGet.mockResolvedValue({
      data: [{ id: '1', name: 'A' }],
      metadata: { totalCount: 1, currentPage: 1, pageSize: 25 },
    })
    const { result } = renderHook(() => useCollectionRecords({ collectionName: 'orders' }), {
      wrapper: bareWrapper,
    })
    await waitFor(() => expect(result.current.data).toHaveLength(1))
    expect(mockGet).toHaveBeenCalled()
  })
})
