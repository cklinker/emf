/**
 * FieldsPanel Component
 *
 * Displays a sortable list of fields within a collection with drag-and-drop
 * reordering support and CRUD actions (add, edit, delete).
 *
 * Requirements:
 * - 4.1: Display all active fields in a sortable list
 * - 4.8: Display confirmation dialog before field deletion
 * - 4.9: Mark field as inactive and remove from list on deletion
 * - 4.10: Support drag-and-drop reordering of fields
 *
 * Features:
 * - Sortable list of fields ordered by field.order
 * - Field details display: name, displayName, type, required, unique, indexed
 * - Drag-and-drop reordering using HTML5 Drag and Drop API
 * - Add field button
 * - Edit field button for each field
 * - Delete field button with confirmation dialog
 * - Empty state when no fields
 * - Loading state
 * - Accessible with keyboard navigation and ARIA attributes
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useI18n } from '../../context/I18nContext'
import { ConfirmDialog } from '../ConfirmDialog'
import { LoadingSpinner } from '../LoadingSpinner'
import styles from './FieldsPanel.module.css'

/**
 * Field type enumeration
 */
export type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference'

/**
 * Validation rule interface
 */
export interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom'
  value?: unknown
  message?: string
}

/**
 * Field definition interface
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
  type: FieldType
  required: boolean
  unique: boolean
  indexed: boolean
  defaultValue?: unknown
  validation?: ValidationRule[]
  referenceTarget?: string
  order: number
}

/**
 * Props for the FieldsPanel component
 */
export interface FieldsPanelProps {
  /** Array of field definitions to display */
  fields: FieldDefinition[]
  /** Callback when add field button is clicked */
  onAdd: () => void
  /** Callback when edit button is clicked for a field */
  onEdit: (field: FieldDefinition) => void
  /** Callback when delete is confirmed for a field */
  onDelete: (field: FieldDefinition) => void
  /** Callback when fields are reordered via drag-and-drop */
  onReorder: (fields: FieldDefinition[]) => void
  /** Whether the panel is in a loading state */
  isLoading?: boolean
  /** Test ID for the component */
  testId?: string
}

/**
 * Get the icon for a field type
 */
function getFieldTypeIcon(type: FieldType): string {
  const icons: Record<FieldType, string> = {
    string: 'Aa',
    number: '#',
    boolean: '✓',
    date: '📅',
    datetime: '🕐',
    json: '{}',
    reference: '🔗',
  }
  return icons[type] || '?'
}

/**
 * FieldsPanel Component
 *
 * Displays a sortable list of fields with drag-and-drop reordering
 * and CRUD actions.
 *
 * @example
 * ```tsx
 * <FieldsPanel
 *   fields={fields}
 *   onAdd={() => setShowAddForm(true)}
 *   onEdit={(field) => setEditingField(field)}
 *   onDelete={(field) => deleteField(field.id)}
 *   onReorder={(newFields) => updateFieldOrder(newFields)}
 *   isLoading={isLoading}
 * />
 * ```
 */
