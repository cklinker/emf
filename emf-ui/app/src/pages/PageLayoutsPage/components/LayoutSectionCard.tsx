/**
 * LayoutSectionCard Component
 *
 * Individual section card in the layout canvas. Displays a header
 * with drag handle, column selector, collapse toggle, and delete button.
 * The body renders a CSS grid with columns that act as drop zones for fields.
 */

import React, { useState, useMemo, useCallback } from 'react'
import { GripVertical, ChevronDown, ChevronRight, X, Columns2, Columns3 } from 'lucide-react'
import { useLayoutEditor, type EditorFieldPlacement } from './LayoutEditorContext'
import { LayoutFieldSlot } from './LayoutFieldSlot'
import styles from './LayoutSectionCard.module.css'

export interface LayoutSectionCardProps {
  sectionId: string
}

export function LayoutSectionCard({
  sectionId,
}: LayoutSectionCardProps): React.ReactElement | null {
  const { state, updateSection, removeSection, selectSection, addField, moveField, setDragSource } =
    useLayoutEditor()

  const section = useMemo(
    () => state.sections.find((s) => s.id === sectionId),
    [state.sections, sectionId]
  )

  const isSelected = state.selectedSectionId === sectionId
  const isHighlightsPanel = section?.sectionType === 'HIGHLIGHTS_PANEL'

  const [collapsedLocal, setCollapsedLocal] = useState(false)
  const [dragOverColumn, setDragOverColumn] = useState<number | null>(null)

  const handleHeaderClick = useCallback(
    (e: React.MouseEvent) => {
      // Don't select if clicking on action buttons
      if ((e.target as HTMLElement).closest('button')) return
      selectSection(sectionId)
    },
    [selectSection, sectionId]
  )

  const handleToggleCollapse = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    setCollapsedLocal((prev) => !prev)
  }, [])

  const handleColumnChange = useCallback(
    (columns: number) => {
      updateSection(sectionId, { columns })
    },
    [updateSection, sectionId]
  )

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      removeSection(sectionId)
    },
    [removeSection, sectionId]
  )

  // Section drag handlers (for reordering sections)
  const handleDragStart = useCallback(
    (e: React.DragEvent) => {
      e.dataTransfer.setData('application/json', JSON.stringify({ type: 'section', sectionId }))
      e.dataTransfer.effectAllowed = 'move'
      setDragSource({ type: 'section', sectionId })
    },
    [sectionId, setDragSource]
  )

  const handleDragEnd = useCallback(() => {
    setDragSource(null)
  }, [setDragSource])

  // Column drop zone handlers
  const handleColumnDragOver = useCallback((e: React.DragEvent, columnNumber: number) => {
    e.preventDefault()
    e.stopPropagation()
    e.dataTransfer.dropEffect = 'move'
    setDragOverColumn(columnNumber)
  }, [])

  const handleColumnDragLeave = useCallback((e: React.DragEvent) => {
    // Only clear if leaving the column entirely
    const relatedTarget = e.relatedTarget as HTMLElement | null
    if (!relatedTarget || !e.currentTarget.contains(relatedTarget)) {
      setDragOverColumn(null)
    }
  }, [])

  const handleColumnDrop = useCallback(
    (e: React.DragEvent, columnNumber: number) => {
      e.preventDefault()
      e.stopPropagation()
      setDragOverColumn(null)

      const raw = e.dataTransfer.getData('application/json')
      if (!raw) return

      try {
        const data = JSON.parse(raw) as Record<string, unknown>

        if (data.type === 'palette-field') {
          // New field from palette
          const fieldsInColumn =
            section?.fields.filter((f) => f.columnNumber === columnNumber) ?? []
          const maxSortOrder = fieldsInColumn.reduce((max, f) => Math.max(max, f.sortOrder), -1)
          addField(
            data.fieldId as string,
            data.fieldName as string,
            data.fieldType as string,
            data.fieldDisplayName as string,
            sectionId,
            columnNumber,
            maxSortOrder + 1
          )
        } else if (data.type === 'canvas-field') {
          // Moving existing field
          const fieldsInColumn =
            section?.fields.filter((f) => f.columnNumber === columnNumber) ?? []
          const maxSortOrder = fieldsInColumn.reduce((max, f) => Math.max(max, f.sortOrder), -1)
          moveField(data.fieldPlacementId as string, sectionId, columnNumber, maxSortOrder + 1)
        }
      } catch {
        // Invalid JSON, ignore
      }
    },
    [section, sectionId, addField, moveField]
  )

  const columns = section?.columns || 1

  // Group fields by column
  const fieldsByColumn = useMemo(() => {
    if (!section) return new Map<number, EditorFieldPlacement[]>()
    const grouped: Map<number, EditorFieldPlacement[]> = new Map()
    for (let i = 0; i < columns; i++) {
      grouped.set(i, [])
    }
    for (const field of section.fields) {
      const col = field.columnNumber
      if (!grouped.has(col)) {
        grouped.set(col, [])
      }
      grouped.get(col)!.push(field)
    }
    // Sort fields within each column by sortOrder
    for (const [, fields] of grouped) {
      fields.sort((a, b) => a.sortOrder - b.sortOrder)
    }
    return grouped
  }, [section, columns])

  if (!section) return null

  const sectionClasses = [
    styles.section,
    isSelected ? styles.sectionSelected : '',
    isHighlightsPanel ? styles.sectionHighlight : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={sectionClasses} data-testid={`layout-section-${sectionId}`}>
      <div
        className={styles.sectionHeader}
        onClick={handleHeaderClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            selectSection(sectionId)
          }
        }}
        data-testid={`layout-section-header-${sectionId}`}
      >
        <div
          className={styles.dragHandle}
          draggable
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          aria-label="Drag to reorder section"
          data-testid={`layout-section-drag-${sectionId}`}
        >
          <GripVertical size={16} />
        </div>

        <button
          type="button"
          className={styles.sectionActionButton}
          onClick={handleToggleCollapse}
          aria-label={collapsedLocal ? 'Expand section' : 'Collapse section'}
          data-testid={`layout-section-collapse-${sectionId}`}
        >
          {collapsedLocal ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
        </button>

        <h4 className={styles.sectionHeading}>{section.heading || 'Untitled Section'}</h4>

        {isHighlightsPanel && <span className={styles.sectionBadge}>Highlights</span>}
        {section.sectionType !== 'HIGHLIGHTS_PANEL' && section.sectionType !== 'fields' && (
          <span className={styles.sectionBadge}>{section.sectionType}</span>
        )}

        <div className={styles.columnSelector}>
          <button
            type="button"
            className={`${styles.columnButton} ${columns === 1 ? styles.columnButtonActive : ''}`}
            onClick={(e) => {
              e.stopPropagation()
              handleColumnChange(1)
            }}
            aria-label="1 column"
            title="1 column"
            data-testid={`layout-section-col1-${sectionId}`}
          >
            1
          </button>
          <button
            type="button"
            className={`${styles.columnButton} ${columns === 2 ? styles.columnButtonActive : ''}`}
            onClick={(e) => {
              e.stopPropagation()
              handleColumnChange(2)
            }}
            aria-label="2 columns"
            title="2 columns"
            data-testid={`layout-section-col2-${sectionId}`}
          >
            <Columns2 size={12} />
          </button>
          <button
            type="button"
            className={`${styles.columnButton} ${columns === 3 ? styles.columnButtonActive : ''}`}
            onClick={(e) => {
              e.stopPropagation()
              handleColumnChange(3)
            }}
            aria-label="3 columns"
            title="3 columns"
            data-testid={`layout-section-col3-${sectionId}`}
          >
            <Columns3 size={12} />
          </button>
        </div>

        <div className={styles.sectionActions}>
          <button
            type="button"
            className={styles.sectionActionButton}
            onClick={handleDelete}
            aria-label="Delete section"
            title="Delete section"
            data-testid={`layout-section-delete-${sectionId}`}
          >
            <X size={14} />
          </button>
        </div>
      </div>

      {!collapsedLocal && (
        <div className={styles.sectionBody}>
          <div
            className={styles.columnGrid}
            style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
          >
            {Array.from({ length: columns }, (_, colIndex) => {
              const fieldsInCol = fieldsByColumn.get(colIndex) ?? []
              const isDragOver = dragOverColumn === colIndex

              return (
                <div
                  key={colIndex}
                  className={`${styles.column} ${isDragOver ? styles.columnDragOver : ''}`}
                  onDragOver={(e) => handleColumnDragOver(e, colIndex)}
                  onDragLeave={handleColumnDragLeave}
                  onDrop={(e) => handleColumnDrop(e, colIndex)}
                  data-testid={`layout-section-col-${sectionId}-${colIndex}`}
                >
                  {fieldsInCol.length === 0 ? (
                    <div className={styles.columnEmpty}>Drop fields here</div>
                  ) : (
                    fieldsInCol.map((fp) => (
                      <LayoutFieldSlot key={fp.id} fieldPlacement={fp} sectionId={sectionId} />
                    ))
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
