import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { ActivityTimeline } from './ActivityTimeline'
import type { ApiClient } from '../../services/apiClient'

// Mock I18n — echo the key so assertions can target stable strings.
vi.mock('../../context/I18nContext', () => ({
  useI18n: vi.fn(() => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string) => key,
  })),
}))

function renderTimeline(apiClient: Partial<ApiClient>) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(ActivityTimeline, {
        collectionId: 'col-1',
        collectionName: 'orders',
        recordId: 'rec-1',
        recordCreatedAt: '2026-01-01T00:00:00Z',
        apiClient: apiClient as ApiClient,
      })
    )
  )
}

describe('ActivityTimeline', () => {
  it('merges a note from /api/notes into the feed', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/notes')) {
        return Promise.resolve([
          { id: 'n1', content: 'Called the customer', createdAt: '2026-02-01T00:00:00Z' },
        ])
      }
      return Promise.resolve([]) // approvals + shares empty
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('Called the customer')).toBeInTheDocument())
    // The notes endpoint was queried for this record.
    expect(getList).toHaveBeenCalledWith(
      expect.stringContaining(
        '/api/notes?filter[collectionId][eq]=col-1&filter[recordId][eq]=rec-1'
      )
    )
  })

  it('truncates long note content', async () => {
    const long = 'x'.repeat(200)
    const getList = vi.fn((url: string) =>
      url.startsWith('/api/notes')
        ? Promise.resolve([{ id: 'n1', content: long, createdAt: '2026-02-01T00:00:00Z' }])
        : Promise.resolve([])
    )

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText(/x{140}…/)).toBeInTheDocument())
  })

  it('merges an attachment from /api/attachments into the feed', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/attachments')) {
        return Promise.resolve([
          { id: 'a1', fileName: 'contract.pdf', uploadedAt: '2026-02-02T00:00:00Z' },
        ])
      }
      return Promise.resolve([])
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText(/contract\.pdf/)).toBeInTheDocument())
    expect(getList).toHaveBeenCalledWith(
      expect.stringContaining(
        '/api/attachments?filter[collectionId][eq]=col-1&filter[recordId][eq]=rec-1'
      )
    )
  })

  it('still renders lifecycle events when there are no notes', async () => {
    const getList = vi.fn(() => Promise.resolve([]))
    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('activity.recordCreated')).toBeInTheDocument())
  })
})
