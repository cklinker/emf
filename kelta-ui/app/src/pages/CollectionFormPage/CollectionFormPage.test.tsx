/**
 * CollectionFormPage tests — edit-mode read/write of displayName + displayFieldId.
 *
 * Regression coverage for two bugs:
 *  - the update payload dropped `displayName` (and `active`), so display-name edits never saved;
 *  - the edit query read only `attributes`, never the `displayFieldId` relationship, so the saved
 *    display field reset to "Auto-detect" on reload and looked unsaved.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { CollectionFormPage } from './CollectionFormPage'
import { I18nProvider } from '../../context/I18nContext'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate, useParams: () => ({ id: 'col-123' }) }
})

// JSON:API GET payload: displayName is empty, displayFieldId lives in `relationships` (a lookup),
// and the collection's fields arrive via ?include=fields.
const getResponse = {
  data: {
    type: 'collections',
    id: 'col-123',
    attributes: {
      name: 'providers',
      displayName: '',
      description: 'Streaming providers',
      active: true,
    },
    relationships: { displayFieldId: { data: { type: 'fields', id: 'field-name' } } },
  },
  included: [
    {
      type: 'fields',
      id: 'field-name',
      attributes: { name: 'name', displayName: 'Name', active: true },
    },
    {
      type: 'fields',
      id: 'field-slug',
      attributes: { name: 'slug', displayName: 'Slug', active: true },
    },
  ],
}

const getMock = vi.fn()
const updateMock = vi.fn()
vi.mock('../../context/ApiContext', () => ({
  useApi: () => ({
    apiClient: { get: getMock },
    keltaClient: { admin: { collections: { update: updateMock, create: vi.fn() } } },
  }),
}))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter initialEntries={['/t/collections/col-123/edit']}>
          <CollectionFormPage />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>
  )
}

describe('CollectionFormPage (edit mode)', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
    getMock.mockReset().mockResolvedValue(getResponse)
    updateMock.mockReset().mockResolvedValue({ id: 'col-123' })
  })

  it('maps the displayFieldId relationship into the Display Field select', async () => {
    renderPage()
    const select = (await screen.findByTestId(
      'collection-display-field-select'
    )) as HTMLSelectElement
    // Read fix: the saved relationship (field-name) is reflected, not reset to "Auto-detect".
    expect(select.value).toBe('field-name')
    // i18n keys now exist — the label renders text, not the raw key.
    expect(screen.getByText('Display Field')).toBeInTheDocument()
  })

  it('includes displayName, active and displayFieldId in the update payload', async () => {
    renderPage()
    const displayName = (await screen.findByTestId(
      'collection-display-name-input'
    )) as HTMLInputElement
    fireEvent.change(displayName, { target: { value: 'Providers' } })

    fireEvent.click(screen.getByTestId('collection-form-submit'))

    await waitFor(() => expect(updateMock).toHaveBeenCalledTimes(1))
    const [calledId, payload] = updateMock.mock.calls[0]
    expect(calledId).toBe('col-123')
    expect(payload).toMatchObject({
      name: 'providers',
      displayName: 'Providers',
      active: true,
      displayFieldId: 'field-name',
    })
  })
})
