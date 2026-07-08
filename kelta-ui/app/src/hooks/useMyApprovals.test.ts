import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useMyApprovals } from './useMyApprovals'

const mockGet = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: vi.fn(() => ({ apiClient: { get: (...a: unknown[]) => mockGet(...a) } })),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: qc }, children)
}

const USER = '11111111-1111-1111-1111-111111111111'

beforeEach(() => vi.clearAllMocks())

describe('useMyApprovals', () => {
  it('does not fetch without a userId', () => {
    renderHook(() => useMyApprovals(undefined), { wrapper })
    expect(mockGet).not.toHaveBeenCalled()
  })

  it('maps pending step instances with instance + collection includes', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url.includes('approval-step-instances')) {
        return Promise.resolve({
          data: [
            {
              id: 'step-1',
              type: 'approval-step-instances',
              attributes: { status: 'PENDING' },
              relationships: {
                approvalInstanceId: {
                  data: { id: 'inst-1', type: 'approval-instances' },
                },
              },
            },
          ],
          included: [
            {
              id: 'inst-1',
              type: 'approval-instances',
              attributes: {
                recordId: 'rec-1',
                submittedBy: 'sub-uuid',
                submittedAt: '2026-07-08T00:00:00Z',
              },
              relationships: {
                collectionId: { data: { id: 'col-1', type: 'collections' } },
              },
            },
            {
              id: 'col-1',
              type: 'collections',
              attributes: { name: 'orders' },
            },
          ],
        })
      }
      return Promise.resolve({ data: [] })
    })

    const { result } = renderHook(() => useMyApprovals(USER), { wrapper })

    await waitFor(() => expect(result.current.pendingCount).toBe(1))
    const row = result.current.pending[0]
    expect(row.stepInstanceId).toBe('step-1')
    expect(row.instanceId).toBe('inst-1')
    expect(row.recordId).toBe('rec-1')
    expect(row.collectionName).toBe('orders')
    expect(row.submittedBy).toBe('sub-uuid')

    const pendingUrl = mockGet.mock.calls
      .map((c) => c[0] as string)
      .find((u) => u.includes('approval-step-instances'))
    expect(pendingUrl).toContain(`filter[assignedTo][eq]=${USER}`)
    expect(pendingUrl).toContain('filter[status][eq]=PENDING')
    expect(pendingUrl).toContain('include=approvalInstanceId,approvalInstanceId.collectionId')
  })

  it('maps submissions filtered by submittedBy', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url.includes('approval-instances?')) {
        return Promise.resolve({
          data: [
            {
              id: 'inst-9',
              type: 'approval-instances',
              attributes: {
                recordId: 'rec-9',
                status: 'REJECTED',
                submittedAt: '2026-07-01T00:00:00Z',
                completedAt: '2026-07-02T00:00:00Z',
              },
              relationships: {
                collectionId: { data: { id: 'col-2', type: 'collections' } },
              },
            },
          ],
          included: [{ id: 'col-2', type: 'collections', attributes: { name: 'expenses' } }],
        })
      }
      return Promise.resolve({ data: [] })
    })

    const { result } = renderHook(() => useMyApprovals(USER), { wrapper })

    await waitFor(() => expect(result.current.submissions).toHaveLength(1))
    const row = result.current.submissions[0]
    expect(row.instanceId).toBe('inst-9')
    expect(row.status).toBe('REJECTED')
    expect(row.collectionName).toBe('expenses')

    const url = mockGet.mock.calls
      .map((c) => c[0] as string)
      .find((u) => u.includes('approval-instances?'))
    expect(url).toContain(`filter[submittedBy][eq]=${USER}`)
    expect(url).toContain('sort=-submittedAt')
  })
})
