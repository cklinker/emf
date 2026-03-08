/**
 * useTableKeyboardNav Hook
 *
 * Adds keyboard navigation to data tables:
 * - Arrow Up/Down: Navigate between rows
 * - Enter: Open the focused row (navigate to detail)
 * - Escape: Clear focus / close any open menu
 * - Space: Toggle selection of focused row
 * - Home/End: Jump to first/last row
 *
 * The hook returns a focusedIndex and handlers to wire into the table.
 */

import { useState, useCallback, useRef, useEffect, useMemo } from 'react'

export interface UseTableKeyboardNavOptions {
  /** Total number of rows in the table */
  rowCount: number
  /** Callback when Enter is pressed on a focused row */
  onRowActivate?: (index: number) => void
  /** Callback when Space is pressed on a focused row */
  onRowToggle?: (index: number) => void
  /** Whether the keyboard nav is enabled (default: true) */
  enabled?: boolean
}

export interface UseTableKeyboardNavReturn {
  /** Currently focused row index (-1 = no focus) */
  focusedIndex: number
  /** Set the focused row index */
  setFocusedIndex: (index: number) => void
  /** Key handler to attach to the table container */
  handleKeyDown: (e: React.KeyboardEvent) => void
  /** Ref to attach to the table container for focus management */
  tableRef: React.RefObject<HTMLDivElement | null>
  /** Get row props for a given index */
  getRowProps: (index: number) => {
    tabIndex: number
    'aria-selected': boolean
    'data-focused': boolean
    onFocus: () => void
  }
}

/**
 * Hook for adding keyboard navigation to data tables.
 *
 * Follows WAI-ARIA grid pattern for accessible table navigation.
 */
export function useTableKeyboardNav(
  options: UseTableKeyboardNavOptions
): UseTableKeyboardNavReturn {
  const { rowCount, onRowActivate, onRowToggle, enabled = true } = options
  const [rawFocusedIndex, setFocusedIndex] = useState(-1)
  const tableRef = useRef<HTMLDivElement | null>(null)

  // Clamp the focused index to valid range when rowCount changes.
  // If rowCount drops below the focused index, reset to -1.
  const focusedIndex = useMemo(() => {
    if (rawFocusedIndex === -1) return -1
    if (rowCount === 0) return -1
    if (rawFocusedIndex >= rowCount) return -1
    return rawFocusedIndex
  }, [rawFocusedIndex, rowCount])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!enabled || rowCount === 0) return

      switch (e.key) {
        case 'ArrowDown': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            const next = prev < rowCount - 1 ? prev + 1 : prev
            return next
          })
          break
        }

        case 'ArrowUp': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            const next = prev > 0 ? prev - 1 : 0
            return next
          })
          break
        }

        case 'Home': {
          e.preventDefault()
          setFocusedIndex(0)
          break
        }

        case 'End': {
          e.preventDefault()
          setFocusedIndex(rowCount - 1)
          break
        }

        case 'Enter': {
          e.preventDefault()
          if (focusedIndex >= 0 && onRowActivate) {
            onRowActivate(focusedIndex)
          }
          break
        }

        case ' ': {
          e.preventDefault()
          if (focusedIndex >= 0 && onRowToggle) {
            onRowToggle(focusedIndex)
          }
          break
        }

        case 'Escape': {
          e.preventDefault()
          setFocusedIndex(-1)
          // Blur the table to return focus to normal flow
          if (tableRef.current) {
            ;(document.activeElement as HTMLElement)?.blur()
          }
          break
        }
      }
    },
    [enabled, rowCount, focusedIndex, onRowActivate, onRowToggle, setFocusedIndex]
  )

  // Scroll the focused row into view
  useEffect(() => {
    if (focusedIndex < 0 || !tableRef.current) return

    const rows = tableRef.current.querySelectorAll('tbody tr')
    const row = rows[focusedIndex] as HTMLElement | undefined
    if (row) {
      row.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
      row.focus({ preventScroll: true })
    }
  }, [focusedIndex])

  const getRowProps = useCallback(
    (index: number) => ({
      tabIndex: index === focusedIndex ? 0 : -1,
      'aria-selected': index === focusedIndex,
      'data-focused': index === focusedIndex,
      onFocus: () => setFocusedIndex(index),
    }),
    [focusedIndex, setFocusedIndex]
  )

  return {
    focusedIndex,
    setFocusedIndex,
    handleKeyDown,
    tableRef,
    getRowProps,
  }
}
