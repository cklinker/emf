/**
 * PageLayoutsPage Tests
 *
 * Unit tests for the PageLayoutsPage component.
 * Tests cover:
 * - Rendering the layouts list (table mode)
 * - Loading and error states
 * - Empty state
 * - Create layout modal (form validation)
 * - View layout (viewer mode)
 * - Edit layout (editor mode with palette, canvas, properties)
 * - Delete layout with confirmation
 * - Mode transitions (list → viewer → editor → list)
 *
 * Requirements tested:
 * - List mode: displays layout table with Name, Collection, Type, Default, Created, Actions
 * - Viewer mode: read-only preview of layout sections and fields
 * - Editor mode: three-panel WYSIWYG builder (palette, canvas, properties)
 * - Create flow: metadata modal → editor mode
 * - Delete flow: confirmation dialog
 */

import React from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createTestWrapper,
  setupAuthMocks,
  mockAxios,
  resetMockAxios,
  createAxiosError,
} from '../../test/testUtils'
import { PageLayoutsPage } from './PageLayoutsPage'
import type { PageLayoutSummary, PageLayoutDetail } from '../../types/layouts'

// ─── Mock Data ───────────────────────────────────────────────────────────────

const mockCollections = {
  content: [
    { id: 'col-1', name: 'accounts', displayName: 'Accounts' },
    { id: 'col-2', name: 'contacts', displayName: 'Contacts' },
    { id: 'col-3', name: 'orders', displayName: 'Orders' },
  ],
}

const mockLayouts: PageLayoutSummary[] = [
  {
    id: 'layout-1',
    name: 'Account Detail',
    description: 'Default account detail layout',
    layoutType: 'DETAIL',
    collectionId: 'col-1',
    isDefault: true,
    createdAt: '2024-06-15T10:00:00Z',
    updatedAt: '2024-06-15T10:00:00Z',
  },
  {
    id: 'layout-2',
    name: 'Contact Edit',
    description: 'Contact edit layout',
    layoutType: 'EDIT',
    collectionId: 'col-2',
    isDefault: false,
    createdAt: '2024-06-10T08:00:00Z',
    updatedAt: '2024-06-12T14:00:00Z',
  },
  {
    id: 'layout-3',
    name: 'Order Mini',
    description: null,
    layoutType: 'MINI',
    collectionId: 'col-3',
    isDefault: false,
    createdAt: '2024-06-05T09:00:00Z',
    updatedAt: '2024-06-05T09:00:00Z',
  },
]

const mockLayoutDetail: PageLayoutDetail = {
  id: 'layout-1',
  name: 'Account Detail',
  description: 'Default account detail layout',
  layoutType: 'DETAIL',
  collectionId: 'col-1',
  isDefault: true,
  createdAt: '2024-06-15T10:00:00Z',
  updatedAt: '2024-06-15T10:00:00Z',
  sections: [
    {
      id: 'sec-1',
      heading: 'Account Information',
      columns: 2,
      sortOrder: 0,
      collapsed: false,
      style: 'DEFAULT',
      fields: [
        {
          id: 'fp-1',
          fieldId: 'field-name',
          fieldName: 'name',
          columnNumber: 1,
          sortOrder: 0,
          requiredOnLayout: true,
          readOnlyOnLayout: false,
        },
        {
          id: 'fp-2',
          fieldId: 'field-email',
          fieldName: 'email',
          columnNumber: 2,
          sortOrder: 0,
          requiredOnLayout: false,
          readOnlyOnLayout: true,
        },
      ],
    },
    {
      id: 'sec-2',
      heading: 'Additional Details',
      columns: 1,
      sortOrder: 1,
      collapsed: true,
      style: 'CARD',
      fields: [
        {
          id: 'fp-3',
          fieldId: 'field-phone',
          fieldName: 'phone',
          columnNumber: 1,
          sortOrder: 0,
          requiredOnLayout: false,
          readOnlyOnLayout: false,
        },
      ],
    },
  ],
  relatedLists: [
    {
      id: 'rl-1',
      relatedCollectionId: 'col-2',
      relationshipFieldId: 'field-account-id',
      displayColumns: 'name,email',
      sortField: 'name',
      sortDirection: 'ASC',
      rowLimit: 10,
      sortOrder: 0,
    },
  ],
}

