/**
 * Editor undo/redo history (app-platform slice 4). Generic and dependency-free so the
 * page can snapshot its authored artifact ({components, variables, dataSources})
 * without an import cycle.
 *
 * Semantics:
 *  - `record` deep-clones the snapshot (structuredClone), skips JSON-identical
 *    snapshots, coalesces records within `coalesceMs` by replacing the top entry
 *    (typing bursts collapse to one undo step), truncates any redo tail, and caps the
 *    stack at `capacity` by dropping the oldest entry.
 *  - `undo`/`redo` move the cursor and return a clone of the entry (callers apply it
 *    to their state; mutating the return value never corrupts the history).
 *  - `reset` replaces the whole history with a single baseline entry (page open).
 */
import { useCallback, useRef, useState } from 'react'

export interface UseEditorHistoryReturn<T> {
  /** Replace the history with a single baseline entry (page open / reload). */
  reset: (baseline: T) => void
  /** Push the current state (deferred/coalesced by the caller's effect). */
  record: (snapshot: T) => void
  /** Step back; null at the oldest entry. */
  undo: () => T | null
  /** Step forward; null at the newest entry. */
  redo: () => T | null
  canUndo: boolean
  canRedo: boolean
}

const DEFAULT_CAPACITY = 50
const DEFAULT_COALESCE_MS = 400

export function useEditorHistory<T>(options?: {
  capacity?: number
  coalesceMs?: number
}): UseEditorHistoryReturn<T> {
  const capacity = options?.capacity ?? DEFAULT_CAPACITY
  const coalesceMs = options?.coalesceMs ?? DEFAULT_COALESCE_MS

  const entriesRef = useRef<T[]>([])
  const indexRef = useRef(-1)
  const lastRecordAtRef = useRef(0)
  // canUndo/canRedo mirror the refs into state (the react-compiler rule forbids
  // reading refs during render); every mutation recomputes them via bump().
  const [edges, setEdges] = useState({ canUndo: false, canRedo: false })
  const bump = useCallback(() => {
    setEdges({
      canUndo: indexRef.current > 0,
      canRedo: indexRef.current < entriesRef.current.length - 1,
    })
  }, [])

  const reset = useCallback(
    (baseline: T) => {
      entriesRef.current = [structuredClone(baseline)]
      indexRef.current = 0
      lastRecordAtRef.current = 0
      // Legal from the page's render-phase seed block (render-phase update of the
      // same component) and from event handlers alike.
      bump()
    },
    [bump]
  )

  const record = useCallback(
    (snapshot: T) => {
      const entries = entriesRef.current
      const top = indexRef.current >= 0 ? entries[indexRef.current] : undefined
      // Skip no-op snapshots (also swallows the seed echo after reset()).
      if (top !== undefined && JSON.stringify(top) === JSON.stringify(snapshot)) return

      const now = Date.now()
      const clone = structuredClone(snapshot)
      // Truncate any redo tail — a new edit forks the timeline.
      entries.length = indexRef.current + 1

      if (top !== undefined && now - lastRecordAtRef.current < coalesceMs) {
        // Coalesce a rapid burst into one undo step (replace the top entry)...
        // unless the top is the baseline (index 0 must stay undoable-to).
        if (indexRef.current > 0) {
          entries[indexRef.current] = clone
          lastRecordAtRef.current = now
          bump()
          return
        }
      }

      entries.push(clone)
      indexRef.current += 1
      if (entries.length > capacity) {
        entries.shift()
        indexRef.current -= 1
      }
      lastRecordAtRef.current = now
      bump()
    },
    [capacity, coalesceMs, bump]
  )

  const undo = useCallback((): T | null => {
    if (indexRef.current <= 0) return null
    indexRef.current -= 1
    // Break the coalescing window so the next edit starts a fresh entry.
    lastRecordAtRef.current = 0
    bump()
    return structuredClone(entriesRef.current[indexRef.current])
  }, [bump])

  const redo = useCallback((): T | null => {
    if (indexRef.current >= entriesRef.current.length - 1) return null
    indexRef.current += 1
    lastRecordAtRef.current = 0
    bump()
    return structuredClone(entriesRef.current[indexRef.current])
  }, [bump])

  return {
    reset,
    record,
    undo,
    redo,
    canUndo: edges.canUndo,
    canRedo: edges.canRedo,
  }
}
