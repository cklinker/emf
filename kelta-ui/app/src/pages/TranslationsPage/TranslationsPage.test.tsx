/** TranslationsPage (app-intelligence slice 4): list, add, edit, delete wiring. */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '@/context/I18nContext'
import { TranslationsPage } from './TranslationsPage'

const api = {
  get: vi.fn(),
  postResource: vi.fn(),
  patch: vi.fn(),
  deleteResource: vi.fn(),
}

vi.mock('@/context/ApiContext', () => ({
  useApi: () => ({ apiClient: api }),
}))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <TranslationsPage />
      </I18nProvider>
    </QueryClientProvider>
  )
}

describe('TranslationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.get.mockResolvedValue({
      data: [
        {
          id: 't1',
          attributes: { locale: 'en', key: 'common.save', value: 'Persist' },
        },
      ],
    })
    api.postResource.mockResolvedValue({})
    api.patch.mockResolvedValue({})
    api.deleteResource.mockResolvedValue(undefined)
  })

  it('lists overrides for the selected locale', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('common.save')).toBeInTheDocument())
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining('filter[locale][eq]=en'))
    expect(screen.getByText('Persist')).toBeInTheDocument()
  })

  it('adds a new override for the active locale', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByTestId('translation-add')).toBeInTheDocument())
    fireEvent.change(screen.getByTestId('translation-new-key'), {
      target: { value: 'custom.greeting' },
    })
    fireEvent.change(screen.getByTestId('translation-new-value'), { target: { value: 'Howdy' } })
    fireEvent.click(screen.getByTestId('translation-add'))
    await waitFor(() =>
      expect(api.postResource).toHaveBeenCalledWith('/api/ui-translations', {
        data: {
          type: 'ui-translations',
          attributes: { locale: 'en', key: 'custom.greeting', value: 'Howdy' },
        },
      })
    )
  })

  it('edits an override value in place', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByTestId('translation-edit-t1')).toBeInTheDocument())
    fireEvent.click(screen.getByTestId('translation-edit-t1'))
    fireEvent.change(screen.getByTestId('translation-edit-value-t1'), {
      target: { value: 'Persist harder' },
    })
    fireEvent.click(screen.getByTestId('translation-save-t1'))
    await waitFor(() =>
      expect(api.patch).toHaveBeenCalledWith('/api/ui-translations/t1', {
        data: { type: 'ui-translations', id: 't1', attributes: { value: 'Persist harder' } },
      })
    )
  })

  it('deletes an override', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByTestId('translation-delete-t1')).toBeInTheDocument())
    fireEvent.click(screen.getByTestId('translation-delete-t1'))
    await waitFor(() => expect(api.deleteResource).toHaveBeenCalledWith('/api/ui-translations/t1'))
  })

  it('switching the locale refetches', async () => {
    renderPage()
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    fireEvent.change(screen.getByTestId('translations-locale-select'), { target: { value: 'de' } })
    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith(expect.stringContaining('filter[locale][eq]=de'))
    )
  })
})
