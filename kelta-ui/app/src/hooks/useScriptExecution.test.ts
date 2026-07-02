import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useScriptExecution } from './useScriptExecution'
import type { QuickActionExecutionContext } from '@/types/quickActions'

const mockPost = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: { post: (...args: unknown[]) => mockPost(...args) },
  })),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}))

const mockToast = { success: vi.fn(), error: vi.fn() }
vi.mock('sonner', () => ({
  toast: Object.assign((...args: unknown[]) => mockToast.success(...args), {
    success: (...args: unknown[]) => mockToast.success(...args),
    error: (...args: unknown[]) => mockToast.error(...args),
  }),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: queryClient }, children)
}

const context: QuickActionExecutionContext = {
  collectionName: 'orders',
  recordId: 'r1',
  tenantSlug: 'acme',
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useScriptExecution', () => {
  it('posts to the execute endpoint with input + record context', async () => {
    mockPost.mockResolvedValue({ success: true, output: {}, executionTimeMs: 5 })

    const { result } = renderHook(() => useScriptExecution(), { wrapper })
    await result.current.execute({ scriptId: 's1', context, parameters: { amount: 10 } })

    expect(mockPost).toHaveBeenCalledWith('/api/scripts/s1/execute', {
      input: { amount: 10 },
      context: { collectionName: 'orders', recordId: 'r1' },
    })
  })

  it('maps a successful response and surfaces the script action', async () => {
    mockPost.mockResolvedValue({
      success: true,
      output: { action: { type: 'toast', message: 'Done', variant: 'success' } },
      executionTimeMs: 3,
    })

    const { result } = renderHook(() => useScriptExecution(), { wrapper })
    const res = await result.current.execute({ scriptId: 's1', context })

    expect(res.success).toBe(true)
    await waitFor(() => expect(mockToast.success).toHaveBeenCalledWith('Done'))
  })

  it('returns a failure result when the request throws', async () => {
    mockPost.mockRejectedValue(new Error('network'))

    const { result } = renderHook(() => useScriptExecution(), { wrapper })
    const res = await result.current.execute({ scriptId: 's1', context })

    expect(res.success).toBe(false)
    expect(mockToast.error).toHaveBeenCalled()
  })

  it('navigates on an open_record action returned by the script', async () => {
    mockPost.mockResolvedValue({
      success: true,
      output: { action: { type: 'open_record', collection: 'invoices', recordId: 'inv-9' } },
    })

    const { result } = renderHook(() => useScriptExecution(), { wrapper })
    await result.current.execute({ scriptId: 's1', context })

    expect(mockNavigate).toHaveBeenCalledWith('/acme/app/o/invoices/inv-9')
  })
})
