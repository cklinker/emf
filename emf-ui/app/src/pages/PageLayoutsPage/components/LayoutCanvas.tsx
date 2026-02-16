/**
 * LayoutCanvas Component
 *
 * Center canvas panel for the layout editor. Renders sections
 * in sort order and provides drag-and-drop section reordering.
 */

import React, { useMemo, useCallback } from 'react'
import { useLayoutEditor } from './LayoutEditorContext'
import { LayoutSectionCard } from './LayoutSectionCard'
import styles from './LayoutCanvas.module.css'

export function LayoutCanvas(): React.ReactElement {
  const { state, addSection } = useLayoutEditor()
  const { sections } = state

  const sortedSections = useMemo(
    () => [...sections].sort((a, b) => a.sortOrder - b.sortOrder),
    [sections]
  )

  const handleAddSectionClick = useCallback(() => {
    addSection()
  }, [addSection])

  const handleAddSectionKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        addSection()
      }
    },
    [addSection]
  )

  return (
    <div className={styles.canvas} data-testid="layout-canvas">
      <div className={styles.canvasContent}>
        {sortedSections.length === 0 ? (
          <div className={styles.canvasEmpty} data-testid="layout-canvas-empty">
            <p>No sections yet.</p>
            <p>Drag fields from the palette or click &quot;Add Section&quot; to get started.</p>
          </div>
        ) : (
          sortedSections.map((section) => (
            <LayoutSectionCard key={section.id} sectionId={section.id} />
          ))
        )}

        <div
          className={styles.addSectionZone}
          onClick={handleAddSectionClick}
          onKeyDown={handleAddSectionKeyDown}
          role="button"
          tabIndex={0}
          data-testid="layout-canvas-add-section"
        >
          + Add Section
        </div>
      </div>
    </div>
  )
}
