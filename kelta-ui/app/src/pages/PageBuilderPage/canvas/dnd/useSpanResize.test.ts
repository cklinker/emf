/**
 * Unit tests for the span-resize snapping (slice 2c). `snapSpan` is the pure snapping function; the hook
 * is exercised by firing synthetic pointer events and asserting it commits ONLY the snapped integer
 * `span.base` (never pixels). A sub-half-column delta rounds to 0 → no commit.
 */
import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { snapSpan, useSpanResize } from './useSpanResize'

describe('snapSpan', () => {
  it('snaps a positive delta to integer columns', () => {
    // gridWidth 1200 → colWidth 100. +250px → +3 cols (rounded from 2.5).
    expect(snapSpan(6, 250, 1200)).toBe(9)
  })

  it('clamps the result to 1..12', () => {
    expect(snapSpan(6, -700, 1200)).toBe(1) // 6 - 6 = 0 → clamp 1 (round(-700/100) = -7)
    expect(snapSpan(10, 700, 1200)).toBe(12) // 10 + 7 = 17 → clamp 12
  })

  it('rounds a sub-half-column delta to no change', () => {
    expect(snapSpan(6, 40, 1200)).toBe(6) // round(40/100) = 0
  })

  it('is a no-op when the grid width is unmeasurable', () => {
    expect(snapSpan(6, 250, 0)).toBe(6)
  })
})

/** Build a synthetic pointer-down event whose target sits inside a fixed-width grid track. */
function pointerDownAt(clientX: number, gridWidthPx: number): React.PointerEvent {
  const track = document.createElement('div')
  Object.defineProperty(track, 'clientWidth', { value: gridWidthPx, configurable: true })
  track.setAttribute('data-grid-track', 'true')
  const handle = document.createElement('button')
  track.appendChild(handle)
  document.body.appendChild(track)
  return {
    clientX,
    currentTarget: handle,
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
  } as unknown as React.PointerEvent
}

describe('useSpanResize hook', () => {
  it('commits the snapped base span on pointer up, never pixels', () => {
    const onCommit = vi.fn()
    const { result } = renderHook(() => useSpanResize({ span: { base: 6 }, onCommit }))

    result.current.handleProps.onPointerDown(pointerDownAt(500, 1200))
    // Move right ~3 cols and release.
    window.dispatchEvent(new PointerEvent('pointermove', { clientX: 750 }))
    window.dispatchEvent(new PointerEvent('pointerup', { clientX: 750 }))

    expect(onCommit).toHaveBeenCalledTimes(1)
    expect(onCommit).toHaveBeenCalledWith({ base: 9 })
  })

  it('does not commit when the snapped delta is zero', () => {
    const onCommit = vi.fn()
    const { result } = renderHook(() => useSpanResize({ span: { base: 6 }, onCommit }))

    result.current.handleProps.onPointerDown(pointerDownAt(500, 1200))
    window.dispatchEvent(new PointerEvent('pointerup', { clientX: 540 })) // +40px → 0 cols

    expect(onCommit).not.toHaveBeenCalled()
  })

  it('preserves other breakpoints while editing base', () => {
    const onCommit = vi.fn()
    const { result } = renderHook(() => useSpanResize({ span: { base: 6, md: 4 }, onCommit }))

    result.current.handleProps.onPointerDown(pointerDownAt(500, 1200))
    window.dispatchEvent(new PointerEvent('pointerup', { clientX: 700 })) // +200px → +2 cols

    expect(onCommit).toHaveBeenCalledWith({ base: 8, md: 4 })
  })
})
