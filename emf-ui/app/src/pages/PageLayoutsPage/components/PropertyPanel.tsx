/**
 * PropertyPanel Component
 *
 * Right-side panel for editing properties of the currently selected
 * section or field placement. Delegates to SectionPropertyForm or
 * FieldPropertyForm based on selection state.
 */

import React from 'react'
import { useLayoutEditor } from './LayoutEditorContext'
import { SectionPropertyForm } from './SectionPropertyForm'
import { FieldPropertyForm } from './FieldPropertyForm'

export function PropertyPanel(): React.ReactElement {
  const { state } = useLayoutEditor()
  const { selectedSectionId, selectedFieldPlacementId } = state

  let content: React.ReactNode

  if (selectedFieldPlacementId) {
    content = (
      <>
        <div className="border-b border-border px-4 py-3">
          <h3 className="m-0 text-sm font-semibold text-foreground">Field Properties</h3>
        </div>
        <div className="flex flex-col gap-4 p-4">
          <FieldPropertyForm fieldPlacementId={selectedFieldPlacementId} />
        </div>
      </>
    )
  } else if (selectedSectionId) {
    content = (
      <>
        <div className="border-b border-border px-4 py-3">
          <h3 className="m-0 text-sm font-semibold text-foreground">Section Properties</h3>
        </div>
        <div className="flex flex-col gap-4 p-4">
          <SectionPropertyForm sectionId={selectedSectionId} />
        </div>
      </>
    )
  } else {
    content = (
      <div
        className="px-4 py-6 text-center text-[13px] text-muted-foreground"
        data-testid="property-panel-empty"
      >
        Select a field or section to edit properties
      </div>
    )
  }

  return (
    <div
      className="flex w-[300px] flex-col overflow-y-auto border-l border-border bg-background max-md:w-full max-md:max-h-[300px] max-md:border-l-0 max-md:border-t"
      data-testid="property-panel"
    >
      {content}
    </div>
  )
}
