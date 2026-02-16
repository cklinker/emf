/**
 * FieldPalette Component
 *
 * Left-side panel containing available fields that can be dragged
 * onto the layout canvas. Includes search, field type icons,
 * and buttons to add sections and highlights panels.
 */

import React, { useState, useMemo, useCallback } from 'react'
import { useLayoutEditor, type AvailableField } from './LayoutEditorContext'
import styles from './FieldPalette.module.css'

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
    <div className={styles.palette} data-testid="field-palette">
      <div className={styles.paletteHeader}>
        <h3 className={styles.paletteTitle}>Fields</h3>
        <input
          type="text"
          className={styles.searchInput}
          placeholder="Search fields..."
          value={searchTerm}
          onChange={handleSearchChange}
          aria-label="Search available fields"
          data-testid="field-palette-search"
        />
      </div>

      <div className={styles.fieldList} data-testid="field-palette-list">
        {filteredFields.length === 0 ? (
          <div
            style={{
              padding: '16px',
              textAlign: 'center',
              color: 'var(--color-text-placeholder, #9ca3af)',
              fontSize: '13px',
            }}
          >
            {searchTerm ? 'No fields match your search' : 'No fields available'}
          </div>
        ) : (
          filteredFields.map((field) => {
            const isPlaced = placedFieldIds.has(field.id)
            return (
              <div
                key={field.id}
                className={`${styles.fieldItem} ${isPlaced ? styles.fieldItemPlaced : ''}`}
                draggable={!isPlaced}
                onDragStart={(e) => handleDragStart(e, field)}
                onDragEnd={handleDragEnd}
                title={isPlaced ? `${field.displayName} (already placed)` : field.displayName}
                data-testid={`field-palette-item-${field.name}`}
              >
                <span className={styles.fieldIcon}>{getFieldTypeIcon(field.type)}</span>
                <span className={styles.fieldLabel}>{field.displayName}</span>
              </div>
            )
          })
        )}
      </div>

      <div className={styles.sectionButtons}>
        <button
          type="button"
          className={styles.addButton}
          onClick={handleAddSection}
          data-testid="field-palette-add-section"
        >
          + Add Section
        </button>
        <button
          type="button"
          className={styles.addButton}
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