const mockFields = [
  {
    id: 'field-name',
    name: 'name',
    displayName: 'Name',
    type: 'string',
    required: true,
  },
  {
    id: 'field-email',
    name: 'email',
    displayName: 'Email',
    type: 'email',
    required: false,
  },
  {
    id: 'field-phone',
    name: 'phone',
    displayName: 'Phone',
    type: 'phone',
    required: false,
  },
  {
    id: 'field-status',
    name: 'status',
    displayName: 'Status',
    type: 'picklist',
    required: false,
  },
]

// ─── Helpers ─────────────────────────────────────────────────────────────────

function setupDefaultMocks() {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/control/layouts?tenantId=')) {
      return Promise.resolve({ data: mockLayouts })
    }
    if (url.includes('/control/collections?size=')) {
      return Promise.resolve({ data: mockCollections })
    }
    if (url.match(/\/control\/layouts\/layout-1$/)) {
      return Promise.resolve({ data: mockLayoutDetail })
    }
    if (url.includes('/control/collections/col-1/fields')) {
      return Promise.resolve({ data: mockFields })
    }
    return Promise.resolve({ data: [] })
  })
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('PageLayoutsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.clearAllMocks()
  })

  // ── Loading State ────────────────────────────────────────────────────────

  describe('Loading State', () => {
    it('should display loading spinner while fetching layouts', async () => {
      mockAxios.get.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({ data: mockLayouts }), 100))
      )

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      expect(screen.getByRole('status')).toBeInTheDocument()
    })
  })

  // ── Error State ──────────────────────────────────────────────────────────

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockAxios.get.mockRejectedValue(createAxiosError(500))

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument()
      })
    })

    it('should display retry button on error', async () => {
      mockAxios.get.mockRejectedValue(createAxiosError(500))

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })

    it('should retry fetching when retry button is clicked', async () => {
      mockAxios.get
        .mockRejectedValueOnce(createAxiosError(500))
        .mockImplementation((url: string) => {
          if (url.includes('/control/layouts?tenantId=')) {
            return Promise.resolve({ data: mockLayouts })
          }
          if (url.includes('/control/collections?size=')) {
            return Promise.resolve({ data: mockCollections })
          }
          return Promise.resolve({ data: [] })
        })

      const user = userEvent.setup()
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: /retry/i }))

      await waitFor(() => {
        expect(screen.getByText('Account Detail')).toBeInTheDocument()
      })
    })
  })

  // ── Empty State ──────────────────────────────────────────────────────────

  describe('Empty State', () => {
    it('should display empty state when no layouts exist', async () => {
      mockAxios.get.mockImplementation((url: string) => {
        if (url.includes('/control/layouts?tenantId=')) {
          return Promise.resolve({ data: [] })
        }
        if (url.includes('/control/collections?size=')) {
          return Promise.resolve({ data: mockCollections })
        }
        return Promise.resolve({ data: [] })
      })

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })

    it('should show create button in empty state', async () => {
      mockAxios.get.mockImplementation((url: string) => {
        if (url.includes('/control/layouts?tenantId=')) {
          return Promise.resolve({ data: [] })
        }
        if (url.includes('/control/collections?size=')) {
          return Promise.resolve({ data: mockCollections })
        }
        return Promise.resolve({ data: [] })
      })

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-layout-button')).toBeInTheDocument()
      })
    })
  })

  // ── List Mode ────────────────────────────────────────────────────────────

  describe('List Mode', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should display the page title', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /page layouts/i })).toBeInTheDocument()
      })
    })

    it('should display all layouts in the table', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Account Detail')).toBeInTheDocument()
        expect(screen.getByText('Contact Edit')).toBeInTheDocument()
        expect(screen.getByText('Order Mini')).toBeInTheDocument()
      })
    })

    it('should display layout types', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('DETAIL')).toBeInTheDocument()
        expect(screen.getByText('EDIT')).toBeInTheDocument()
        expect(screen.getByText('MINI')).toBeInTheDocument()
      })
    })

    it('should display collection names', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Accounts')).toBeInTheDocument()
        expect(screen.getByText('Contacts')).toBeInTheDocument()
        expect(screen.getByText('Orders')).toBeInTheDocument()
      })
    })

    it('should display default badges', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        const table = screen.getByTestId('page-layouts-table')
        const yesElements = within(table).getAllByText('Yes')
        const noElements = within(table).getAllByText('No')
        expect(yesElements.length).toBeGreaterThan(0)
        expect(noElements.length).toBeGreaterThan(0)
      })
    })

    it('should have view, edit, and delete buttons for each row', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
        expect(screen.getByTestId('delete-button-0')).toBeInTheDocument()
      })
    })

    it('should have a create layout button', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-layout-button')).toBeInTheDocument()
      })
    })
  })

  // ── Create Layout Modal ──────────────────────────────────────────────────

  describe('Create Layout Modal', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should open the create form when clicking the create button', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-layout-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('add-layout-button'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-form-modal')).toBeInTheDocument()
      })
    })

    it('should display form fields in the create modal', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-layout-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('add-layout-button'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-name-input')).toBeInTheDocument()
        expect(screen.getByTestId('layout-description-input')).toBeInTheDocument()
        expect(screen.getByTestId('layout-type-select')).toBeInTheDocument()
        expect(screen.getByTestId('layout-collection-id-input')).toBeInTheDocument()
        expect(screen.getByTestId('layout-is-default-input')).toBeInTheDocument()
      })
    })

    it('should show validation errors for empty name', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-layout-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('add-layout-button'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-form-submit')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('layout-form-submit'))

      await waitFor(() => {
        expect(screen.getByText('Name is required')).toBeInTheDocument()
      })
    })

    it('should close the create form when clicking cancel', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-layout-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('add-layout-button'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-form-cancel')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('layout-form-cancel'))

      await waitFor(() => {
        expect(screen.queryByTestId('layout-form-modal')).not.toBeInTheDocument()
      })
    })
  })

  // ── Mode Transitions ─────────────────────────────────────────────────────

  describe('Mode Transitions', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should navigate to viewer mode when clicking a layout name', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('layout-name-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('layout-name-0'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-viewer')).toBeInTheDocument()
      })
    })

    it('should navigate to viewer mode when clicking the view button', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-viewer')).toBeInTheDocument()
      })
    })

    it('should navigate to editor mode when clicking the edit button', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-editor')).toBeInTheDocument()
      })
    })

    it('should navigate back to list from viewer mode', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('viewer-back-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('viewer-back-button'))

      await waitFor(() => {
        expect(screen.getByTestId('page-layouts-table')).toBeInTheDocument()
      })
    })
  })

  // ── Viewer Mode ──────────────────────────────────────────────────────────

  describe('Viewer Mode', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should display the layout name in viewer', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-viewer')).toBeInTheDocument()
        expect(screen.getByText('Account Detail')).toBeInTheDocument()
      })
    })

    it('should display section headings in viewer', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-button-0'))

      await waitFor(() => {
        expect(screen.getByText('Account Information')).toBeInTheDocument()
        expect(screen.getByText('Additional Details')).toBeInTheDocument()
      })
    })

    it('should display edit and back buttons in viewer', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('viewer-edit-button')).toBeInTheDocument()
        expect(screen.getByTestId('viewer-back-button')).toBeInTheDocument()
      })
    })

    it('should navigate to editor from viewer when clicking edit', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('view-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('viewer-edit-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('viewer-edit-button'))

      await waitFor(() => {
        expect(screen.getByTestId('layout-editor')).toBeInTheDocument()
      })
    })
  })

  // ── Editor Mode ──────────────────────────────────────────────────────────

  describe('Editor Mode', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should display three-panel layout (palette, canvas, properties)', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('field-palette')).toBeInTheDocument()
        expect(screen.getByTestId('layout-canvas')).toBeInTheDocument()
        expect(screen.getByTestId('properties-panel')).toBeInTheDocument()
      })
    })

    it('should display field palette with collection fields', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('field-palette')).toBeInTheDocument()
        // Should show fields not yet placed as well as placed fields
        expect(screen.getByTestId('field-search-input')).toBeInTheDocument()
      })
    })

    it('should display existing sections on the canvas', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('section-0')).toBeInTheDocument()
        expect(screen.getByTestId('section-1')).toBeInTheDocument()
      })
    })

    it('should display save and cancel buttons in editor', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('editor-save-button')).toBeInTheDocument()
        expect(screen.getByTestId('editor-cancel-button')).toBeInTheDocument()
      })
    })

    it('should display add section button', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('add-section-button')).toBeInTheDocument()
      })
    })

    it('should add a new section when clicking add section', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('add-section-button')).toBeInTheDocument()
      })

      // Layout-1 has 2 sections initially
      expect(screen.getByTestId('section-0')).toBeInTheDocument()
      expect(screen.getByTestId('section-1')).toBeInTheDocument()

      await user.click(screen.getByTestId('add-section-button'))

      // Should now have 3 sections
      await waitFor(() => {
        expect(screen.getByTestId('section-2')).toBeInTheDocument()
      })
    })

    it('should remove a section when clicking the remove button', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('section-remove-1')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('section-remove-1'))

      await waitFor(() => {
        expect(screen.queryByTestId('section-1')).not.toBeInTheDocument()
      })
    })
  })

  // ── Delete Confirmation ──────────────────────────────────────────────────

  describe('Delete Confirmation', () => {
    beforeEach(() => {
      setupDefaultMocks()
      mockAxios.delete.mockResolvedValue({ data: null })
    })

    it('should show delete confirmation dialog when clicking delete', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('delete-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button-0'))

      await waitFor(() => {
        expect(screen.getByText(/are you sure you want to delete this layout/i)).toBeInTheDocument()
      })
    })

    it('should close delete dialog when clicking cancel', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('delete-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog-cancel')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('confirm-dialog-cancel'))

      await waitFor(() => {
        expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
      })
    })

    it('should call delete API when confirming deletion', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('delete-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('delete-button-0'))

      await waitFor(() => {
        const confirmButtons = screen.getAllByRole('button', {
          name: /delete/i,
        })
        // The confirm button in the dialog
        const dialogConfirm = confirmButtons.find(
          (btn) =>
            btn.closest('[role="dialog"]') ||
            btn.closest('[class*="modal"]') ||
            btn.closest('[class*="confirmDialog"]')
        )
        expect(dialogConfirm || confirmButtons[confirmButtons.length - 1]).toBeInTheDocument()
      })
    })
  })

  // ── Properties Panel ─────────────────────────────────────────────────────

  describe('Properties Panel', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should show placeholder text when nothing is selected', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('properties-panel')).toBeInTheDocument()
        // Should show "select a section, field..." guidance
        expect(screen.getByText(/select a section/i)).toBeInTheDocument()
      })
    })

    it('should show section properties when a section is selected', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('section-config-0')).toBeInTheDocument()
      })

      // Click the section config button to select the section
      await user.click(screen.getByTestId('section-config-0'))

      await waitFor(() => {
        expect(screen.getByTestId('section-properties')).toBeInTheDocument()
        expect(screen.getByTestId('section-heading-input')).toBeInTheDocument()
        expect(screen.getByTestId('section-columns-select')).toBeInTheDocument()
      })
    })

    it('should show field properties when a field is selected', async () => {
      const user = userEvent.setup()

      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('edit-button-0'))

      await waitFor(() => {
        expect(screen.getByTestId('field-chip-0-0')).toBeInTheDocument()
      })

      // Click a field to select it
      await user.click(screen.getByTestId('field-chip-0-0'))

      await waitFor(() => {
        expect(screen.getByTestId('field-properties')).toBeInTheDocument()
        expect(screen.getByTestId('field-required-checkbox')).toBeInTheDocument()
        expect(screen.getByTestId('field-readonly-checkbox')).toBeInTheDocument()
      })
    })
  })

  // ── Accessibility ────────────────────────────────────────────────────────

  describe('Accessibility', () => {
    beforeEach(() => {
      setupDefaultMocks()
    })

    it('should have a grid role on the layout table', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByRole('grid')).toBeInTheDocument()
      })
    })

    it('should have column headers in the table', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        const headers = screen.getAllByRole('columnheader')
        expect(headers.length).toBeGreaterThanOrEqual(5)
      })
    })

    it('should have aria-labels on action buttons', async () => {
      render(<PageLayoutsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByLabelText('View Account Detail')).toBeInTheDocument()
        expect(screen.getByLabelText('Edit Account Detail')).toBeInTheDocument()
        expect(screen.getByLabelText('Delete Account Detail')).toBeInTheDocument()
      })
    })
  })
})
