/**
 * Save round-trip mutation-payload test (slice 2d, correction owned with 2c). After authoring page
 * variables + data sources in the page-settings drawer, the save call's `updateMutation.mutate` BODY
 * must carry `variables` and `dataSources` (plus `components`/`schemaVersion` from 2c) — proving 2d's
 * keys are in the passed set and not silently dropped by `mergeConfig`.
 */
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { PageBuilderPage } from './PageBuilderPage'
import type { UIPage } from './PageBuilderPage'

const page: UIPage = {
  id: '1',
  name: 'dashboard',
  path: '/dashboard',
  title: 'Dashboard',
  layout: { type: 'single' },
  components: [],
  published: false,
  createdAt: '2024-01-15T10:00:00Z',
  updatedAt: '2024-01-15T10:00:00Z',
}

interface SavedConfig {
  schemaVersion?: number
  components?: Array<{ type: string }>
  variables?: Array<{ name: string; type: string }>
  dataSources?: Array<{ name: string; collection: string }>
}

describe('PageBuilderPage — save payload (variables/dataSources)', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    mockAxios.get.mockImplementation((url: string) => {
      if (url.includes('/api/ui-pages/1')) return Promise.resolve({ data: page })
      return Promise.resolve({ data: [page] })
    })
    mockAxios.patch.mockResolvedValue({ data: page })
  })

  afterEach(() => {
    cleanupAuthMocks()
    resetMockAxios()
    vi.clearAllMocks()
  })

  it('persists drawer-authored variables and dataSources in the save payload', async () => {
    const user = userEvent.setup()
    render(<PageBuilderPage />, { wrapper: createTestWrapper() })

    await waitFor(() => expect(screen.getByText('dashboard')).toBeInTheDocument())
    await user.click(screen.getByTestId('page-name-0'))
    await waitFor(() => expect(screen.getByTestId('page-settings-button')).toBeInTheDocument())

    // Open the page-settings drawer and author one variable + one data source.
    await user.click(screen.getByTestId('page-settings-button'))
    await waitFor(() => expect(screen.getByTestId('page-settings-drawer')).toBeInTheDocument())

    await user.click(screen.getByTestId('add-variable-button'))
    await user.type(screen.getByTestId('variable-name-0'), 'count')

    await user.click(screen.getByTestId('add-data-source-button'))
    await user.type(screen.getByTestId('data-source-name-0'), 'accounts')
    await user.type(screen.getByTestId('data-source-collection-0'), 'accounts')

    // Close the drawer (its overlay blocks pointer events on the toolbar) before saving.
    await user.keyboard('{Escape}')
    await waitFor(() =>
      expect(screen.queryByTestId('page-settings-drawer')).not.toBeInTheDocument()
    )

    await waitFor(() => expect(screen.getByTestId('save-page-button')).not.toBeDisabled())
    await user.click(screen.getByTestId('save-page-button'))

    await waitFor(() => expect(mockAxios.patch).toHaveBeenCalled())
    const body = mockAxios.patch.mock.calls[0][1] as {
      data: { attributes: { config: SavedConfig } }
    }
    const config = body.data.attributes.config
    expect(config.schemaVersion).toBe(2)
    expect(config.variables).toEqual([{ name: 'count', type: 'string', default: '' }])
    expect(config.dataSources?.[0]).toMatchObject({ name: 'accounts', collection: 'accounts' })
  })
})
