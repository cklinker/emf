/**
 * usePageDataSources (slice 2d). Security-critical: assert the fetch hits the SAME authorized JSON:API
 * URL `DataTableNode` uses (`/api/{collection}?page[size]=N`) — no server-side binding resolution —
 * plus single-mode id resolution, client-resolved bound filters, and the MAX_PAGE_DATA_SOURCES cap.
 */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { usePageDataSources, buildListUrl } from './usePageDataSources'
import { MAX_PAGE_DATA_SOURCES } from '../model/limits'
import type { PageDataSource } from '../pageConfig'
import type { BindingScope } from '../model/bindingScope'

const getList = vi.fn()
const getOne = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: () => ({ apiClient: { getList, getOne } }),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: queryClient }, children)
}

beforeEach(() => {
  getList.mockReset()
  getOne.mockReset()
})

describe('buildListUrl', () => {
  it('builds the authorized JSON:API list URL with page size', () => {
    const src: PageDataSource = {
      name: 'accounts',
      collection: 'accounts',
      mode: 'list',
      limit: 25,
    }
    expect(buildListUrl(src, {})).toBe('/api/accounts?page[size]=25')
  })

  it('clamps the page size to MAX_HTTP_PAGE_SIZE', () => {
    const src: PageDataSource = { name: 'a', collection: 'a', mode: 'list', limit: 9999 }
    expect(buildListUrl(src, {})).toBe('/api/a?page[size]=200')
  })

  it('appends fields, sort and a client-resolved bound filter', () => {
    const src: PageDataSource = {
      name: 'a',
      collection: 'accounts',
      mode: 'list',
      fields: ['name', 'email'],
      sort: ['name'],
      filter: { status: { $bind: 'vars.s', mode: 'path' } },
    }
    const scope: BindingScope = { vars: { s: 'open' } }
    const url = buildListUrl(src, scope)
    expect(url).toContain('fields[accounts]=name%2Cemail')
    expect(url).toContain('sort=name')
    expect(url).toContain('filter[status][EQ]=open')
  })
})

describe('usePageDataSources', () => {
  it('fetches a list source on the authorized path and populates data.<name>', async () => {
    getList.mockResolvedValue([{ id: '1', name: 'Acme' }])
    const sources: PageDataSource[] = [
      { name: 'accounts', collection: 'accounts', mode: 'list', limit: 25 },
    ]
    const { result } = renderHook(() => usePageDataSources(sources, {}), { wrapper })
    await waitFor(() => expect(result.current.data.accounts).toEqual([{ id: '1', name: 'Acme' }]))
    expect(getList).toHaveBeenCalledWith('/api/accounts?page[size]=25')
  })

  it('single mode resolves a bound record id client-side and fetches via getOne', async () => {
    getOne.mockResolvedValue({ id: 'r1', name: 'One' })
    const sources: PageDataSource[] = [
      {
        name: 'current',
        collection: 'accounts',
        mode: 'single',
        recordId: { $bind: 'page.params.id', mode: 'path' },
      },
    ]
    const scope: BindingScope = { page: { params: { id: 'r1' } } }
    const { result } = renderHook(() => usePageDataSources(sources, scope), { wrapper })
    await waitFor(() => expect(result.current.data.current).toBeTruthy())
    expect(getOne).toHaveBeenCalledWith('/api/accounts/r1')
    expect(result.current.data.current).toEqual({ id: 'r1', name: 'One' })
  })

  it('single mode returns null on a 404 (denied/missing)', async () => {
    getOne.mockRejectedValue(new Error('404'))
    const sources: PageDataSource[] = [
      { name: 'x', collection: 'accounts', mode: 'single', recordId: 'missing' },
    ]
    const { result } = renderHook(() => usePageDataSources(sources, {}), { wrapper })
    await waitFor(() => expect(getOne).toHaveBeenCalled())
    await waitFor(() => expect(result.current.data.x).toBeNull())
  })

  it('caps the number of sources at MAX_PAGE_DATA_SOURCES', async () => {
    getList.mockResolvedValue([])
    const sources: PageDataSource[] = Array.from({ length: MAX_PAGE_DATA_SOURCES + 5 }, (_, i) => ({
      name: `s${i}`,
      collection: `c${i}`,
      mode: 'list' as const,
    }))
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { result } = renderHook(() => usePageDataSources(sources, {}), { wrapper })
    await waitFor(() => expect(getList).toHaveBeenCalled())
    // Extras beyond the cap are never fetched.
    expect(getList.mock.calls.length).toBeLessThanOrEqual(MAX_PAGE_DATA_SOURCES)
    expect(result.current.data.s12).toBeUndefined()
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })
})
