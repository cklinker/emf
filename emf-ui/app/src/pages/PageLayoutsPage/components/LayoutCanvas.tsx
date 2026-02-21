/**
 * LayoutCanvas Component
 *
 * Center canvas panel for the layout editor. Renders sections
 * in sort order and provides drag-and-drop section reordering.
 */

import React, { useMemo, useCallback } from 'react'
import { useLayoutEditor } from './LayoutEditorContext'
import { LayoutSectionCard } from './LayoutSectionCard'

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
    <div
      className="flex-1 overflow-y-auto bg-muted p-6 min-h-0 max-md:p-3"
      data-testid="layout-canvas"
    >
      <div className="mx-auto flex max-w-[900px] flex-col gap-4 max-md:gap-3">
        {sortedSections.length === 0 ? (
          <div
            className="flex h-[300px] flex-col items-center justify-center rounded-xl border-2 border-dashed border-input text-center text-muted-foreground max-md:h-[200px]"
            data-testid="layout-canvas-empty"
          >
            <p>No sections yet.</p>
            <p>Drag fields from the palette or click &quot;Add Section&quot; to get started.</p>
          </div>
        ) : (
          sortedSections.map((section) => (
            <LayoutSectionCard key={section.id} sectionId={section.id} />
          ))
        )}

        <div
          className="cursor-pointer rounded-lg border-2 border-dashed border-input p-4 text-center text-[13px] text-muted-foreground transition-colors duration-150 hover:border-primary hover:text-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 motion-reduce:transition-none"
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
