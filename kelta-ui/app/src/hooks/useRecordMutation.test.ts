/**
 * useRecordMutation offline wiring (Rec 2B-2). While offline, writes queue to the
 * outbox via engine.queue instead of hitting the network; online is unchanged.
 */
import React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useRecordMutation } from './useRecordMutation'
import { OfflineProvider } from '../offline'
import { InMemoryOfflineStore } from '../offline/store'

const apiClient = {
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}
vi.mock('../context/ApiContext', () => ({
  useApi: () => ({ apiClient }),
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

beforeEach(() => {
  apiClient.post
    .mockReset()
    .mockResolvedValue({ data: { id: 's1', type: 'orders', attributes: {} } })
  apiClient.put
    .mockReset()
    .mockResolvedValue({ data: { id: 's1', type: 'orders', attributes: {} } })
  apiClient.patch
    .mockReset()
    .mockResolvedValue({ data: { id: 's1', type: 'orders', attributes: {} } })
  apiClient.delete.mockReset().mockResolvedValue(undefined)
})
afterEach(() => vi.restoreAllMocks())

describe('useRecordMutation — offline queueing', () => {
  it('offline create: queues to the outbox instead of POSTing', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()

    const { result } = renderHook(() => useRecordMutation({ collectionName: 'orders' }), {
      wrapper: offlineWrapper(store),
    })

    await result.current.create.mutateAsync({ name: 'X' })

    expect(apiClient.post).not.toHaveBeenCalled()
    const outbox = await store.listOutbox()
    expect(outbox).toHaveLength(1)
    expect(outbox[0].op).toBe('create')
    expect(outbox[0].payload).toEqual({ name: 'X' })
  })

  it('offline delete: queues a delete op instead of DELETEing', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()
    await store.putRecords('orders', [{ id: 'r1', name: 'A' }])

    const { result } = renderHook(() => useRecordMutation({ collectionName: 'orders' }), {
      wrapper: offlineWrapper(store),
    })

    await result.current.remove.mutateAsync('r1')

    expect(apiClient.delete).not.toHaveBeenCalled()
    const outbox = await store.listOutbox()
    expect(outbox).toHaveLength(1)
    expect(outbox[0].op).toBe('delete')
    expect(outbox[0].recordId).toBe('r1')
    // optimistic local delete
    expect(await store.get('orders', 'r1')).toBeUndefined()
  })

  it('online create: POSTs as usual', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const store = new InMemoryOfflineStore()

    const { result } = renderHook(() => useRecordMutation({ collectionName: 'orders' }), {
      wrapper: offlineWrapper(store),
    })

    await result.current.create.mutateAsync({ name: 'X' })

    expect(apiClient.post).toHaveBeenCalledTimes(1)
    await waitFor(async () => expect(await store.listOutbox()).toHaveLength(0))
  })
})
