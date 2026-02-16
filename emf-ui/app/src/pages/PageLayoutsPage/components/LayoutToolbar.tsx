/**
 * LayoutToolbar Component
 *
 * Toolbar at the top of the WYSIWYG layout editor.
 * Provides navigation (back), layout name display, undo/redo,
 * device preview toggle, and save controls.
 */

import React, { useCallback } from 'react'
import { ArrowLeft, Undo2, Redo2, Monitor, Tablet, Smartphone } from 'lucide-react'
import { useLayoutEditor, type PreviewDevice } from './LayoutEditorContext'
import styles from './LayoutToolbar.module.css'

export interface LayoutToolbarProps {
  onBack: () => void
  layoutName: string
  onSave: () => void
  isSaving: boolean
}

const DEVICE_OPTIONS: {
  device: PreviewDevice
  icon: React.ComponentType<{ size?: number }>
  label: string
}[] = [
  { device: 'desktop', icon: Monitor, label: 'Desktop' },
  { device: 'tablet', icon: Tablet, label: 'Tablet' },
  { device: 'mobile', icon: Smartphone, label: 'Mobile' },
]

export function LayoutToolbar({
  onBack,
  layoutName,
  onSave,
  isSaving,
}: LayoutToolbarProps): React.ReactElement {
  const { state, undo, redo, setPreviewDevice } = useLayoutEditor()
  const { isDirty, undoStack, redoStack, previewDevice } = state

  const handleUndo = useCallback(() => {
    undo()
  }, [undo])

  const handleRedo = useCallback(() => {
    redo()
  }, [redo])

  const handleDeviceChange = useCallback(
    (device: PreviewDevice) => {
      setPreviewDevice(device)
    },
    [setPreviewDevice]
  )

  return (
    <div className={styles.toolbar} data-testid="layout-toolbar">
      <div className={styles.toolbarLeft}>
        <button
          type="button"
          className={styles.backButton}
          onClick={onBack}
          aria-label="Back to layouts"
          data-testid="toolbar-back-button"
        >
          <ArrowLeft size={16} />
        </button>
        <h2 className={styles.layoutTitle} data-testid="toolbar-layout-name">
          {layoutName}
          {isDirty && <span className={styles.unsavedIndicator}>*</span>}
        </h2>
      </div>

      <div className={styles.toolbarRight}>
        <div className={styles.undoRedoGroup}>
          <button
            type="button"
            className={styles.iconButton}
            onClick={handleUndo}
            disabled={undoStack.length === 0}
            aria-label="Undo"
            title="Undo"
            data-testid="toolbar-undo-button"
          >
            <Undo2 size={16} />
          </button>
          <button
            type="button"
            className={styles.iconButton}
            onClick={handleRedo}
            disabled={redoStack.length === 0}
            aria-label="Redo"
            title="Redo"
            data-testid="toolbar-redo-button"
          >
            <Redo2 size={16} />
          </button>
        </div>

        <div className={styles.deviceToggle} role="group" aria-label="Device preview">
          {DEVICE_OPTIONS.map(({ device, icon: Icon, label }) => (
            <button
              key={device}
              type="button"
              className={`${styles.deviceButton} ${previewDevice === device ? styles.deviceButtonActive : ''}`}
              onClick={() => handleDeviceChange(device)}
              aria-label={label}
              aria-pressed={previewDevice === device}
              title={label}
              data-testid={`toolbar-device-${device}`}
            >
              <Icon size={16} />
            </button>
          ))}
        </div>

        <button
          type="button"
          className={styles.saveButton}
          onClick={onSave}
          disabled={isSaving || !isDirty}
          data-testid="toolbar-save-button"
        >
          {isSaving ? 'Saving...' : 'Save'}
        </button>
      </div>
    </div>
  )
}
