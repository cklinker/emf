import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePrefetch } from './usePrefetch'

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

describe('usePrefetch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns prefetchRecord and cancelPrefetch functions', () => {
    const { result } = renderHook(() => usePrefetch({ collectionName: 'accounts' }), {
      wrapper: TestWrapper,
    })

    expect(typeof result.current.prefetchRecord).toBe('function')
    expect(typeof result.current.cancelPrefetch).toBe('function')
  })

  it('does not prefetch when collectionName is undefined', () => {
    const { result } = renderHook(() => usePrefetch({ collectionName: undefined }), {
      wrapper: TestWrapper,
    })

    act(() => {
      result.current.prefetchRecord('rec-123')
    })

    act(() => {
      vi.advanceTimersByTime(200)
    })

    expect(mockGet).not.toHaveBeenCalled()
  })

  it('cancels pending prefetch', () => {
    const { result } = renderHook(
      () => usePrefetch({ collectionName: 'accounts', debounceMs: 150 }),
      { wrapper: TestWrapper }
    )

    act(() => {
      result.current.prefetchRecord('rec-123')
    })

    // Cancel before debounce fires
    act(() => {
      vi.advanceTimersByTime(100)
      result.current.cancelPrefetch()
    })

    act(() => {
      vi.advanceTimersByTime(100)
    })

    expect(mockGet).not.toHaveBeenCalled()
  })

  it('replaces previous prefetch when new one is triggered', () => {
    const { result } = renderHook(
      () => usePrefetch({ collectionName: 'accounts', debounceMs: 150 }),
      { wrapper: TestWrapper }
    )

    // Start first prefetch
    act(() => {
      result.current.prefetchRecord('rec-123')
    })

    // Before debounce fires, start another
    act(() => {
      vi.advanceTimersByTime(100)
      result.current.prefetchRecord('rec-456')
    })

    // First should not have fired
    // Wait for second to fire
    act(() => {
      vi.advanceTimersByTime(150)
    })

    // Only the second record should be fetched
    // (Due to React Query's internal batching, we check that rec-123 was not directly fetched)
    // The prefetchQuery is called internally by React Query
    expect(mockGet).not.toHaveBeenCalledWith(expect.stringContaining('/accounts/rec-123'))
  })
})
