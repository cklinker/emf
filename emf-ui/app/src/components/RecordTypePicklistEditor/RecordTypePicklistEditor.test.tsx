/**
 * RecordTypePicklistEditor Component Tests
 *
 * Tests for the RecordTypePicklistEditor modal component that allows
 * managing picklist value overrides per record type.
 */

import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RecordTypePicklistEditor } from './RecordTypePicklistEditor'
import type { RecordType, FieldDefinition } from '../../types/collections'

// Track mock API calls
const mockShowToast = vi.fn()

// Mock I18nContext
vi.mock('../../context/I18nContext', () => ({
  useI18n: vi.fn(() => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string, params?: Record<string, string | number>) => {
      const translations: Record<string, string> = {
        'recordTypes.picklistOverrides': 'Picklist Overrides',
        'recordTypes.noPicklistFields': 'This collection has no picklist fields to configure.',
        'recordTypes.allValuesAvailable': 'All values available',
        'recordTypes.restrictedValues': `${params?.count ?? '?'} of ${params?.total ?? '?'} values available`,
        'recordTypes.defaultValue': 'Default Value',
        'recordTypes.noDefault': 'No default',
        'recordTypes.overrideSaved': 'Picklist overrides saved successfully',
        'recordTypes.picklists': 'Picklists',
        'common.close': 'Close',
        'common.cancel': 'Cancel',
        'common.save': 'Save',
        'common.saving': 'Saving...',
        'common.loading': 'Loading...',
        'common.selectAll': 'Select All',
        'common.deselectAll': 'Deselect All',
        'picklistDependencies.noValuesForField': 'No values for this field',
        'errors.generic': 'An error occurred',
      }
      return translations[key] || key
    },
    formatDate: vi.fn(),
    formatNumber: vi.fn(),
    direction: 'ltr' as const,
  })),
}))

// Mock ApiContext
vi.mock('../../context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: vi.fn().mockResolvedValue([]),
      put: vi.fn().mockResolvedValue({}),
      delete: vi.fn().mockResolvedValue(undefined),
      post: vi.fn(),
    },
  })),
}))

// Mock Toast
vi.mock('../Toast', () => ({
  useToast: vi.fn(() => ({
    showToast: mockShowToast,
  })),
}))

// Default mock data for useQuery
const mockOverrides: unknown[] = []
const mockFieldValues: Record<string, unknown[]> = {
  'field-pl-1': [
    { value: 'open', label: 'Open', isDefault: false, active: true, sortOrder: 0 },
    {
      value: 'closed',
      label: 'Closed',
      isDefault: false,
      active: true,
      sortOrder: 1,
    },
    {
      value: 'pending',
      label: 'Pending',
      isDefault: false,
      active: true,
      sortOrder: 2,
    },
  ],
  'field-pl-2': [
    { value: 'bug', label: 'Bug', isDefault: false, active: true, sortOrder: 0 },
    {
      value: 'feature',
      label: 'Feature',
      isDefault: false,
      active: true,
      sortOrder: 1,
    },
  ],
}

// Mock react-query
vi.mock('@tanstack/react-query', () => ({
  useQuery: vi.fn(
    (opts: { queryKey: string[]; queryFn: () => Promise<unknown>; enabled?: boolean }) => {
      if (opts.enabled === false) {
        return { data: undefined, isLoading: false, error: null }
      }
      const key = opts.queryKey[0]
      if (key === 'record-type-picklist-overrides') {
        return { data: mockOverrides, isLoading: false, error: null }
      }
      if (key === 'picklist-field-values') {
        return { data: mockFieldValues, isLoading: false, error: null }
      }
      return { data: undefined, isLoading: false, error: null }
    }
  ),
  useQueryClient: vi.fn(() => ({
    invalidateQueries: vi.fn(),
  })),
}))

// Test data
const mockRecordType: RecordType = {
  id: 'rt-001',
  collectionId: 'col-001',
  name: 'Standard',
  description: 'Standard record type',
  active: true,
  isDefault: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
}

const mockPicklistField: FieldDefinition = {
  id: 'field-pl-1',
  name: 'status',
  displayName: 'Status',
  type: 'picklist',
  required: false,
  unique: false,
  indexed: false,
  order: 0,
}

const mockMultiPicklistField: FieldDefinition = {
  id: 'field-pl-2',
  name: 'tags',
  displayName: 'Tags',
  type: 'multi_picklist',
  required: false,
  unique: false,
  indexed: false,
  order: 1,
}

const mockStringField: FieldDefinition = {
  id: 'field-str-1',
  name: 'name',
  displayName: 'Name',
  type: 'string',
  required: true,
  unique: false,
  indexed: false,
  order: 2,
}

