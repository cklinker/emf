import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTableKeyboardNav } from './useTableKeyboardNav'

describe('useTableKeyboardNav', () => {
  it('initializes with focusedIndex = -1', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5 }))
    expect(result.current.focusedIndex).toBe(-1)
  })

  it('moves focus down on ArrowDown', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5 }))

    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)

    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(1)
  })

  it('moves focus up on ArrowUp', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5 }))

    // Move down first
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(1)

    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowUp',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)
  })

  it('jumps to first row on Home', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5 }))

    // Move to row 3
    for (let i = 0; i < 4; i++) {
      act(() => {
        result.current.handleKeyDown({
          key: 'ArrowDown',
          preventDefault: vi.fn(),
        } as unknown as React.KeyboardEvent)
      })
    }

    act(() => {
      result.current.handleKeyDown({
        key: 'Home',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)
  })

  it('jumps to last row on End', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5 }))

    act(() => {
      result.current.handleKeyDown({
        key: 'End',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(4)
  })

  it('calls onRowActivate on Enter', () => {
    const onRowActivate = vi.fn()
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5, onRowActivate }))

    // Focus first row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    // Press Enter
    act(() => {
      result.current.handleKeyDown({
        key: 'Enter',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(onRowActivate).toHaveBeenCalledWith(0)
  })

  it('calls onRowToggle on Space', () => {
    const onRowToggle = vi.fn()
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5, onRowToggle }))

    // Focus first row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    // Press Space
    act(() => {
      result.current.handleKeyDown({
        key: ' ',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(onRowToggle).toHaveBeenCalledWith(0)
  })

  it('clears focus on Escape', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 5 }))

    // Focus a row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)

    // Press Escape
    act(() => {
      result.current.handleKeyDown({
        key: 'Escape',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(-1)
  })

  it('does not move past last row', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 3 }))

    // Move to last row
    for (let i = 0; i < 5; i++) {
      act(() => {
        result.current.handleKeyDown({
          key: 'ArrowDown',
          preventDefault: vi.fn(),
        } as unknown as React.KeyboardEvent)
      })
    }

    expect(result.current.focusedIndex).toBe(2)
  })

  it('does not move above first row', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 3 }))

    // Focus first row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    // Try to move above
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowUp',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)
  })

  it('returns correct getRowProps', () => {
    const { result } = renderHook(() => useTableKeyboardNav({ rowCount: 3 }))

    // Before focus
    const unfocusedProps = result.current.getRowProps(0)
    expect(unfocusedProps.tabIndex).toBe(-1)
    expect(unfocusedProps['aria-selected']).toBe(false)
    expect(unfocusedProps['data-focused']).toBe(false)

    // Focus first row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    const focusedProps = result.current.getRowProps(0)
    expect(focusedProps.tabIndex).toBe(0)
    expect(focusedProps['aria-selected']).toBe(true)
    expect(focusedProps['data-focused']).toBe(true)

    // Non-focused row
    const otherProps = result.current.getRowProps(1)
    expect(otherProps.tabIndex).toBe(-1)
    expect(otherProps['aria-selected']).toBe(false)
  })

  it('does nothing when disabled', () => {
    const onRowActivate = vi.fn()
    const { result } = renderHook(() =>
      useTableKeyboardNav({ rowCount: 5, onRowActivate, enabled: false })
    )

    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(-1)
  })

  it('clamps focus when rowCount shrinks below focused index', () => {
    const { result, rerender } = renderHook(({ rowCount }) => useTableKeyboardNav({ rowCount }), {
      initialProps: { rowCount: 5 },
    })

    // Move to row index 4 (last row)
    act(() => {
      result.current.handleKeyDown({
        key: 'End',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(4)

    // Shrink row count below the focused index
    rerender({ rowCount: 3 })

    // focusedIndex should be clamped to -1 since 4 >= 3
    expect(result.current.focusedIndex).toBe(-1)
  })

  it('keeps focus when rowCount shrinks but index is still valid', () => {
    const { result, rerender } = renderHook(({ rowCount }) => useTableKeyboardNav({ rowCount }), {
      initialProps: { rowCount: 5 },
    })

    // Focus first row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)

    // Shrink row count but index 0 is still valid
    rerender({ rowCount: 3 })

    expect(result.current.focusedIndex).toBe(0)
  })

  it('resets focus when rowCount becomes 0', () => {
    const { result, rerender } = renderHook(({ rowCount }) => useTableKeyboardNav({ rowCount }), {
      initialProps: { rowCount: 5 },
    })

    // Focus a row
    act(() => {
      result.current.handleKeyDown({
        key: 'ArrowDown',
        preventDefault: vi.fn(),
      } as unknown as React.KeyboardEvent)
    })

    expect(result.current.focusedIndex).toBe(0)

    // Set row count to 0
    rerender({ rowCount: 0 })

    expect(result.current.focusedIndex).toBe(-1)
  })
})
