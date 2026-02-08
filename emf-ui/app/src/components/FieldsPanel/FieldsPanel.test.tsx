/**
 * FieldsPanel Component Tests
 *
 * Tests for the FieldsPanel component covering rendering, interactions,
 * drag-and-drop reordering, and accessibility.
 *
 * Requirements tested:
 * - 4.1: Display all active fields in a sortable list
 * - 4.8: Display confirmation dialog before field deletion
 * - 4.9: Mark field as inactive and remove from list on deletion
 * - 4.10: Support drag-and-drop reordering of fields
 */

import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FieldsPanel } from './FieldsPanel'
import type { FieldDefinition, FieldType } from './FieldsPanel'
import { I18nProvider } from '../../context/I18nContext'

// Wrapper component to provide I18n context
function TestWrapper({ children }: { children: React.ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>
}

// Helper to render with I18n context
function renderWithI18n(ui: React.ReactElement) {
  return render(ui, { wrapper: TestWrapper })
}

// Sample field data for testing
const createField = (overrides: Partial<FieldDefinition> = {}): FieldDefinition => ({
  id: 'field-1',
  name: 'test_field',
  displayName: 'Test Field',
  type: 'string' as FieldType,
  required: false,
  unique: false,
  indexed: false,
  order: 0,
  ...overrides,
})

const sampleFields: FieldDefinition[] = [
  createField({
    id: 'field-1',
    name: 'name',
    displayName: 'Name',
    type: 'string',
    required: true,
    order: 0,
  }),
  createField({
    id: 'field-2',
    name: 'email',
    displayName: 'Email',
    type: 'string',
    unique: true,
    order: 1,
  }),
  createField({
    id: 'field-3',
    name: 'age',
    displayName: 'Age',
    type: 'number',
    indexed: true,
    order: 2,
  }),
  createField({
    id: 'field-4',
    name: 'is_active',
    displayName: 'Is Active',
    type: 'boolean',
    order: 3,
  }),
  createField({
    id: 'field-5',
    name: 'created_at',
    displayName: 'Created At',
    type: 'datetime',
    order: 4,
  }),
]

describe('FieldsPanel Component', () => {
  const defaultProps = {
    fields: sampleFields,
    onAdd: vi.fn(),
    onEdit: vi.fn(),
    onDelete: vi.fn(),
    onReorder: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // Clean up body overflow style
    document.body.style.overflow = ''
  })

  describe('Rendering', () => {
    it('should render the fields panel container', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel')).toBeInTheDocument()
    })

    it('should render the header with title', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByText('Fields')).toBeInTheDocument()
    })

    it('should render the add field button', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-add-button')).toBeInTheDocument()
      expect(screen.getByText('Add Field')).toBeInTheDocument()
    })

    it('should render all fields in the list', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-field-1')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-field-field-2')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-field-field-3')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-field-field-4')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-field-field-5')).toBeInTheDocument()
    })

    it('should render fields sorted by order', () => {
      const unorderedFields = [
        createField({ id: 'field-c', name: 'c_field', order: 2 }),
        createField({ id: 'field-a', name: 'a_field', order: 0 }),
        createField({ id: 'field-b', name: 'b_field', order: 1 }),
      ]

      renderWithI18n(<FieldsPanel {...defaultProps} fields={unorderedFields} />)

      const list = screen.getByTestId('fields-panel-list')
      const items = within(list).getAllByRole('listitem')

      expect(items[0]).toHaveAttribute('data-testid', 'fields-panel-field-field-a')
      expect(items[1]).toHaveAttribute('data-testid', 'fields-panel-field-field-b')
      expect(items[2]).toHaveAttribute('data-testid', 'fields-panel-field-field-c')
    })

    it('should render field name', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-name-field-1')).toHaveTextContent('name')
    })

    it('should render field display name when different from name', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-display-name-field-1')).toHaveTextContent(
        '(Name)'
      )
    })

    it('should not render display name when same as name', () => {
      const fields = [createField({ id: 'field-1', name: 'test', displayName: 'test', order: 0 })]
      renderWithI18n(<FieldsPanel {...defaultProps} fields={fields} />)

      expect(
        screen.queryByTestId('fields-panel-field-display-name-field-1')
      ).not.toBeInTheDocument()
    })

    it('should render field type', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-type-field-1')).toHaveTextContent('String')
      expect(screen.getByTestId('fields-panel-field-type-field-3')).toHaveTextContent('Number')
      expect(screen.getByTestId('fields-panel-field-type-field-4')).toHaveTextContent('Boolean')
    })

    it('should render required badge when field is required', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-required-field-1')).toBeInTheDocument()
      expect(screen.queryByTestId('fields-panel-field-required-field-2')).not.toBeInTheDocument()
    })

    it('should render unique badge when field is unique', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-unique-field-2')).toBeInTheDocument()
      expect(screen.queryByTestId('fields-panel-field-unique-field-1')).not.toBeInTheDocument()
    })

    it('should render indexed badge when field is indexed', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-field-indexed-field-3')).toBeInTheDocument()
      expect(screen.queryByTestId('fields-panel-field-indexed-field-1')).not.toBeInTheDocument()
    })

    it('should render reference target for reference fields', () => {
      const fields = [
        createField({
          id: 'field-1',
          name: 'user_id',
          type: 'reference',
          referenceTarget: 'users',
          order: 0,
        }),
      ]
      renderWithI18n(<FieldsPanel {...defaultProps} fields={fields} />)

      expect(screen.getByTestId('fields-panel-field-reference-field-1')).toHaveTextContent(
        '→ users'
      )
    })

    it('should render edit button for each field', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-edit-field-1')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-edit-field-2')).toBeInTheDocument()
    })

    it('should render delete button for each field', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-delete-field-1')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-delete-field-2')).toBeInTheDocument()
    })

    it('should render drag handle for each field', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-drag-handle-field-1')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-drag-handle-field-2')).toBeInTheDocument()
    })

    it('should render with custom testId', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} testId="custom-panel" />)

      expect(screen.getByTestId('custom-panel')).toBeInTheDocument()
    })
  })

  describe('Empty State', () => {
    it('should render empty state when no fields', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} fields={[]} />)

      expect(screen.getByTestId('fields-panel-empty')).toBeInTheDocument()
    })

    it('should display empty message', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} fields={[]} />)

      expect(screen.getByText('No fields defined')).toBeInTheDocument()
    })

    it('should display add field hint in empty state', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} fields={[]} />)

      expect(screen.getByText('Click "Add Field" to create your first field.')).toBeInTheDocument()
    })

    it('should still show add button in empty state', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} fields={[]} />)

      expect(screen.getByTestId('fields-panel-add-button')).toBeInTheDocument()
    })
  })

  describe('Loading State', () => {
    it('should render loading state when isLoading is true', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} isLoading={true} />)

      expect(screen.getByTestId('fields-panel-loading')).toBeInTheDocument()
    })

    it('should show loading spinner', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} isLoading={true} />)

      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument()
    })

    it('should not render field list when loading', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} isLoading={true} />)

      expect(screen.queryByTestId('fields-panel-list')).not.toBeInTheDocument()
    })

    it('should not render add button when loading', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} isLoading={true} />)

      expect(screen.queryByTestId('fields-panel-add-button')).not.toBeInTheDocument()
    })
  })

  describe('Add Field Action', () => {
    it('should call onAdd when add button is clicked', async () => {
      const onAdd = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onAdd={onAdd} />)

      fireEvent.click(screen.getByTestId('fields-panel-add-button'))

      expect(onAdd).toHaveBeenCalledTimes(1)
    })

    it('should call onAdd when add button is clicked in empty state', async () => {
      const onAdd = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} fields={[]} onAdd={onAdd} />)

      fireEvent.click(screen.getByTestId('fields-panel-add-button'))

      expect(onAdd).toHaveBeenCalledTimes(1)
    })
  })

  describe('Edit Field Action', () => {
    it('should call onEdit with field when edit button is clicked', async () => {
      const onEdit = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onEdit={onEdit} />)

      fireEvent.click(screen.getByTestId('fields-panel-edit-field-1'))

      expect(onEdit).toHaveBeenCalledTimes(1)
      expect(onEdit).toHaveBeenCalledWith(sampleFields[0])
    })

    it('should call onEdit with correct field for different fields', async () => {
      const onEdit = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onEdit={onEdit} />)

      fireEvent.click(screen.getByTestId('fields-panel-edit-field-3'))

      expect(onEdit).toHaveBeenCalledWith(sampleFields[2])
    })
  })

  describe('Delete Field Action', () => {
    it('should open confirmation dialog when delete button is clicked', async () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))

      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    })

    it('should display field name in confirmation message', async () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))

      expect(screen.getByTestId('confirm-dialog-message')).toHaveTextContent('name')
    })

    it('should call onDelete when deletion is confirmed', async () => {
      const onDelete = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onDelete={onDelete} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))
      fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))

      expect(onDelete).toHaveBeenCalledTimes(1)
      expect(onDelete).toHaveBeenCalledWith(sampleFields[0])
    })

    it('should not call onDelete when deletion is cancelled', async () => {
      const onDelete = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onDelete={onDelete} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))
      fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))

      expect(onDelete).not.toHaveBeenCalled()
    })

    it('should close dialog after confirmation', async () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))
      fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))

      expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    })

    it('should close dialog after cancellation', async () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))
      fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))

      expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    })

    it('should use danger variant for delete confirmation', async () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      fireEvent.click(screen.getByTestId('fields-panel-delete-field-1'))

      expect(screen.getByTestId('confirm-dialog').className).toMatch(/danger/)
    })
  })

  describe('Drag and Drop Reordering', () => {
    it('should have draggable attribute on field items', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      const fieldItem = screen.getByTestId('fields-panel-field-field-1')
      expect(fieldItem).toHaveAttribute('draggable', 'true')
    })

    it('should call onReorder when field is dropped on another field', () => {
      const onReorder = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onReorder={onReorder} />)

      const sourceField = screen.getByTestId('fields-panel-field-field-1')
      const targetField = screen.getByTestId('fields-panel-field-field-3')

      // Simulate drag and drop
      fireEvent.dragStart(sourceField, {
        dataTransfer: {
          effectAllowed: 'move',
          setData: vi.fn(),
        },
      })

      fireEvent.dragOver(targetField, {
        dataTransfer: {
          dropEffect: 'move',
        },
        preventDefault: vi.fn(),
      })

      fireEvent.drop(targetField, {
        dataTransfer: {
          getData: () => 'field-1',
        },
        preventDefault: vi.fn(),
      })

      expect(onReorder).toHaveBeenCalledTimes(1)
    })

    it('should update field order values after reorder', () => {
      const onReorder = vi.fn()
      const fields = [
        createField({ id: 'field-a', name: 'a', order: 0 }),
        createField({ id: 'field-b', name: 'b', order: 1 }),
        createField({ id: 'field-c', name: 'c', order: 2 }),
      ]
      renderWithI18n(<FieldsPanel {...defaultProps} fields={fields} onReorder={onReorder} />)

      const sourceField = screen.getByTestId('fields-panel-field-field-a')
      const targetField = screen.getByTestId('fields-panel-field-field-c')

      fireEvent.dragStart(sourceField, {
        dataTransfer: {
          effectAllowed: 'move',
          setData: vi.fn(),
        },
      })

      fireEvent.drop(targetField, {
        dataTransfer: {
          getData: () => 'field-a',
        },
        preventDefault: vi.fn(),
      })

      expect(onReorder).toHaveBeenCalled()
      const reorderedFields = onReorder.mock.calls[0][0]

      // Verify order values are updated
      expect(reorderedFields[0].order).toBe(0)
      expect(reorderedFields[1].order).toBe(1)
      expect(reorderedFields[2].order).toBe(2)
    })

    it('should not call onReorder when dropping on same field', () => {
      const onReorder = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onReorder={onReorder} />)

      const field = screen.getByTestId('fields-panel-field-field-1')

      fireEvent.dragStart(field, {
        dataTransfer: {
          effectAllowed: 'move',
          setData: vi.fn(),
        },
      })

      fireEvent.drop(field, {
        dataTransfer: {
          getData: () => 'field-1',
        },
        preventDefault: vi.fn(),
      })

      expect(onReorder).not.toHaveBeenCalled()
    })
  })

  describe('Keyboard Reordering', () => {
    it('should move field up with Alt+ArrowUp', async () => {
      const onReorder = vi.fn()
      const fields = [
        createField({ id: 'field-a', name: 'a', order: 0 }),
        createField({ id: 'field-b', name: 'b', order: 1 }),
      ]
      renderWithI18n(<FieldsPanel {...defaultProps} fields={fields} onReorder={onReorder} />)

      const secondField = screen.getByTestId('fields-panel-field-field-b')
      fireEvent.keyDown(secondField, { key: 'ArrowUp', altKey: true })

      expect(onReorder).toHaveBeenCalled()
      const reorderedFields = onReorder.mock.calls[0][0]
      expect(reorderedFields[0].id).toBe('field-b')
      expect(reorderedFields[1].id).toBe('field-a')
    })

    it('should move field down with Alt+ArrowDown', async () => {
      const onReorder = vi.fn()
      const fields = [
        createField({ id: 'field-a', name: 'a', order: 0 }),
        createField({ id: 'field-b', name: 'b', order: 1 }),
      ]
      renderWithI18n(<FieldsPanel {...defaultProps} fields={fields} onReorder={onReorder} />)

      const firstField = screen.getByTestId('fields-panel-field-field-a')
      fireEvent.keyDown(firstField, { key: 'ArrowDown', altKey: true })

      expect(onReorder).toHaveBeenCalled()
      const reorderedFields = onReorder.mock.calls[0][0]
      expect(reorderedFields[0].id).toBe('field-b')
      expect(reorderedFields[1].id).toBe('field-a')
    })

    it('should not move first field up', async () => {
      const onReorder = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onReorder={onReorder} />)

      const firstField = screen.getByTestId('fields-panel-field-field-1')
      fireEvent.keyDown(firstField, { key: 'ArrowUp', altKey: true })

      expect(onReorder).not.toHaveBeenCalled()
    })

    it('should not move last field down', async () => {
      const onReorder = vi.fn()
      renderWithI18n(<FieldsPanel {...defaultProps} onReorder={onReorder} />)

      const lastField = screen.getByTestId('fields-panel-field-field-5')
      fireEvent.keyDown(lastField, { key: 'ArrowDown', altKey: true })

      expect(onReorder).not.toHaveBeenCalled()
    })
  })

  describe('Accessibility', () => {
    it('should have role="list" on field list', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-list')).toHaveAttribute('role', 'list')
    })

    it('should have role="listitem" on field items', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      const fieldItem = screen.getByTestId('fields-panel-field-field-1')
      expect(fieldItem).toHaveAttribute('role', 'listitem')
    })

    it('should have aria-label on field list', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-list')).toHaveAttribute('aria-label')
    })

    it('should have aria-label on field items', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      const fieldItem = screen.getByTestId('fields-panel-field-field-1')
      expect(fieldItem).toHaveAttribute('aria-label')
    })

    it('should have aria-label on add button', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-add-button')).toHaveAttribute('aria-label')
    })

    it('should have aria-label on edit buttons', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-edit-field-1')).toHaveAttribute('aria-label')
    })

    it('should have aria-label on delete buttons', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      expect(screen.getByTestId('fields-panel-delete-field-1')).toHaveAttribute('aria-label')
    })

    it('should have tabIndex on field items for keyboard navigation', () => {
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      const fieldItem = screen.getByTestId('fields-panel-field-field-1')
      expect(fieldItem).toHaveAttribute('tabIndex', '0')
    })

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup()
      renderWithI18n(<FieldsPanel {...defaultProps} />)

      const addButton = screen.getByTestId('fields-panel-add-button')
      addButton.focus()
      expect(addButton).toHaveFocus()

      await user.tab()
      expect(screen.getByTestId('fields-panel-field-field-1')).toHaveFocus()
    })
  })

  describe('Field Types Display', () => {
    it('should display all field types correctly', () => {
      const allTypeFields: FieldDefinition[] = [
        createField({ id: 'f1', name: 'string_field', type: 'string', order: 0 }),
        createField({ id: 'f2', name: 'number_field', type: 'number', order: 1 }),
        createField({ id: 'f3', name: 'boolean_field', type: 'boolean', order: 2 }),
        createField({ id: 'f4', name: 'date_field', type: 'date', order: 3 }),
        createField({ id: 'f5', name: 'datetime_field', type: 'datetime', order: 4 }),
        createField({ id: 'f6', name: 'json_field', type: 'json', order: 5 }),
        createField({
          id: 'f7',
          name: 'reference_field',
          type: 'reference',
          referenceTarget: 'users',
          order: 6,
        }),
      ]

      renderWithI18n(<FieldsPanel {...defaultProps} fields={allTypeFields} />)

      expect(screen.getByTestId('fields-panel-field-type-f1')).toHaveTextContent('String')
      expect(screen.getByTestId('fields-panel-field-type-f2')).toHaveTextContent('Number')
      expect(screen.getByTestId('fields-panel-field-type-f3')).toHaveTextContent('Boolean')
      expect(screen.getByTestId('fields-panel-field-type-f4')).toHaveTextContent('Date')
      expect(screen.getByTestId('fields-panel-field-type-f5')).toHaveTextContent('Date & Time')
      expect(screen.getByTestId('fields-panel-field-type-f6')).toHaveTextContent('JSON')
      expect(screen.getByTestId('fields-panel-field-type-f7')).toHaveTextContent('Reference')
    })
  })

  describe('Multiple Badges', () => {
    it('should display multiple badges when field has multiple attributes', () => {
      const fields = [
        createField({
          id: 'field-1',
          name: 'email',
          type: 'string',
          required: true,
          unique: true,
          indexed: true,
          order: 0,
        }),
      ]

      renderWithI18n(<FieldsPanel {...defaultProps} fields={fields} />)

      expect(screen.getByTestId('fields-panel-field-required-field-1')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-field-unique-field-1')).toBeInTheDocument()
      expect(screen.getByTestId('fields-panel-field-indexed-field-1')).toBeInTheDocument()
    })
  })
})

