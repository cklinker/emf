import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePageLayout } from './usePageLayout'

const mockGetList = vi.fn()
const mockGet = vi.fn()

vi.mock('../context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: mockGet,
      getList: mockGetList,
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    },
  })),
}))

vi.mock('../context/CollectionStoreContext', () => ({
  useCollectionStore: vi.fn(() => ({
    getCollectionById: vi.fn(() => null),
    getFieldById: vi.fn(() => null),
  })),
}))

function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return React.createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('usePageLayout', () => {
  beforeEach(() => {
    mockGetList.mockReset()
    mockGet.mockReset()
  })

  it('fallback query filters by layoutType=DETAIL to ignore LIST defaults on the same collection', async () => {
    mockGetList.mockImplementation(async (url: string) => {
      if (url.includes('/api/layout-assignments')) return []
      if (url.includes('/api/page-layouts')) {
        expect(url).toContain('filter[layoutType][eq]=DETAIL')
        return [
          {
            id: 'detail-layout-id',
            collectionId: 'coll-1',
            isDefault: true,
            layoutType: 'DETAIL',
          },
        ]
      }
      if (url.includes('/api/layout-rules')) return []
      return []
    })

    mockGet.mockResolvedValueOnce({
      data: {
        type: 'page-layouts',
        id: 'detail-layout-id',
        attributes: {
          collectionId: 'coll-1',
          name: 'Customer Layout',
          layoutType: 'DETAIL',
          isDefault: true,
        },
      },
      included: [
        {
          type: 'layout-sections',
          id: 'sec-1',
          attributes: { heading: 'General', columns: 2, sortOrder: 0 },
        },
      ],
    })

    const { result } = renderHook(() => usePageLayout('coll-1', 'user-1'), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.layout?.id).toBe('detail-layout-id')
    expect(result.current.layout?.layoutType).toBe('DETAIL')
  })

  it('returns null when the assignment-resolved layout is not a DETAIL layout', async () => {
    mockGetList.mockImplementation(async (url: string) => {
      if (url.includes('/api/layout-assignments')) {
        return [
          {
            id: 'assign-1',
            collectionId: 'coll-1',
            profileId: 'user-1',
            layoutId: 'list-layout-id',
          },
        ]
      }
      if (url.includes('/api/layout-rules')) return []
      return []
    })

    mockGet.mockResolvedValueOnce({
      data: {
        type: 'page-layouts',
        id: 'list-layout-id',
        attributes: {
          collectionId: 'coll-1',
          name: 'Customer LIST',
          layoutType: 'LIST',
          isDefault: true,
        },
      },
      included: [],
    })

    const { result } = renderHook(() => usePageLayout('coll-1', 'user-1'), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.layout).toBeNull()
  })

  it('warns and picks the first layout when multiple default DETAIL layouts are returned', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    mockGetList.mockImplementation(async (url: string) => {
      if (url.includes('/api/layout-assignments')) return []
      if (url.includes('/api/page-layouts')) {
        return [
          { id: 'older', collectionId: 'coll-1', isDefault: true, layoutType: 'DETAIL' },
          { id: 'newer', collectionId: 'coll-1', isDefault: true, layoutType: 'DETAIL' },
        ]
      }
      if (url.includes('/api/layout-rules')) return []
      return []
    })

    mockGet.mockResolvedValueOnce({
      data: {
        type: 'page-layouts',
        id: 'older',
        attributes: {
          collectionId: 'coll-1',
          name: 'Older',
          layoutType: 'DETAIL',
          isDefault: true,
        },
      },
      included: [
        {
          type: 'layout-sections',
          id: 'sec-1',
          attributes: { heading: 'General', columns: 2, sortOrder: 0 },
        },
      ],
    })

    const { result } = renderHook(() => usePageLayout('coll-1', 'user-1'), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.layout?.id).toBe('older')
    expect(warnSpy).toHaveBeenCalled()
    warnSpy.mockRestore()
  })
})
