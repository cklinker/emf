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
import { cn } from '@/lib/utils'

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

  return (
    <div
      className={cn(
        'overflow-hidden rounded-lg border border-border bg-background transition-shadow duration-150 motion-reduce:transition-none',
        isSelected && 'border-primary shadow-[0_0_0_2px_rgba(37,99,235,0.2)]',
        isHighlightsPanel && 'border-yellow-500 bg-yellow-50'
      )}
      data-testid={`layout-section-${sectionId}`}
    >
      <div
        className="flex cursor-pointer items-center gap-2 border-b border-border bg-muted px-4 py-2.5 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px] max-md:px-3 max-md:py-2"
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
          className="flex shrink-0 items-center text-muted-foreground cursor-grab active:cursor-grabbing"
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
          className="flex h-6 w-6 items-center justify-center rounded border-none bg-transparent p-0 text-muted-foreground cursor-pointer transition-colors duration-150 hover:bg-muted-foreground/10 hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 motion-reduce:transition-none"
          onClick={handleToggleCollapse}
          aria-label={collapsedLocal ? 'Expand section' : 'Collapse section'}
          data-testid={`layout-section-collapse-${sectionId}`}
        >
          {collapsedLocal ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
        </button>

        <h4 className="m-0 flex-1 text-sm font-medium text-foreground">
          {section.heading || 'Untitled Section'}
        </h4>

        {isHighlightsPanel && (
          <span className="rounded bg-muted-foreground/10 px-1.5 py-0.5 text-[11px] text-muted-foreground">
            Highlights
          </span>
        )}
        {section.sectionType !== 'HIGHLIGHTS_PANEL' && section.sectionType !== 'fields' && (
          <span className="rounded bg-muted-foreground/10 px-1.5 py-0.5 text-[11px] text-muted-foreground">
            {section.sectionType}
          </span>
        )}

        <div className="flex gap-0.5">
          <button
            type="button"
            className={cn(
              'rounded-sm border border-input bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground cursor-pointer transition-colors duration-150 motion-reduce:transition-none',
              'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-1',
              columns === 1 ? 'bg-primary text-primary-foreground border-primary' : 'hover:bg-muted'
            )}
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
            className={cn(
              'rounded-sm border border-input bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground cursor-pointer transition-colors duration-150 motion-reduce:transition-none',
              'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-1',
              columns === 2 ? 'bg-primary text-primary-foreground border-primary' : 'hover:bg-muted'
            )}
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
            className={cn(
              'rounded-sm border border-input bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground cursor-pointer transition-colors duration-150 motion-reduce:transition-none',
              'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-1',
              columns === 3 ? 'bg-primary text-primary-foreground border-primary' : 'hover:bg-muted'
            )}
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

        <div className="flex gap-1">
          <button
            type="button"
            className="flex h-6 w-6 items-center justify-center rounded border-none bg-transparent p-0 text-muted-foreground cursor-pointer transition-colors duration-150 hover:bg-muted-foreground/10 hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 motion-reduce:transition-none"
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
        <div className="min-h-[60px] p-4 max-md:p-3">
          <div
            className="grid gap-3 max-md:!grid-cols-1"
            style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
          >
            {Array.from({ length: columns }, (_, colIndex) => {
              const fieldsInCol = fieldsByColumn.get(colIndex) ?? []
              const isDragOver = dragOverColumn === colIndex

              return (
                <div
                  key={colIndex}
                  className={cn(
                    'min-h-[40px] rounded-md p-1 transition-colors duration-150 motion-reduce:transition-none',
                    isDragOver &&
                      'bg-primary/5 outline-2 outline-dashed outline-primary outline-offset-[-2px]'
                  )}
                  onDragOver={(e) => handleColumnDragOver(e, colIndex)}
                  onDragLeave={handleColumnDragLeave}
                  onDrop={(e) => handleColumnDrop(e, colIndex)}
                  data-testid={`layout-section-col-${sectionId}-${colIndex}`}
                >
                  {fieldsInCol.length === 0 ? (
                    <div className="flex min-h-[40px] items-center justify-center rounded-md border border-dashed border-border text-xs text-input">
                      Drop fields here
                    </div>
                  ) : (
                    fieldsInCol.map((fp) => {
                      const span = fp.columnSpan ?? 1
                      const showSpanBadge = span > 1
                      return (
                        <div
                          key={fp.id}
                          className={showSpanBadge ? 'relative' : undefined}
                          data-span={span > 1 ? span : undefined}
                        >
                          <LayoutFieldSlot fieldPlacement={fp} sectionId={sectionId} />
                          {showSpanBadge && (
                            <span className="pointer-events-none absolute -right-1 -top-1.5 z-[1] rounded bg-primary px-[5px] py-px text-[10px] font-semibold leading-[1.4] text-primary-foreground">
                              Span {span}
                            </span>
                          )}
                        </div>
                      )
                    })
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
