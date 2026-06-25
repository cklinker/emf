/**
 * `usePicklistOptions` tests (slice 2f). Verifies the picklist source resolution (FIELD vs GLOBAL via
 * `fieldTypeConfig.globalPicklistId`, handling both the parsed-object and JSON-string forms), `isActive`
 * filtering, `sortOrder` ordering, and the empty-on-error fallback — matching `ObjectFormPage` behaviour.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePicklistOptions, resolvePicklistSource } from './usePicklistOptions'

const mockGetList = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: () => ({ apiClient: { getList: (url: string) => mockGetList(url) } }),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: queryClient }, children)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('resolvePicklistSource', () => {
  it('defaults to the field id with FIELD source', () => {
    expect(resolvePicklistSource({ id: 'f1', fieldTypeConfig: undefined })).toEqual({
      sourceId: 'f1',
      sourceType: 'FIELD',
    })
  })

  it('uses globalPicklistId + GLOBAL when fieldTypeConfig is an object', () => {
    expect(
      resolvePicklistSource({ id: 'f1', fieldTypeConfig: { globalPicklistId: 'gp-9' } })
    ).toEqual({ sourceId: 'gp-9', sourceType: 'GLOBAL' })
  })

  it('handles fieldTypeConfig delivered as a JSON string', () => {
    expect(
      resolvePicklistSource({ id: 'f1', fieldTypeConfig: '{"globalPicklistId":"gp-7"}' })
    ).toEqual({ sourceId: 'gp-7', sourceType: 'GLOBAL' })
  })

  it('falls back to FIELD on malformed JSON config', () => {
    expect(resolvePicklistSource({ id: 'f1', fieldTypeConfig: '{not json' })).toEqual({
      sourceId: 'f1',
      sourceType: 'FIELD',
    })
  })
})

describe('usePicklistOptions', () => {
  it('fetches FIELD values, drops inactive, and sorts by sortOrder', async () => {
    mockGetList.mockResolvedValueOnce([
      { value: 'b', label: 'B', isActive: true, sortOrder: 2 },
      { value: 'a', label: 'A', isActive: true, sortOrder: 1 },
      { value: 'x', label: 'X', isActive: false, sortOrder: 0 },
    ])
    const { result } = renderHook(
      () => usePicklistOptions({ id: 'f1', fieldTypeConfig: undefined }),
      {
        wrapper,
      }
    )
    await waitFor(() => expect(result.current.options).toEqual(['a', 'b']))
    expect(mockGetList).toHaveBeenCalledWith(
      expect.stringContaining('filter[picklistSourceId][eq]=f1')
    )
    expect(mockGetList).toHaveBeenCalledWith(
      expect.stringContaining('filter[picklistSourceType][eq]=FIELD')
    )
  })

  it('fetches GLOBAL values when fieldTypeConfig.globalPicklistId is set', async () => {
    mockGetList.mockResolvedValueOnce([{ value: 'g', label: 'G', isActive: true, sortOrder: 0 }])
    const { result } = renderHook(
      () => usePicklistOptions({ id: 'f1', fieldTypeConfig: { globalPicklistId: 'gp-9' } }),
      { wrapper }
    )
    await waitFor(() => expect(result.current.options).toEqual(['g']))
    expect(mockGetList).toHaveBeenCalledWith(
      expect.stringContaining('filter[picklistSourceId][eq]=gp-9')
    )
    expect(mockGetList).toHaveBeenCalledWith(
      expect.stringContaining('filter[picklistSourceType][eq]=GLOBAL')
    )
  })

  it('returns an empty list on fetch error', async () => {
    mockGetList.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(
      () => usePicklistOptions({ id: 'f1', fieldTypeConfig: undefined }),
      {
        wrapper,
      }
    )
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.options).toEqual([])
  })

  it('does not fetch when disabled (e.g. editor mode)', () => {
    renderHook(() => usePicklistOptions({ id: 'f1', fieldTypeConfig: undefined }, false), {
      wrapper,
    })
    expect(mockGetList).not.toHaveBeenCalled()
  })
})
