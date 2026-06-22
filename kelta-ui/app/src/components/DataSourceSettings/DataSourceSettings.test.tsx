/**
 * DataSourceSettings tests — configure an external connector on a collection (Rec 4 slice 4e).
 * API calls flow through the SDK's mocked Axios instance (testUtils.mockAxios).
 */
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { DataSourceSettings } from './DataSourceSettings'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'

function collectionResponse(adapterConfig: Record<string, string> | null) {
  return {
    data: {
      data: { type: 'collections', id: 'c1', attributes: { name: 'orders', adapterConfig } },
    },
  }
}

describe('DataSourceSettings', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    mockAxios.patch.mockResolvedValue({
      data: { data: { type: 'collections', id: 'c1', attributes: {} } },
    })
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  it('defaults to physical table when no adapter config is set', async () => {
    mockAxios.get.mockResolvedValue(collectionResponse(null))
    render(<DataSourceSettings collectionId="c1" />, { wrapper: createTestWrapper() })

    const select = await screen.findByTestId('adapter-type-select')
    expect(select).toHaveValue('physical-table')
    // No connector fields shown for the physical default.
    expect(screen.queryByTestId('ds-field-baseUrl')).not.toBeInTheDocument()
  })

  it('reveals REST fields and requires a base URL before saving', async () => {
    mockAxios.get.mockResolvedValue(collectionResponse(null))
    render(<DataSourceSettings collectionId="c1" />, { wrapper: createTestWrapper() })

    const select = await screen.findByTestId('adapter-type-select')
    await userEvent.selectOptions(select, 'external-rest')

    expect(screen.getByTestId('ds-field-baseUrl')).toBeInTheDocument()
    expect(screen.getByTestId('data-source-save')).toBeDisabled()

    await userEvent.type(screen.getByTestId('ds-field-baseUrl'), 'https://api.test')
    expect(screen.getByTestId('data-source-save')).toBeEnabled()
  })

  it('saves a REST connector config (adapterType + non-blank fields only)', async () => {
    mockAxios.get.mockResolvedValue(collectionResponse(null))
    render(<DataSourceSettings collectionId="c1" />, { wrapper: createTestWrapper() })

    const select = await screen.findByTestId('adapter-type-select')
    await userEvent.selectOptions(select, 'external-rest')
    await userEvent.type(screen.getByTestId('ds-field-baseUrl'), 'https://api.test')
    await userEvent.type(screen.getByTestId('ds-field-credentialRef'), 'orders-api')
    await userEvent.click(screen.getByTestId('data-source-save'))

    await waitFor(() => expect(mockAxios.patch).toHaveBeenCalled())
    const [url, body] = mockAxios.patch.mock.calls[0]
    expect(url).toBe('/api/collections/c1')
    expect(body.data.attributes.adapterConfig).toEqual({
      adapterType: 'external-rest',
      baseUrl: 'https://api.test',
      credentialRef: 'orders-api',
    })
  })

  it('preselects and populates an existing external-jdbc config', async () => {
    mockAxios.get.mockResolvedValue(
      collectionResponse({
        adapterType: 'external-jdbc',
        jdbcUrl: 'jdbc:postgresql://h/db',
        table: 'orders',
      })
    )
    render(<DataSourceSettings collectionId="c1" />, { wrapper: createTestWrapper() })

    const select = await screen.findByTestId('adapter-type-select')
    await waitFor(() => expect(select).toHaveValue('external-jdbc'))
    expect(screen.getByTestId('ds-field-jdbcUrl')).toHaveValue('jdbc:postgresql://h/db')
    expect(screen.getByTestId('ds-field-table')).toHaveValue('orders')
  })

  it('clears external config when reverting to physical table', async () => {
    mockAxios.get.mockResolvedValue(
      collectionResponse({ adapterType: 'external-rest', baseUrl: 'https://x' })
    )
    render(<DataSourceSettings collectionId="c1" />, { wrapper: createTestWrapper() })

    const select = await screen.findByTestId('adapter-type-select')
    await waitFor(() => expect(select).toHaveValue('external-rest'))
    await userEvent.selectOptions(select, 'physical-table')
    await userEvent.click(screen.getByTestId('data-source-save'))

    await waitFor(() => expect(mockAxios.patch).toHaveBeenCalled())
    expect(mockAxios.patch.mock.calls[0][1].data.attributes.adapterConfig).toEqual({})
  })
})
