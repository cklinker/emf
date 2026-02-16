/**
 * LayoutFieldSlot Component
 *
 * Individual field slot rendered within a section column.
 * Displays the field name, type, required/read-only badges,
 * a drag handle for reordering, and a delete button on hover.
 */

import React, { useCallback } from 'react'
import { GripVertical, X } from 'lucide-react'
import { useLayoutEditor, type EditorFieldPlacement } from './LayoutEditorContext'
import styles from './LayoutFieldSlot.module.css'

export interface LayoutFieldSlotProps {
  fieldPlacement: EditorFieldPlacement
  sectionId: string
}

export function LayoutFieldSlot({
  fieldPlacement,
  sectionId,
}: LayoutFieldSlotProps): React.ReactElement {
  const { state, selectField, removeField, setDragSource } = useLayoutEditor()
  const isSelected = state.selectedFieldPlacementId === fieldPlacement.id

  const displayName =
    fieldPlacement.labelOverride ||
    fieldPlacement.fieldDisplayName ||
    fieldPlacement.fieldName ||
    'Unknown Field'

  const handleClick = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      selectField(fieldPlacement.id)
    },
    [selectField, fieldPlacement.id]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        e.stopPropagation()
        selectField(fieldPlacement.id)
      }
    },
    [selectField, fieldPlacement.id]
  )

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      removeField(fieldPlacement.id)
    },
    [removeField, fieldPlacement.id]
  )

  const handleDragStart = useCallback(
    (e: React.DragEvent) => {
      const data = {
        type: 'canvas-field',
        fieldPlacementId: fieldPlacement.id,
        sourceSectionId: sectionId,
        sourceColumn: fieldPlacement.columnNumber,
      }
      e.dataTransfer.setData('application/json', JSON.stringify(data))
      e.dataTransfer.effectAllowed = 'move'
      setDragSource({
        type: 'canvas-field',
        fieldPlacementId: fieldPlacement.id,
        sourceSectionId: sectionId,
        sourceColumn: fieldPlacement.columnNumber,
      })
    },
    [fieldPlacement.id, fieldPlacement.columnNumber, sectionId, setDragSource]
  )

  const handleDragEnd = useCallback(() => {
    setDragSource(null)
  }, [setDragSource])

  const slotClasses = [styles.fieldSlot, isSelected ? styles.fieldSlotSelected : '']
    .filter(Boolean)
    .join(' ')

  return (
    <div
      className={slotClasses}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      role="button"
      tabIndex={0}
      data-testid={`layout-field-slot-${fieldPlacement.id}`}
    >
      <div
        className={styles.fieldDragHandle}
        draggable
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        aria-label="Drag to reorder field"
      >
        <GripVertical size={14} />
      </div>

      <div className={styles.fieldInfo}>
        <span className={styles.fieldName}>{displayName}</span>
        {fieldPlacement.fieldType && (
          <span className={styles.fieldType}>{fieldPlacement.fieldType}</span>
        )}
      </div>

      <div className={styles.fieldBadges}>
        {fieldPlacement.requiredOnLayout && <span className={styles.requiredBadge}>Required</span>}
        {fieldPlacement.readOnlyOnLayout && <span className={styles.readOnlyBadge}>Read-only</span>}
      </div>

      <button
        type="button"
        className={styles.deleteFieldButton}
        onClick={handleDelete}
        aria-label={`Remove ${displayName}`}
        title="Remove field"
        data-testid={`layout-field-delete-${fieldPlacement.id}`}
      >
        <X size={12} />
      </button>
    </div>
  )
}
