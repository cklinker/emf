/** useOfflineOutbox (Phase 4 slice 1): live lists, retry/discard, inert without provider. */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OfflineProvider } from './OfflineProvider'
import { InMemoryOfflineStore } from './store'
import { useOfflineOutbox } from './useOfflineOutbox'
import type { SyncApi } from './syncEngine'

vi.mock('../context/ApiContext', () => ({
  useApi: () => ({ apiClient: apiDouble }),
}))
vi.mock('../context/TenantContext', () => ({
  getTenantSlug: () => 'acme',
}))

class ApiStatusError extends Error {
  readonly status: number
  constructor(status: number) {
    super(`status ${status}`)
    this.status = status
  }
}

const putMock = vi.fn(async () => ({ id: 'o1' }))
const apiDouble: SyncApi = {
  get: async <T,>() => ({ data: { deletions: [], deletionCount: 0, cursor: 'c' } }) as T,
  getPage: async <T,>() => ({ content: [] as T[] }),
  postResource: async <T,>() => ({ id: 'srv-1' }) as T,
  putResource: putMock as unknown as SyncApi['putResource'],
  deleteResource: async () => undefined,
}

function makeWrapper(store: InMemoryOfflineStore) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <OfflineProvider store={store}>{children}</OfflineProvider>
      </QueryClientProvider>
    )
  }
}

describe('useOfflineOutbox', () => {
  it('is inert (empty lists, no-op actions) without an OfflineProvider', async () => {
    const queryClient = new QueryClient()
    const { result } = renderHook(() => useOfflineOutbox(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    })
    expect(result.current.pendingCount).toBe(0)
    expect(result.current.failedCount).toBe(0)
    await act(async () => {
      await result.current.retry('x')
      await result.current.discard('x')
    })
  })

  it('lists pending and failed ops live via the engine subscription', async () => {
    const store = new InMemoryOfflineStore()
    await store.addFailed({
      id: 'f1',
      collection: 'orders',
      op: 'update',
      recordId: 'o1',
      payload: { v: 2 },
      queuedAt: '2026-07-08T01:00:00Z',
      status: 422,
      error: 'bad value',
      failedAt: '2026-07-08T01:01:00Z',
    })
    const { result } = renderHook(() => useOfflineOutbox(), { wrapper: makeWrapper(store) })

    await waitFor(() => expect(result.current.failedCount).toBe(1))
    expect(result.current.failed[0]).toMatchObject({ id: 'f1', error: 'bad value' })
    expect(result.current.pendingCount).toBe(0)
  })

  it('retry re-queues the failed op and pushes it when online', async () => {
    putMock.mockClear()
    putMock.mockResolvedValue({ id: 'o1' })
    const store = new InMemoryOfflineStore()
    await store.addFailed({
      id: 'f1',
      collection: 'orders',
      op: 'update',
      recordId: 'o1',
      payload: { v: 2 },
      queuedAt: '2026-07-08T01:00:00Z',
      status: 422,
      error: 'bad value',
      failedAt: '2026-07-08T01:01:00Z',
    })
    const { result } = renderHook(() => useOfflineOutbox(), { wrapper: makeWrapper(store) })
    await waitFor(() => expect(result.current.failedCount).toBe(1))

    await act(async () => {
      await result.current.retry('f1')
    })

    expect(putMock).toHaveBeenCalledWith('/api/orders/o1', { v: 2 })
    await waitFor(() => expect(result.current.failedCount).toBe(0))
    expect(result.current.pendingCount).toBe(0)
  })

  it('a retried op that fails again is retained again', async () => {
    putMock.mockClear()
    putMock.mockRejectedValue(new ApiStatusError(422))
    const store = new InMemoryOfflineStore()
    await store.addFailed({
      id: 'f1',
      collection: 'orders',
      op: 'update',
      recordId: 'o1',
      payload: { v: 2 },
      queuedAt: '2026-07-08T01:00:00Z',
      status: 422,
      error: 'bad value',
      failedAt: '2026-07-08T01:01:00Z',
    })
    const { result } = renderHook(() => useOfflineOutbox(), { wrapper: makeWrapper(store) })
    await waitFor(() => expect(result.current.failedCount).toBe(1))

    await act(async () => {
      await result.current.retry('f1')
    })

    await waitFor(() => expect(result.current.failedCount).toBe(1))
    expect(result.current.failed[0]).toMatchObject({ id: 'f1', status: 422 })
  })

  it('discard removes a failed op', async () => {
    const store = new InMemoryOfflineStore()
    await store.addFailed({
      id: 'f1',
      collection: 'orders',
      op: 'delete',
      recordId: 'o1',
      queuedAt: '2026-07-08T01:00:00Z',
      error: 'gone',
      failedAt: '2026-07-08T01:01:00Z',
    })
    const { result } = renderHook(() => useOfflineOutbox(), { wrapper: makeWrapper(store) })
    await waitFor(() => expect(result.current.failedCount).toBe(1))

    await act(async () => {
      await result.current.discard('f1')
    })
    await waitFor(() => expect(result.current.failedCount).toBe(0))
  })
})
