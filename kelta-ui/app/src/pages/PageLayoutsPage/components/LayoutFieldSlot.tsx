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
import { cn } from '@/lib/utils'

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

  return (
    <div
      className={cn(
        'group relative mb-1 flex items-center gap-2 rounded-md border border-border bg-background px-3 py-2 cursor-grab transition-all duration-150 motion-reduce:transition-none',
        'hover:border-input',
        'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2',
        'active:cursor-grabbing',
        'max-md:px-2.5 max-md:py-1.5',
        isSelected && 'border-primary bg-primary/5 shadow-[0_0_0_2px_rgba(37,99,235,0.15)]'
      )}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      role="button"
      tabIndex={0}
      data-testid={`layout-field-slot-${fieldPlacement.id}`}
    >
      <div
        className="flex shrink-0 items-center text-input cursor-grab active:cursor-grabbing"
        draggable
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        aria-label="Drag to reorder field"
      >
        <GripVertical size={14} />
      </div>

      <div className="flex flex-1 flex-col gap-0.5 overflow-hidden min-w-0">
        <span className="text-[13px] text-foreground whitespace-nowrap overflow-hidden text-ellipsis">
          {displayName}
        </span>
        {fieldPlacement.fieldType && (
          <span className="text-[11px] text-muted-foreground">{fieldPlacement.fieldType}</span>
        )}
      </div>

      <div className="flex shrink-0 gap-1">
        {fieldPlacement.requiredOnLayout && (
          <span className="rounded-sm bg-destructive/10 px-1 py-px text-[10px] text-destructive">
            Required
          </span>
        )}
        {fieldPlacement.readOnlyOnLayout && (
          <span className="rounded-sm bg-muted px-1 py-px text-[10px] text-muted-foreground">
            Read-only
          </span>
        )}
      </div>

      <button
        type="button"
        className="flex h-5 w-5 items-center justify-center rounded border-none bg-transparent p-0 text-destructive cursor-pointer opacity-0 transition-opacity duration-150 group-hover:opacity-100 hover:bg-destructive/10 focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-destructive focus-visible:outline-offset-2 motion-reduce:transition-none"
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
