import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useLookupDisplayMap } from './useLookupDisplayMap'
import type { FieldDefinition } from './useCollectionSchema'

// Mock fetchCollectionSchema — called by the hook for display field resolution
const mockFetchCollectionSchema = vi.fn()
vi.mock('./useCollectionSchema', async () => {
  const actual = await vi.importActual('./useCollectionSchema')
  return {
    ...actual,
    fetchCollectionSchema: (...args: unknown[]) => mockFetchCollectionSchema(...args),
  }
})

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
    mockFetchCollectionSchema.mockReset()
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

  it('returns undefined for reference fields without referenceCollectionId or referenceTarget', () => {
    const fields: FieldDefinition[] = [
      makeField({ name: 'customer', type: 'master_detail' }), // no referenceCollectionId or referenceTarget
    ]

    const { result } = renderHook(() => useLookupDisplayMap(fields), {
      wrapper: TestWrapper,
    })

    expect(result.current.lookupDisplayMap).toBeUndefined()
    expect(result.current.isLoading).toBe(false)
  })

  it('resolves display labels using referenceTarget when referenceCollectionId is missing', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceTarget: 'customers', // only referenceTarget, no referenceCollectionId
      }),
    ]

    // Mock: fetchCollectionSchema returns schema with displayFieldName
    mockFetchCollectionSchema.mockResolvedValue({
      id: 'col-customers',
      name: 'customers',
      displayName: 'Customers',
      displayFieldName: 'full_name',
      fields: [{ id: 'f1', name: 'full_name', type: 'string', required: false }],
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/customers')) {
        return Promise.resolve({
          data: [
            { id: 'cust-1', attributes: { full_name: 'Alice Smith' } },
            { id: 'cust-2', attributes: { full_name: 'Bob Jones' } },
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

  it('resolves display labels for master_detail fields with both referenceTarget and referenceCollectionId', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceCollectionId: 'col-customers',
        referenceTarget: 'customers',
      }),
    ]

    // When both are present, referenceTarget (name) is preferred for grouping
    // so fetchCollectionSchema is called with the name
    mockFetchCollectionSchema.mockResolvedValue({
      id: 'col-customers',
      name: 'customers',
      displayName: 'Customers',
      displayFieldName: 'full_name',
      fields: [{ id: 'f1', name: 'full_name', type: 'string', required: false }],
    })

    // Mock: get for records (JSON:API envelope)
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/customers')) {
        return Promise.resolve({
          data: [
            { id: 'cust-1', attributes: { full_name: 'Alice Smith' } },
            { id: 'cust-2', attributes: { full_name: 'Bob Jones' } },
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

    mockFetchCollectionSchema.mockResolvedValue({
      id: 'col-categories',
      name: 'categories',
      displayName: 'Categories',
      displayFieldName: undefined, // no explicit display field
      fields: [
        { id: 'f1', name: 'name', type: 'string', required: false },
        { id: 'f2', name: 'description', type: 'string', required: false },
      ],
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/categories')) {
        return Promise.resolve({
          data: [{ id: 'cat-1', attributes: { name: 'Electronics' } }],
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

    // Should fall back to 'name' field since no displayFieldName is set
    expect(result.current.lookupDisplayMap!['category']['cat-1']).toBe('Electronics')
  })

  it('falls back to first string field when no displayFieldName or name field', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'product',
        type: 'master_detail',
        referenceTarget: 'products',
      }),
    ]

    mockFetchCollectionSchema.mockResolvedValue({
      id: 'col-products',
      name: 'products',
      displayName: 'Products',
      displayFieldName: undefined, // no explicit display field
      fields: [
        { id: 'f1', name: 'sku', type: 'string', required: false },
        { id: 'f2', name: 'price', type: 'number', required: false },
      ],
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/products')) {
        return Promise.resolve({
          data: [{ id: 'prod-1', attributes: { sku: 'SKU-001', price: 29.99 } }],
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
        referenceTarget: 'customers',
      }),
      makeField({
        name: 'billing_customer',
        type: 'lookup',
        referenceTarget: 'customers', // same target
      }),
    ]

    mockFetchCollectionSchema.mockResolvedValue({
      id: 'col-customers',
      name: 'customers',
      displayName: 'Customers',
      displayFieldName: 'full_name',
      fields: [{ id: 'f1', name: 'full_name', type: 'string', required: false }],
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/customers')) {
        return Promise.resolve({
          data: [{ id: 'cust-1', attributes: { full_name: 'Alice' } }],
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

    // Should only have fetched the schema once (one group)
    expect(mockFetchCollectionSchema).toHaveBeenCalledTimes(1)
  })

  it('falls back to getOne + fetchCollectionSchema when only referenceCollectionId is present', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceCollectionId: 'col-customers',
        // no referenceTarget — must look up name via getOne first
      }),
    ]

    // Mock: getOne to resolve UUID → collection name
    mockGetOne.mockImplementation((url: string) => {
      if (url === '/api/collections/col-customers') {
        return Promise.resolve({ name: 'customers' })
      }
      return Promise.reject(new Error(`Unexpected getOne URL: ${url}`))
    })

    // Mock: fetchCollectionSchema after name is resolved
    mockFetchCollectionSchema.mockResolvedValue({
      id: 'col-customers',
      name: 'customers',
      displayName: 'Customers',
      displayFieldName: 'full_name',
      fields: [{ id: 'f1', name: 'full_name', type: 'string', required: false }],
    })

    // Mock: get for records
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/customers')) {
        return Promise.resolve({
          data: [{ id: 'cust-1', attributes: { full_name: 'Alice' } }],
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

    expect(result.current.lookupDisplayMap!['customer']['cust-1']).toBe('Alice')
    expect(result.current.lookupTargetNameMap!['customer']).toBe('customers')
    expect(mockGetOne).toHaveBeenCalledWith('/api/collections/col-customers')
  })

  it('handles API errors gracefully', async () => {
    const fields: FieldDefinition[] = [
      makeField({
        name: 'customer',
        type: 'master_detail',
        referenceCollectionId: 'col-customers',
      }),
    ]

    // getOne (UUID → name lookup) rejects with error
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
