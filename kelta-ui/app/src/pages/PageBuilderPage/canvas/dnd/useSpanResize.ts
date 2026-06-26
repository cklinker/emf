/**
 * Pointer-based grid-span resize (slice 2c). NOT a dnd-kit drag (that would conflict with sorting) — a
 * lightweight pointer handler on the child's right-edge handle. It measures the parent grid container's
 * width ONLY at pointer-down (to compute the per-column width for snapping); the measured pixels are
 * NEVER persisted. On pointer up it calls `onCommit` with the snapped integer `span.base` (clamped
 * 1..12). Only `span` is stored — never px/row/column coords.
 */
import React, { useCallback, useRef } from 'react'
import type { ResponsiveSpan } from '../../model/pageModel'
import { clampSpan } from '../spanClasses'

export interface SpanResizeArgs {
  /** The current full span (base defaults to 12). */
  span: ResponsiveSpan | undefined
  /** Persist the new span (the canvas handle edits `base`). */
  onCommit: (next: ResponsiveSpan) => void
}

export interface SpanResizeHandleProps {
  onPointerDown: (event: React.PointerEvent) => void
}

/** Compute the snapped base span for a pixel delta against a measured grid width. */
export function snapSpan(currentBase: number, deltaPx: number, gridWidthPx: number): number {
  const colWidth = gridWidthPx / 12
  if (!Number.isFinite(colWidth) || colWidth <= 0) return clampSpan(currentBase)
  const deltaCols = Math.round(deltaPx / colWidth)
  return clampSpan(currentBase + deltaCols)
}

/** A hook returning the pointer-handle props that drive a snapped `span.base` resize. */
export function useSpanResize({ span, onCommit }: SpanResizeArgs): {
  handleProps: SpanResizeHandleProps
} {
  const startX = useRef(0)

  const onPointerDown = useCallback(
    (event: React.PointerEvent) => {
      event.preventDefault()
      event.stopPropagation()
      startX.current = event.clientX
      const startBase = span?.base ?? 12
      const startSpan = span
      // Measure the nearest grid track's width at gesture start (never persisted).
      const handleEl = event.currentTarget as HTMLElement
      const track = handleEl.closest<HTMLElement>('[data-grid-track="true"]')
      const gridWidthPx = track?.clientWidth ?? handleEl.parentElement?.clientWidth ?? 0

      const onMove = (e: PointerEvent) => {
        void e // live preview could be wired here; we commit on up.
      }
      const onUp = (e: PointerEvent) => {
        const next = snapSpan(startBase, e.clientX - startX.current, gridWidthPx)
        if (next !== startBase) {
          onCommit({ ...(startSpan ?? { base: startBase }), base: next })
        }
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    },
    [span, onCommit]
  )

  return { handleProps: { onPointerDown } }
}
