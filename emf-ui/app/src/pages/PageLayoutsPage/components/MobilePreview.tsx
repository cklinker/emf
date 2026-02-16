/**
 * MobilePreview Component
 *
 * Wraps the layout canvas in a constrained viewport based on
 * the currently selected preview device (desktop/tablet/mobile).
 * Provides device-frame chrome for tablet and mobile views.
 */

import React from 'react'
import { useLayoutEditor } from './LayoutEditorContext'
import { LayoutCanvas } from './LayoutCanvas'
import styles from './MobilePreview.module.css'

export function MobilePreview(): React.ReactElement {
  const { state } = useLayoutEditor()
  const { previewDevice } = state

  if (previewDevice === 'desktop') {
    return (
      <div
        className={`${styles.previewContainer} ${styles.previewDesktop}`}
        data-testid="mobile-preview-desktop"
      >
        <LayoutCanvas />
      </div>
    )
  }

  if (previewDevice === 'tablet') {
    return (
      <div
        className={`${styles.previewContainer} ${styles.previewTablet}`}
        data-testid="mobile-preview-tablet"
      >
        <div className={styles.tabletFrame}>
          <LayoutCanvas />
        </div>
      </div>
    )
  }

  // Mobile
  return (
    <div
      className={`${styles.previewContainer} ${styles.previewMobile}`}
      data-testid="mobile-preview-mobile"
    >
      <div className={styles.phoneFrame}>
        <div className={styles.phoneNotch} />
        <div className={styles.phoneContent}>
          <LayoutCanvas />
        </div>
      </div>
    </div>
  )
}
