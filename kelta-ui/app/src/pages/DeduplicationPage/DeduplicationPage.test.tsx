/**
 * DeduplicationPage Tests
 *
 * Covers the detect → pick-master → merge flow:
 * - loads collections and their fields into the pickers
 * - scans for duplicates via POST /api/collections/{name}/duplicates
 * - renders duplicate groups and requires a master before merging
 * - merges via POST /api/collections/{name}/merge with the non-master ids, behind a confirm dialog
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, beforeEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { DeduplicationPage } from './DeduplicationPage'

// The collections *list* (no fields embedded — matches the real endpoint).
const collectionsListDoc = {
  data: [
    { type: 'collections', id: 'c1', attributes: { name: 'contacts', displayName: 'Contacts' } },
  ],
  metadata: { totalCount: 1 },
}

// The single-collection schema (`?include=fields`) that populates the match-field picker.
const collectionSchemaDoc = {
  data: {
    type: 'collections',
    id: 'c1',
    attributes: { name: 'contacts', displayName: 'Contacts' },
  },
  included: [
    {
      type: 'fields',
      id: 'f0',
      attributes: { name: 'id', type: 'STRING', active: true, fieldOrder: 0 },
    },
    {
      type: 'fields',
      id: 'f1',
      attributes: {
        name: 'email',
        displayName: 'Email',
        type: 'EMAIL',
        active: true,
        fieldOrder: 1,
      },
    },
    {
      type: 'fields',
      id: 'f2',
      attributes: {
        name: 'name',
        displayName: 'Name',
        type: 'STRING',
        active: true,
        fieldOrder: 2,
      },
    },
  ],
}

const duplicatesBody = {
  collectionName: 'contacts',
  matchFields: ['email'],
  scanned: 3,
  truncated: false,
  groups: [{ values: { email: 'a@x.com' }, count: 2, recordIds: ['r1', 'r2'] }],
}

const mergeBody = {
  masterId: 'r1',
  deletedIds: ['r2'],
  reparentedRecords: 1,
  reparented: [{ collection: 'orders', field: 'customer', count: 1 }],
}

describe('DeduplicationPage', () => {
  beforeEach(() => {
    resetMockAxios()
    setupAuthMocks()
    mockAxios.get.mockImplementation((url: string) => {
      // Schema fetch (?include=fields) → single resource + included fields; else the list.
      if (url.includes('include=fields')) return Promise.resolve({ data: collectionSchemaDoc })
      return Promise.resolve({ data: collectionsListDoc })
    })
    mockAxios.post.mockImplementation((url: string) => {
      if (url.endsWith('/duplicates')) return Promise.resolve({ data: duplicatesBody })
      if (url.endsWith('/merge')) return Promise.resolve({ data: mergeBody })
      return Promise.reject(new Error(`unexpected POST ${url}`))
    })
  })

  it('detects a group and merges the non-master duplicates into the chosen master', async () => {
    const user = userEvent.setup()
    render(<DeduplicationPage />, { wrapper: createTestWrapper() })

    // Collection picker populated from the collections list.
    await waitFor(() => expect(screen.getByTestId('dedup-collection-select')).toBeInTheDocument())
    await user.selectOptions(screen.getByTestId('dedup-collection-select'), 'contacts')

    // Match-field chips load from the collection schema; scan is disabled until one is chosen.
    expect(screen.getByTestId('dedup-scan-button')).toBeDisabled()
    await user.click(await screen.findByTestId('dedup-field-email'))
    expect(screen.getByTestId('dedup-scan-button')).toBeEnabled()

    // Scan → the duplicate group renders.
    await user.click(screen.getByTestId('dedup-scan-button'))
    await waitFor(() => expect(screen.getByTestId('dedup-group-r1')).toBeInTheDocument())
    expect(mockAxios.post).toHaveBeenCalledWith('/api/collections/contacts/duplicates', {
      matchFields: ['email'],
      limit: 100,
    })

    // Pick a master, then merge → a confirm dialog gates the destructive action.
    await user.click(screen.getByTestId('dedup-master-r1'))
    await user.click(screen.getByTestId('dedup-merge-r1'))
    const confirm = await screen.findByRole('button', { name: 'Merge' })
    await user.click(confirm)

    // Merge posts only the non-master ids.
    await waitFor(() =>
      expect(mockAxios.post).toHaveBeenCalledWith('/api/collections/contacts/merge', {
        masterId: 'r1',
        duplicateIds: ['r2'],
      })
    )
  })

  it('does not open the merge dialog until a master is chosen', async () => {
    const user = userEvent.setup()
    render(<DeduplicationPage />, { wrapper: createTestWrapper() })

    await waitFor(() => expect(screen.getByTestId('dedup-collection-select')).toBeInTheDocument())
    await user.selectOptions(screen.getByTestId('dedup-collection-select'), 'contacts')
    await user.click(await screen.findByTestId('dedup-field-email'))
    await user.click(screen.getByTestId('dedup-scan-button'))
    await waitFor(() => expect(screen.getByTestId('dedup-group-r1')).toBeInTheDocument())

    // Merge button is disabled with no master selected; clicking never posts a merge.
    expect(screen.getByTestId('dedup-merge-r1')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Merge' })).not.toBeInTheDocument()
    expect(mockAxios.post).not.toHaveBeenCalledWith(
      '/api/collections/contacts/merge',
      expect.anything()
    )
  })
})
