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
import styles from './PropertyPanel.module.css'

export function PropertyPanel(): React.ReactElement {
  const { state } = useLayoutEditor()
  const { selectedSectionId, selectedFieldPlacementId } = state

  let content: React.ReactNode

  if (selectedFieldPlacementId) {
    content = (
      <>
        <div className={styles.panelHeader}>
          <h3 className={styles.panelTitle}>Field Properties</h3>
        </div>
        <div className={styles.panelContent}>
          <FieldPropertyForm fieldPlacementId={selectedFieldPlacementId} />
        </div>
      </>
    )
  } else if (selectedSectionId) {
    content = (
      <>
        <div className={styles.panelHeader}>
          <h3 className={styles.panelTitle}>Section Properties</h3>
        </div>
        <div className={styles.panelContent}>
          <SectionPropertyForm sectionId={selectedSectionId} />
        </div>
      </>
    )
  } else {
    content = (
      <div className={styles.panelEmpty} data-testid="property-panel-empty">
        Select a field or section to edit properties
      </div>
    )
  }

  return (
    <div className={styles.panel} data-testid="property-panel">
      {content}
    </div>
  )
}