export function FieldsPanel({
  fields,
  onAdd,
  onEdit,
  onDelete,
  onReorder,
  isLoading = false,
  testId = 'fields-panel',
}: FieldsPanelProps): React.ReactElement {
  const { t } = useI18n()

  // State for delete confirmation dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [fieldToDelete, setFieldToDelete] = useState<FieldDefinition | null>(null)

  // State for drag-and-drop
  const [draggedFieldId, setDraggedFieldId] = useState<string | null>(null)
  const [dragOverFieldId, setDragOverFieldId] = useState<string | null>(null)

  // Sort fields by order
  const sortedFields = useMemo(() => {
    return [...fields].sort((a, b) => a.order - b.order)
  }, [fields])

  /**
   * Handle delete button click - open confirmation dialog
   */
  const handleDeleteClick = useCallback((field: FieldDefinition) => {
    setFieldToDelete(field)
    setDeleteDialogOpen(true)
  }, [])

  /**
   * Handle delete confirmation
   */
  const handleDeleteConfirm = useCallback(() => {
    if (fieldToDelete) {
      onDelete(fieldToDelete)
    }
    setDeleteDialogOpen(false)
    setFieldToDelete(null)
  }, [fieldToDelete, onDelete])

  /**
   * Handle delete cancellation
   */
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setFieldToDelete(null)
  }, [])

  /**
   * Handle drag start
   */
  const handleDragStart = useCallback(
    (e: React.DragEvent<HTMLDivElement>, field: FieldDefinition) => {
      setDraggedFieldId(field.id)
      e.dataTransfer.effectAllowed = 'move'
      e.dataTransfer.setData('text/plain', field.id)

      // Add a slight delay to allow the drag image to be captured
      const target = e.currentTarget
      setTimeout(() => {
        target.classList.add(styles.dragging)
      }, 0)
    },
    []
  )

  /**
   * Handle drag end
   */
  const handleDragEnd = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.currentTarget.classList.remove(styles.dragging)
    setDraggedFieldId(null)
    setDragOverFieldId(null)
  }, [])

  /**
   * Handle drag over
   */
  const handleDragOver = useCallback(
    (e: React.DragEvent<HTMLDivElement>, field: FieldDefinition) => {
      e.preventDefault()
      e.dataTransfer.dropEffect = 'move'

      if (draggedFieldId && draggedFieldId !== field.id) {
        setDragOverFieldId(field.id)
      }
    },
    [draggedFieldId]
  )

  /**
   * Handle drag leave
   */
  const handleDragLeave = useCallback(() => {
    setDragOverFieldId(null)
  }, [])

  /**
   * Handle drop - reorder fields
   */
  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>, targetField: FieldDefinition) => {
      e.preventDefault()

      if (!draggedFieldId || draggedFieldId === targetField.id) {
        setDragOverFieldId(null)
        return
      }

      const draggedIndex = sortedFields.findIndex((f) => f.id === draggedFieldId)
      const targetIndex = sortedFields.findIndex((f) => f.id === targetField.id)

      if (draggedIndex === -1 || targetIndex === -1) {
        setDragOverFieldId(null)
        return
      }

      // Create new array with reordered fields
      const newFields = [...sortedFields]
      const [draggedField] = newFields.splice(draggedIndex, 1)
      newFields.splice(targetIndex, 0, draggedField)

      // Update order values
      const reorderedFields = newFields.map((field, index) => ({
        ...field,
        order: index,
      }))

      onReorder(reorderedFields)
      setDragOverFieldId(null)
    },
    [draggedFieldId, sortedFields, onReorder]
  )

  /**
   * Handle keyboard reordering
   */
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLDivElement>, field: FieldDefinition, index: number) => {
      if (e.key === 'ArrowUp' && e.altKey && index > 0) {
        e.preventDefault()
        const newFields = [...sortedFields]
        ;[newFields[index - 1], newFields[index]] = [newFields[index], newFields[index - 1]]
        const reorderedFields = newFields.map((f, i) => ({ ...f, order: i }))
        onReorder(reorderedFields)
      } else if (e.key === 'ArrowDown' && e.altKey && index < sortedFields.length - 1) {
        e.preventDefault()
        const newFields = [...sortedFields]
        ;[newFields[index], newFields[index + 1]] = [newFields[index + 1], newFields[index]]
        const reorderedFields = newFields.map((f, i) => ({ ...f, order: i }))
        onReorder(reorderedFields)
      }
    },
    [sortedFields, onReorder]
  )

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.header}>
          <h3 className={styles.title}>{t('collections.fields')}</h3>
        </div>
        <div className={styles.loadingContainer} data-testid={`${testId}-loading`}>
          <LoadingSpinner size="medium" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render empty state
  if (sortedFields.length === 0) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.header}>
          <h3 className={styles.title}>{t('collections.fields')}</h3>
          <button
            type="button"
            className={styles.addButton}
            onClick={onAdd}
            data-testid={`${testId}-add-button`}
            aria-label={t('collections.addField')}
          >
            <span className={styles.addIcon} aria-hidden="true">
              +
            </span>
            {t('collections.addField')}
          </button>
        </div>
        <div className={styles.emptyState} data-testid={`${testId}-empty`}>
          <p className={styles.emptyMessage}>{t('fieldsPanel.noFields')}</p>
          <p className={styles.emptyHint}>{t('fieldsPanel.addFieldHint')}</p>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      <div className={styles.header}>
        <h3 className={styles.title}>{t('collections.fields')}</h3>
        <button
          type="button"
          className={styles.addButton}
          onClick={onAdd}
          data-testid={`${testId}-add-button`}
          aria-label={t('collections.addField')}
        >
          <span className={styles.addIcon} aria-hidden="true">
            +
          </span>
          {t('collections.addField')}
        </button>
      </div>

      <div
        className={styles.fieldList}
        role="list"
        aria-label={t('fieldsPanel.fieldListLabel')}
        data-testid={`${testId}-list`}
      >
        {sortedFields.map((field, index) => (
          <div
            key={field.id}
            className={`${styles.fieldItem} ${dragOverFieldId === field.id ? styles.dragOver : ''}`}
            role="listitem"
            draggable
            onDragStart={(e) => handleDragStart(e, field)}
            onDragEnd={handleDragEnd}
            onDragOver={(e) => handleDragOver(e, field)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, field)}
            onKeyDown={(e) => handleKeyDown(e, field, index)}
            tabIndex={0}
            data-testid={`${testId}-field-${field.id}`}
            aria-label={t('fieldsPanel.fieldItemLabel', { name: field.displayName || field.name })}
          >
            <div
              className={styles.dragHandle}
              aria-hidden="true"
              data-testid={`${testId}-drag-handle-${field.id}`}
            >
              <span className={styles.dragIcon}>⋮⋮</span>
            </div>

            <div className={styles.fieldInfo}>
              <div className={styles.fieldMain}>
                <span
                  className={styles.fieldTypeIcon}
                  title={t(`fields.types.${field.type.toLowerCase()}`)}
                  aria-label={t(`fields.types.${field.type.toLowerCase()}`)}
                >
                  {getFieldTypeIcon(field.type)}
                </span>
                <span className={styles.fieldName} data-testid={`${testId}-field-name-${field.id}`}>
                  {field.name}
                </span>
                {field.displayName && field.displayName !== field.name && (
                  <span
                    className={styles.fieldDisplayName}
                    data-testid={`${testId}-field-display-name-${field.id}`}
                  >
                    ({field.displayName})
                  </span>
                )}
              </div>

              <div className={styles.fieldMeta}>
                <span className={styles.fieldType} data-testid={`${testId}-field-type-${field.id}`}>
                  {t(`fields.types.${field.type.toLowerCase()}`)}
                </span>

                {field.required && (
                  <span
                    className={`${styles.badge} ${styles.requiredBadge}`}
                    title={t('fields.validation.required')}
                    data-testid={`${testId}-field-required-${field.id}`}
                  >
                    {t('fields.validation.required')}
                  </span>
                )}

                {field.unique && (
                  <span
                    className={`${styles.badge} ${styles.uniqueBadge}`}
                    title={t('fields.validation.unique')}
                    data-testid={`${testId}-field-unique-${field.id}`}
                  >
                    {t('fields.validation.unique')}
                  </span>
                )}

                {field.indexed && (
                  <span
                    className={`${styles.badge} ${styles.indexedBadge}`}
                    title={t('fields.validation.indexed')}
                    data-testid={`${testId}-field-indexed-${field.id}`}
                  >
                    {t('fields.validation.indexed')}
                  </span>
                )}

                {field.type === 'reference' && field.referenceTarget && (
                  <span
                    className={styles.referenceTarget}
                    data-testid={`${testId}-field-reference-${field.id}`}
                  >
                    → {field.referenceTarget}
                  </span>
                )}
              </div>
            </div>

            <div className={styles.fieldActions}>
              <button
                type="button"
                className={styles.actionButton}
                onClick={() => onEdit(field)}
                title={t('collections.editField')}
                aria-label={t('fieldsPanel.editFieldLabel', { name: field.name })}
                data-testid={`${testId}-edit-${field.id}`}
              >
                <span aria-hidden="true">✏️</span>
              </button>
              <button
                type="button"
                className={`${styles.actionButton} ${styles.deleteButton}`}
                onClick={() => handleDeleteClick(field)}
                title={t('collections.deleteField')}
                aria-label={t('fieldsPanel.deleteFieldLabel', { name: field.name })}
                data-testid={`${testId}-delete-${field.id}`}
              >
                <span aria-hidden="true">🗑️</span>
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Delete confirmation dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('collections.deleteField')}
        message={t('fieldsPanel.confirmDelete', { name: fieldToDelete?.name || '' })}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
        id="delete-field-dialog"
      />
    </div>
  )
}

export default FieldsPanel
