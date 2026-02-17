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
import { cn } from '@/lib/utils'

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
    <div
      className="flex items-center justify-between gap-3 border-b border-border bg-background px-4 py-3 max-md:flex-wrap max-md:px-3 max-md:py-2"
      data-testid="layout-toolbar"
    >
      <div className="flex items-center gap-3 max-md:flex-[1_1_100%]">
        <button
          type="button"
          className="border-none bg-transparent p-0 text-sm text-muted-foreground cursor-pointer no-underline transition-colors duration-150 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 focus-visible:rounded-sm motion-reduce:transition-none"
          onClick={onBack}
          aria-label="Back to layouts"
          data-testid="toolbar-back-button"
        >
          <ArrowLeft size={16} />
        </button>
        <h2 className="m-0 text-lg font-semibold text-foreground" data-testid="toolbar-layout-name">
          {layoutName}
          {isDirty && <span className="ml-1 text-destructive">*</span>}
        </h2>
      </div>

      <div className="flex items-center gap-2 max-md:flex-[1_1_100%] max-md:justify-end">
        <div className="flex gap-1">
          <button
            type="button"
            className="flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background p-0 text-foreground cursor-pointer transition-colors duration-150 hover:bg-muted focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 disabled:opacity-40 disabled:cursor-not-allowed motion-reduce:transition-none"
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
            className="flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background p-0 text-foreground cursor-pointer transition-colors duration-150 hover:bg-muted focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 disabled:opacity-40 disabled:cursor-not-allowed motion-reduce:transition-none"
            onClick={handleRedo}
            disabled={redoStack.length === 0}
            aria-label="Redo"
            title="Redo"
            data-testid="toolbar-redo-button"
          >
            <Redo2 size={16} />
          </button>
        </div>

        <div
          className="flex gap-0.5 overflow-hidden rounded-md border border-border"
          role="group"
          aria-label="Device preview"
        >
          {DEVICE_OPTIONS.map(({ device, icon: Icon, label }) => (
            <button
              key={device}
              type="button"
              className={cn(
                'border-none bg-background px-2.5 py-1.5 text-xs font-medium text-muted-foreground cursor-pointer transition-colors duration-150 motion-reduce:transition-none',
                'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px]',
                previewDevice === device ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'
              )}
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
          className="rounded-md border-none bg-primary px-4 py-2 text-sm font-medium text-primary-foreground cursor-pointer transition-colors duration-150 hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 disabled:opacity-50 disabled:cursor-not-allowed motion-reduce:transition-none"
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
