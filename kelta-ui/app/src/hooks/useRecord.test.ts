/**
 * useRecord offline wiring (Rec 2B-2). Online reads write through to the replica;
 * offline reads serve the cached record; no provider ⇒ online-only behavior.
 */
import React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useRecord } from './useRecord'
import { OfflineProvider } from '../offline'
import { InMemoryOfflineStore } from '../offline/store'

const mockGetWithMeta = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: () => ({ apiClient: { getWithMeta: (...a: unknown[]) => mockGetWithMeta(...a) } }),
}))

function offlineWrapper(store: InMemoryOfflineStore) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(OfflineProvider, { store, children })
    )
  }
}

beforeEach(() => mockGetWithMeta.mockReset())
afterEach(() => vi.restoreAllMocks())

describe('useRecord — offline replica', () => {
  it('online: fetches and writes the record through to the replica', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    mockGetWithMeta.mockResolvedValue({ data: { id: '1', name: 'A' }, etag: 'W/"1"' })
    const store = new InMemoryOfflineStore()

    const { result } = renderHook(() => useRecord({ collectionName: 'orders', recordId: '1' }), {
      wrapper: offlineWrapper(store),
    })

    await waitFor(() => expect(result.current.record?.id).toBe('1'))
    expect(result.current.etag).toBe('W/"1"')
    await waitFor(async () =>
      expect(await store.get('orders', '1')).toEqual({ id: '1', name: 'A' })
    )
  })

  it('offline: serves the cached record without a network call', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()
    await store.putRecords('orders', [{ id: '1', name: 'Cached' }])

    const { result } = renderHook(() => useRecord({ collectionName: 'orders', recordId: '1' }), {
      wrapper: offlineWrapper(store),
    })

    await waitFor(() => expect(result.current.record?.name).toBe('Cached'))
    expect(mockGetWithMeta).not.toHaveBeenCalled()
    expect(result.current.etag).toBeUndefined()
  })
})
