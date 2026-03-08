/**
 * FieldPalette Component
 *
 * Left-side panel containing available fields that can be dragged
 * onto the layout canvas. Includes search, field type icons,
 * and buttons to add sections and highlights panels.
 */

import React, { useState, useMemo, useCallback } from 'react'
import { useLayoutEditor, type AvailableField } from './LayoutEditorContext'
import { cn } from '@/lib/utils'

/**
 * Returns a short letter/symbol representing a field type.
 */
function getFieldTypeIcon(type: string): string {
  switch (type.toLowerCase()) {
    case 'string':
      return 'S'
    case 'number':
    case 'integer':
    case 'long':
    case 'double':
      return '#'
    case 'boolean':
      return 'T/F'
    case 'date':
      return 'D'
    case 'datetime':
      return 'DT'
    case 'reference':
      return 'Ref'
    case 'array':
      return '[]'
    case 'object':
    case 'json':
      return '{}'
    default:
      return '?'
  }
}

export function FieldPalette(): React.ReactElement {
  const { state, addSection, setDragSource } = useLayoutEditor()
  const { availableFields, placedFieldIds, sections } = state

  const [searchTerm, setSearchTerm] = useState('')

  const hasHighlightsPanel = useMemo(
    () => sections.some((s) => s.sectionType === 'HIGHLIGHTS_PANEL'),
    [sections]
  )

  const filteredFields = useMemo(() => {
    if (!searchTerm.trim()) return availableFields
    const lower = searchTerm.toLowerCase()
    return availableFields.filter(
      (f) => f.name.toLowerCase().includes(lower) || f.displayName.toLowerCase().includes(lower)
    )
  }, [availableFields, searchTerm])

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(e.target.value)
  }, [])

  const handleDragStart = useCallback(
    (e: React.DragEvent<HTMLDivElement>, field: AvailableField) => {
      if (placedFieldIds.has(field.id)) {
        e.preventDefault()
        return
      }
      const data = {
        type: 'palette-field',
        fieldId: field.id,
        fieldName: field.name,
        fieldType: field.type,
        fieldDisplayName: field.displayName,
      }
      e.dataTransfer.setData('application/json', JSON.stringify(data))
      e.dataTransfer.effectAllowed = 'move'
      setDragSource({ type: 'palette-field', fieldId: field.id })
    },
    [placedFieldIds, setDragSource]
  )

  const handleDragEnd = useCallback(() => {
    setDragSource(null)
  }, [setDragSource])

  const handleAddSection = useCallback(() => {
    addSection()
  }, [addSection])

  const handleAddHighlightsPanel = useCallback(() => {
    if (!hasHighlightsPanel) {
      addSection('HIGHLIGHTS_PANEL')
    }
  }, [addSection, hasHighlightsPanel])

  return (
    <div
      className="flex w-[240px] flex-col overflow-y-auto border-r border-border bg-muted max-md:w-full max-md:max-h-[200px] max-md:border-r-0 max-md:border-b"
      data-testid="field-palette"
    >
      <div className="border-b border-border px-4 py-3">
        <h3 className="mb-2 text-sm font-semibold text-foreground">Fields</h3>
        <input
          type="text"
          className="w-full rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 motion-reduce:transition-none"
          placeholder="Search fields..."
          value={searchTerm}
          onChange={handleSearchChange}
          aria-label="Search available fields"
          data-testid="field-palette-search"
        />
      </div>

      <div className="flex-1 overflow-y-auto p-2" data-testid="field-palette-list">
        {filteredFields.length === 0 ? (
          <div className="p-4 text-center text-[13px] text-muted-foreground">
            {searchTerm ? 'No fields match your search' : 'No fields available'}
          </div>
        ) : (
          filteredFields.map((field) => {
            const isPlaced = placedFieldIds.has(field.id)
            return (
              <div
                key={field.id}
                className={cn(
                  'flex items-center gap-2 rounded-md border border-transparent px-3 py-2 text-[13px] text-foreground cursor-grab transition-colors duration-150 motion-reduce:transition-none',
                  'hover:bg-muted-foreground/10',
                  'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px]',
                  'active:cursor-grabbing',
                  isPlaced && 'opacity-40 cursor-default'
                )}
                draggable={!isPlaced}
                onDragStart={(e) => handleDragStart(e, field)}
                onDragEnd={handleDragEnd}
                title={isPlaced ? `${field.displayName} (already placed)` : field.displayName}
                data-testid={`field-palette-item-${field.name}`}
              >
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded bg-muted-foreground/10 text-[10px] text-muted-foreground">
                  {getFieldTypeIcon(field.type)}
                </span>
                <span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap">
                  {field.displayName}
                </span>
              </div>
            )
          })
        )}
      </div>

      <div className="border-t border-border px-4 py-3">
        <button
          type="button"
          className="mb-2 w-full rounded-md border border-dashed border-input bg-background p-2 text-[13px] text-muted-foreground cursor-pointer transition-colors duration-150 hover:border-primary hover:text-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 disabled:opacity-40 disabled:cursor-not-allowed motion-reduce:transition-none"
          onClick={handleAddSection}
          data-testid="field-palette-add-section"
        >
          + Add Section
        </button>
        <button
          type="button"
          className="w-full rounded-md border border-dashed border-input bg-background p-2 text-[13px] text-muted-foreground cursor-pointer transition-colors duration-150 hover:border-primary hover:text-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 disabled:opacity-40 disabled:cursor-not-allowed motion-reduce:transition-none"
          onClick={handleAddHighlightsPanel}
          disabled={hasHighlightsPanel}
          title={hasHighlightsPanel ? 'A highlights panel already exists' : 'Add highlights panel'}
          data-testid="field-palette-add-highlights"
        >
          + Add Highlights Panel
        </button>
      </div>
    </div>
  )
}
