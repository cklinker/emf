/**
 * usePageDataSources offline wiring (Rec 2B-2). Online sources write through to the
 * replica; offline sources serve cached rows/records without hitting the network.
 */
import React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { usePageDataSources } from './usePageDataSources'
import { OfflineProvider } from '@/offline'
import { InMemoryOfflineStore } from '@/offline/store'
import type { PageDataSource } from '../pageConfig'
import type { BindingScope } from '../model/bindingScope'

const getList = vi.fn()
const getOne = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: () => ({ apiClient: { getList, getOne } }),
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
  getList.mockReset()
  getOne.mockReset()
})
afterEach(() => vi.restoreAllMocks())

describe('usePageDataSources — offline replica', () => {
  it('online list source writes through to the replica', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    getList.mockResolvedValue([{ id: '1', name: 'Acme' }])
    const store = new InMemoryOfflineStore()
    const sources: PageDataSource[] = [
      { name: 'accounts', collection: 'accounts', mode: 'list', limit: 25 },
    ]

    const { result } = renderHook(() => usePageDataSources(sources, {}), {
      wrapper: offlineWrapper(store),
    })

    await waitFor(() => expect(result.current.data.accounts).toEqual([{ id: '1', name: 'Acme' }]))
    await waitFor(async () =>
      expect(await store.getAll('accounts')).toEqual([{ id: '1', name: 'Acme' }])
    )
  })

  it('offline list source serves cached rows without a network call', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()
    await store.putRecords('accounts', [{ id: '9', name: 'Cached Co' }])
    const sources: PageDataSource[] = [
      { name: 'accounts', collection: 'accounts', mode: 'list', limit: 25 },
    ]

    const { result } = renderHook(() => usePageDataSources(sources, {}), {
      wrapper: offlineWrapper(store),
    })

    await waitFor(() =>
      expect(result.current.data.accounts).toEqual([{ id: '9', name: 'Cached Co' }])
    )
    expect(getList).not.toHaveBeenCalled()
  })

  it('offline single source serves the cached record', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const store = new InMemoryOfflineStore()
    await store.putRecords('accounts', [{ id: 'r1', name: 'One' }])
    const sources: PageDataSource[] = [
      { name: 'current', collection: 'accounts', mode: 'single', recordId: 'r1' },
    ]
    const scope: BindingScope = {}

    const { result } = renderHook(() => usePageDataSources(sources, scope), {
      wrapper: offlineWrapper(store),
    })

    await waitFor(() => expect(result.current.data.current).toEqual({ id: 'r1', name: 'One' }))
    expect(getOne).not.toHaveBeenCalled()
  })
})
