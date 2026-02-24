import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useLookupDisplayMap } from './useLookupDisplayMap'
import type { FieldDefinition } from './useCollectionSchema'

// Mock API context
const mockGet = vi.fn()
const mockGetOne = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: mockGet,
      getOne: mockGetOne,
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

function makeField(overrides: Partial<FieldDefinition> & { name: string }): FieldDefinition {
  return {
    id: overrides.id ?? `field-${overrides.name}`,
    name: overrides.name,
    type: overrides.type ?? 'string',
    required: overrides.required ?? false,
    referenceTarget: overrides.referenceTarget,
    referenceCollectionId: overrides.referenceCollectionId,
    displayName: overrides.displayName,
  }
}

describe('useLookupDisplayMap', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGetOne.mockReset()
  })

  it('returns undefined when fields is undefined', () => {
    const { result } = renderHook(() => useLookupDisplayMap(undefined), {
      wrapper: TestWrapper,
    })

    expect(result.current.lookupDisplayMap).toBeUndefined()
    expect(result.current.lookupTargetNameMap).toBeUndefined()
    expect(result.current.isLoading).toBe(false)
  })

  it('returns undefined when no reference fields exist', () => {
    const fields: FieldDefinition[] = [
      makeField({ name: 'name', type: 'string' }),
      makeField({ name: 'age', type: 'number' }),
    ]

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    expect(result.current.lookupDisplayMap).toBeUndefined()
    expect(result.current.isLoading).toBe(false)
  })

  it('returns undefined for reference fields without referenceCollectionId', () => {
    const fields: FieldDefinition[] = [
      makeField({ name: 'customer', type: 'master_detail' }), // no referenceCollectionId
    ]

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    expect(result.current.lookupDisplayMap).toBeUndefined()
    expect(result.current.isLoading).toBe(false)
  })

  it('resolves display labels for master_detail fields', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceCollectionId: 'col-customers',
        referenceTarget: 'customers',
      }),
    ]

    // Mock: getOne for collection schema (JSON:API auto-unwrapped)
    mockGetOne.mockImplementation((url: string) => {
      if (url === '/api/collections/col-customers') {
        return Promise.resolve({
          name: 'customers',
          displayFieldName: 'full_name',
          fields: [{ name: 'full_name', type: 'STRING' }],
        })
      }
      return Promise.reject(new Error(`Unexpected getOne URL: ${url}`))
    })

    // Mock: get for records (returns JSON:API envelope)
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/customers')) {
        return Promise.resolve({
          data: [
            { id: 'cust-1', full_name: 'Alice Smith' },
            { id: 'cust-2', full_name: 'Bob Jones' },
          ],
        })
      }
      return Promise.reject(new Error(`Unexpected get URL: ${url}`))
    })

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.lookupDisplayMap).toBeDefined()
    })

    expect(result.current.lookupDisplayMap!['customer']['cust-1']).toBe('Alice Smith')
    expect(result.current.lookupDisplayMap!['customer']['cust-2']).toBe('Bob Jones')
    expect(result.current.lookupTargetNameMap!['customer']).toBe('customers')
  })

  it('resolves display labels for lookup fields', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'category',
        type: 'lookup',
        referenceCollectionId: 'col-categories',
        referenceTarget: 'categories',
      }),
    ]

    // Mock: getOne for collection schema (JSON:API auto-unwrapped)
    mockGetOne.mockImplementation((url: string) => {
      if (url === '/api/collections/col-categories') {
        return Promise.resolve({
          name: 'categories',
          fields: [{ name: 'name', type: 'STRING' }],
        })
      }
      return Promise.reject(new Error(`Unexpected getOne URL: ${url}`))
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/categories')) {
        return Promise.resolve({
          data: [{ id: 'cat-1', name: 'Electronics' }],
        })
      }
      return Promise.reject(new Error(`Unexpected get URL: ${url}`))
    })

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.lookupDisplayMap).toBeDefined()
    })

    expect(result.current.lookupDisplayMap!['category']['cat-1']).toBe('Electronics')
  })

  it('falls back to first string field when no displayFieldName or name field', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'product',
        type: 'master_detail',
        referenceCollectionId: 'col-products',
        referenceTarget: 'products',
      }),
    ]

    // Mock: getOne for collection schema (JSON:API auto-unwrapped)
    mockGetOne.mockImplementation((url: string) => {
      if (url === '/api/collections/col-products') {
        return Promise.resolve({
          name: 'products',
          fields: [
            { name: 'sku', type: 'STRING' },
            { name: 'price', type: 'DOUBLE' },
          ],
        })
      }
      return Promise.reject(new Error(`Unexpected getOne URL: ${url}`))
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/products')) {
        return Promise.resolve({
          data: [{ id: 'prod-1', sku: 'SKU-001', price: 29.99 }],
        })
      }
      return Promise.reject(new Error(`Unexpected get URL: ${url}`))
    })

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.lookupDisplayMap).toBeDefined()
    })

    expect(result.current.lookupDisplayMap!['product']['prod-1']).toBe('SKU-001')
  })

  it('groups fields by target collection to minimize API calls', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceCollectionId: 'col-customers',
      }),
      makeField({
        name: 'billing_customer',
        type: 'lookup',
        referenceCollectionId: 'col-customers', // same target
      }),
    ]

    // Mock: getOne for collection schema (JSON:API auto-unwrapped)
    mockGetOne.mockImplementation((url: string) => {
      if (url === '/api/collections/col-customers') {
        return Promise.resolve({
          name: 'customers',
          displayFieldName: 'full_name',
          fields: [{ name: 'full_name', type: 'STRING' }],
        })
      }
      return Promise.reject(new Error(`Unexpected getOne URL: ${url}`))
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/customers')) {
        return Promise.resolve({
          data: [{ id: 'cust-1', full_name: 'Alice' }],
        })
      }
      return Promise.reject(new Error(`Unexpected get URL: ${url}`))
    })

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.lookupDisplayMap).toBeDefined()
    })

    // Both fields should be resolved from the same target
    expect(result.current.lookupDisplayMap!['customer']['cust-1']).toBe('Alice')
    expect(result.current.lookupDisplayMap!['billing_customer']['cust-1']).toBe('Alice')

    // Should only have fetched the collection schema once via getOne
    const schemaCallCount = mockGetOne.mock.calls.filter(
      (c: string[]) => c[0] === '/api/collections/col-customers'
    ).length
    expect(schemaCallCount).toBe(1)
  })

  it('handles API errors gracefully', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceCollectionId: 'col-customers',
      }),
    ]

    // getOne (collection schema) rejects with error
    mockGetOne.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    await waitFor(() => {
      expect(result.current.lookupDisplayMap).toBeDefined()
    })

    // Should have an empty map for the field, not crash
    expect(result.current.lookupDisplayMap!['customer']).toEqual({})
  })
})
