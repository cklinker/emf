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

export function MobilePreview(): React.ReactElement {
  const { state } = useLayoutEditor()
  const { previewDevice } = state

  if (previewDevice === 'desktop') {
    return (
      <div
        className="flex flex-1 justify-center overflow-y-auto bg-muted p-6 min-h-0 w-full max-w-full max-md:p-3"
        data-testid="mobile-preview-desktop"
      >
        <LayoutCanvas />
      </div>
    )
  }

  if (previewDevice === 'tablet') {
    return (
      <div
        className="flex flex-1 justify-center overflow-y-auto bg-muted p-6 min-h-0 w-full max-w-[768px] max-md:p-3"
        data-testid="mobile-preview-tablet"
      >
        <div className="rounded-xl border-2 border-border bg-background p-3 shadow-md">
          <LayoutCanvas />
        </div>
      </div>
    )
  }

  // Mobile
  return (
    <div
      className="flex flex-1 justify-center overflow-y-auto bg-muted p-6 min-h-0 w-full max-w-[375px] max-md:p-3"
      data-testid="mobile-preview-mobile"
    >
      <div className="relative rounded-3xl border-[3px] border-gray-800 bg-background p-2 shadow-lg">
        <div className="relative flex justify-center pb-2 pt-1 before:h-1.5 before:w-20 before:rounded-full before:bg-gray-800 before:content-['']" />
        <div className="min-h-[500px] overflow-hidden rounded-2xl">
          <LayoutCanvas />
        </div>
      </div>
    </div>
  )
}