describe('FieldsPanel Integration', () => {
  it('should work with state management for CRUD operations', () => {
    const TestComponent = () => {
      const [fields, setFields] = React.useState<FieldDefinition[]>([
        {
          id: '1',
          name: 'field1',
          type: 'string',
          required: false,
          unique: false,
          indexed: false,
          order: 0,
        },
        {
          id: '2',
          name: 'field2',
          type: 'number',
          required: false,
          unique: false,
          indexed: false,
          order: 1,
        },
      ])
      const [addCount, setAddCount] = React.useState(0)
      const [editedField, setEditedField] = React.useState<string | null>(null)

      return (
        <I18nProvider>
          <div data-testid="add-count">{addCount}</div>
          <div data-testid="edited-field">{editedField || 'none'}</div>
          <div data-testid="field-count">{fields.length}</div>
          <FieldsPanel
            fields={fields}
            onAdd={() => setAddCount((c) => c + 1)}
            onEdit={(field) => setEditedField(field.name)}
            onDelete={(field) => setFields((f) => f.filter((item) => item.id !== field.id))}
            onReorder={(newFields) => setFields(newFields)}
          />
        </I18nProvider>
      )
    }

    render(<TestComponent />)

    // Initial state
    expect(screen.getByTestId('field-count')).toHaveTextContent('2')
    expect(screen.getByTestId('add-count')).toHaveTextContent('0')
    expect(screen.getByTestId('edited-field')).toHaveTextContent('none')

    // Test add
    fireEvent.click(screen.getByTestId('fields-panel-add-button'))
    expect(screen.getByTestId('add-count')).toHaveTextContent('1')

    // Test edit
    fireEvent.click(screen.getByTestId('fields-panel-edit-1'))
    expect(screen.getByTestId('edited-field')).toHaveTextContent('field1')

    // Test delete
    fireEvent.click(screen.getByTestId('fields-panel-delete-1'))
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    expect(screen.getByTestId('field-count')).toHaveTextContent('1')
  })
})