const defaultProps = {
  collectionId: 'col-001',
  recordType: mockRecordType,
  fields: [mockPicklistField, mockMultiPicklistField, mockStringField],
  onClose: vi.fn(),
  onSaved: vi.fn(),
}

describe('RecordTypePicklistEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('should render modal with record type name in header', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      expect(screen.getByTestId('picklist-override-modal')).toBeInTheDocument()
      expect(screen.getByText(/Picklist Overrides/)).toBeInTheDocument()
      expect(screen.getByText(/Standard/)).toBeInTheDocument()
    })

    it('should render overlay', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      expect(screen.getByTestId('picklist-override-overlay')).toBeInTheDocument()
    })

    it('should render close button', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      expect(screen.getByTestId('picklist-override-close')).toBeInTheDocument()
    })

    it('should render cancel and save buttons', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      expect(screen.getByTestId('picklist-override-cancel')).toBeInTheDocument()
      expect(screen.getByTestId('picklist-override-save')).toBeInTheDocument()
    })

    it('should show empty state when no picklist fields exist', () => {
      render(<RecordTypePicklistEditor {...defaultProps} fields={[mockStringField]} />)

      expect(screen.getByTestId('no-picklist-fields')).toBeInTheDocument()
      expect(
        screen.getByText('This collection has no picklist fields to configure.')
      ).toBeInTheDocument()
    })

    it('should render field sections for picklist fields only', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      // Should render sections for picklist fields
      expect(screen.getByTestId('field-section-field-pl-1')).toBeInTheDocument()
      expect(screen.getByTestId('field-section-field-pl-2')).toBeInTheDocument()
      // Should not render a section for the string field
      expect(screen.queryByTestId('field-section-field-str-1')).not.toBeInTheDocument()
    })

    it('should display field names in section headers', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      expect(screen.getByText('Status')).toBeInTheDocument()
      expect(screen.getByText('Tags')).toBeInTheDocument()
    })

    it('should show "All values available" badge when no override exists', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      const badges = screen.getAllByText('All values available')
      expect(badges.length).toBeGreaterThan(0)
    })
  })

  describe('Interactions', () => {
    it('should call onClose when close button is clicked', () => {
      const onClose = vi.fn()
      render(<RecordTypePicklistEditor {...defaultProps} onClose={onClose} />)

      fireEvent.click(screen.getByTestId('picklist-override-close'))

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should call onClose when cancel button is clicked', () => {
      const onClose = vi.fn()
      render(<RecordTypePicklistEditor {...defaultProps} onClose={onClose} />)

      fireEvent.click(screen.getByTestId('picklist-override-cancel'))

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should call onClose when overlay is clicked', () => {
      const onClose = vi.fn()
      render(<RecordTypePicklistEditor {...defaultProps} onClose={onClose} />)

      fireEvent.mouseDown(screen.getByTestId('picklist-override-overlay'))

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should not call onClose when modal content is clicked', () => {
      const onClose = vi.fn()
      render(<RecordTypePicklistEditor {...defaultProps} onClose={onClose} />)

      fireEvent.mouseDown(screen.getByTestId('picklist-override-modal'))

      expect(onClose).not.toHaveBeenCalled()
    })

    it('should call onClose when Escape key is pressed', () => {
      const onClose = vi.fn()
      render(<RecordTypePicklistEditor {...defaultProps} onClose={onClose} />)

      fireEvent.keyDown(screen.getByTestId('picklist-override-overlay'), { key: 'Escape' })

      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })

  describe('Collapsible Sections', () => {
    it('should toggle field section collapse on header click', async () => {
      const user = userEvent.setup()
      render(<RecordTypePicklistEditor {...defaultProps} />)

      const header = screen.getByTestId('field-header-field-pl-1')

      // Initially expanded
      expect(header).toHaveAttribute('aria-expanded', 'true')

      // Click to collapse
      await user.click(header)
      expect(header).toHaveAttribute('aria-expanded', 'false')

      // Click to expand again
      await user.click(header)
      expect(header).toHaveAttribute('aria-expanded', 'true')
    })
  })

  describe('Accessibility', () => {
    it('should have dialog role with aria-modal', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      const modal = screen.getByTestId('picklist-override-modal')
      expect(modal).toHaveAttribute('role', 'dialog')
      expect(modal).toHaveAttribute('aria-modal', 'true')
    })

    it('should have aria-labelledby pointing to title', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      const modal = screen.getByTestId('picklist-override-modal')
      expect(modal).toHaveAttribute('aria-labelledby', 'picklist-override-title')
    })

    it('should have close button with aria-label', () => {
      render(<RecordTypePicklistEditor {...defaultProps} />)

      const closeBtn = screen.getByTestId('picklist-override-close')
      expect(closeBtn).toHaveAttribute('aria-label', 'Close')
    })
  })
})
