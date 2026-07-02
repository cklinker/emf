/**
 * OfflineProvider wiring (Rec 2B-2). Asserts the provider exposes a store/engine,
 * reflects live connectivity, and flushes (engine.sync) on a false→true reconnect.
 */
import React from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OfflineProvider, useOffline } from './OfflineProvider'
import { InMemoryOfflineStore } from './store'
import { SyncEngine } from './syncEngine'

vi.mock('../context/ApiContext', () => ({
  useApi: () => ({ apiClient: { get: vi.fn(), getPage: vi.fn() } }),
}))

function makeWrapper(store = new InMemoryOfflineStore()) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return (
      <QueryClientProvider client={qc}>
        <OfflineProvider store={store}>{children}</OfflineProvider>
      </QueryClientProvider>
    )
  }
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('OfflineProvider', () => {
  it('exposes a store + engine and reflects navigator.onLine', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const store = new InMemoryOfflineStore()
    const { result } = renderHook(() => useOffline(), { wrapper: makeWrapper(store) })
    expect(result.current?.store).toBe(store)
    expect(result.current?.engine).toBeInstanceOf(SyncEngine)
    expect(result.current?.online).toBe(true)
  })

  it('flushes (engine.sync) on a false→true reconnect', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const syncSpy = vi.spyOn(SyncEngine.prototype, 'sync').mockResolvedValue({
      pulled: 0,
      deleted: 0,
      pushed: 0,
      failed: 0,
      conflicts: 0,
    })

    render(<div />, { wrapper: makeWrapper() })
    expect(syncSpy).not.toHaveBeenCalled() // starts offline — no sync

    act(() => {
      vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
      window.dispatchEvent(new Event('online'))
    })

    await waitFor(() => expect(syncSpy).toHaveBeenCalledTimes(1))
  })

  it('does not sync while staying online', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const syncSpy = vi.spyOn(SyncEngine.prototype, 'sync')
    render(<div />, { wrapper: makeWrapper() })
    expect(syncSpy).not.toHaveBeenCalled()
  })

  it('returns undefined outside a provider (admin pages no-op)', () => {
    const { result } = renderHook(() => useOffline())
    expect(result.current).toBeUndefined()
  })
})
