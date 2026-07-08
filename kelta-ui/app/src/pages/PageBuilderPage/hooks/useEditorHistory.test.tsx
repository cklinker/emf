/** useEditorHistory (app-platform slice 4): record/undo/redo, coalescing, caps, clones. */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useEditorHistory } from './useEditorHistory'

interface Snap {
  components: Array<{ id: string; props?: Record<string, unknown> }>
}

const snap = (id: string): Snap => ({ components: [{ id }] })

describe('useEditorHistory', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(0)
  })
  afterEach(() => vi.useRealTimers())

  function setup(options?: { capacity?: number; coalesceMs?: number }) {
    return renderHook(() => useEditorHistory<Snap>(options))
  }

  it('starts with nothing to undo or redo', () => {
    const { result } = setup()
    expect(result.current.canUndo).toBe(false)
    expect(result.current.canRedo).toBe(false)
    expect(result.current.undo()).toBeNull()
    expect(result.current.redo()).toBeNull()
  })

  it('records against a baseline and round-trips undo/redo', () => {
    const { result } = setup()
    act(() => result.current.reset(snap('base')))
    vi.setSystemTime(1000)
    act(() => result.current.record(snap('a')))
    expect(result.current.canUndo).toBe(true)

    let undone: Snap | null = null
    act(() => {
      undone = result.current.undo()
    })
    expect(undone).toEqual(snap('base'))
    expect(result.current.canRedo).toBe(true)

    let redone: Snap | null = null
    act(() => {
      redone = result.current.redo()
    })
    expect(redone).toEqual(snap('a'))
    expect(result.current.canRedo).toBe(false)
  })

  it('skips identical snapshots (the seed echo after reset)', () => {
    const { result } = setup()
    act(() => result.current.reset(snap('base')))
    act(() => result.current.record(snap('base')))
    expect(result.current.canUndo).toBe(false)
  })

  it('a new record truncates the redo tail', () => {
    const { result } = setup()
    act(() => result.current.reset(snap('base')))
    vi.setSystemTime(1000)
    act(() => result.current.record(snap('a')))
    act(() => result.current.undo())
    vi.setSystemTime(2000)
    act(() => result.current.record(snap('b')))
    expect(result.current.canRedo).toBe(false)
    let undone: Snap | null = null
    act(() => {
      undone = result.current.undo()
    })
    expect(undone).toEqual(snap('base'))
  })

  it('coalesces records inside the window into one undo step', () => {
    const { result } = setup({ coalesceMs: 400 })
    act(() => result.current.reset(snap('base')))
    vi.setSystemTime(1000)
    act(() => result.current.record(snap('a')))
    vi.setSystemTime(1200) // within 400ms of the last record
    act(() => result.current.record(snap('ab')))
    // One undo lands back on the baseline (a and ab merged).
    let undone: Snap | null = null
    act(() => {
      undone = result.current.undo()
    })
    expect(undone).toEqual(snap('base'))
    expect(result.current.canUndo).toBe(false)
  })

  it('records outside the window stay separate steps', () => {
    const { result } = setup({ coalesceMs: 400 })
    act(() => result.current.reset(snap('base')))
    vi.setSystemTime(1000)
    act(() => result.current.record(snap('a')))
    vi.setSystemTime(2000)
    act(() => result.current.record(snap('b')))
    let undone: Snap | null = null
    act(() => {
      undone = result.current.undo()
    })
    expect(undone).toEqual(snap('a'))
  })

  it('caps the stack by dropping the oldest entry', () => {
    const { result } = setup({ capacity: 3, coalesceMs: 0 })
    act(() => result.current.reset(snap('base')))
    for (let i = 1; i <= 5; i++) {
      vi.setSystemTime(i * 1000)
      act(() => result.current.record(snap(`s${i}`)))
    }
    // capacity 3 → entries [s3, s4, s5]; two undos reach the oldest retained entry.
    act(() => result.current.undo())
    let last: Snap | null = null
    act(() => {
      last = result.current.undo()
    })
    expect(last).toEqual(snap('s3'))
    expect(result.current.canUndo).toBe(false)
  })

  it('returned snapshots are clones — mutating them never corrupts history', () => {
    const { result } = setup()
    act(() => result.current.reset(snap('base')))
    vi.setSystemTime(1000)
    const recorded = snap('a')
    act(() => result.current.record(recorded))
    recorded.components[0].id = 'mutated-after-record'

    let undone: Snap | null = null
    act(() => {
      undone = result.current.undo()
    })
    undone!.components[0].id = 'mutated-after-undo'

    let redone: Snap | null = null
    act(() => {
      redone = result.current.redo()
    })
    expect(redone).toEqual(snap('a'))
  })

  it('reset clears both directions', () => {
    const { result } = setup()
    act(() => result.current.reset(snap('base')))
    vi.setSystemTime(1000)
    act(() => result.current.record(snap('a')))
    act(() => result.current.undo())
    act(() => result.current.reset(snap('fresh')))
    expect(result.current.canUndo).toBe(false)
    expect(result.current.canRedo).toBe(false)
  })
})
