/**
 * Routing unit tests for the canvas DnD controller (slice 2c). Calls `onDragEnd` directly with synthetic
 * `active`/`over` payloads (no DOM drag) to pin the palette-vs-node decision in isolation:
 *  - palette source → `insertNode` at the drop container/index (NOT root, NOT moveNode),
 *  - existing node  → `moveNode` to the drop container/index,
 *  - dropped on nothing → no change,
 *  - no-op move → `onChange` not called (the §3.2 guard honored at the hook boundary).
 */
import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import type { DragEndEvent } from '@dnd-kit/core'
import type { PageComponent } from '../../model/pageModel'
import { useCanvasDnd } from './useCanvasDnd'
import '../../widgets/builtins'

function tree(): PageComponent[] {
  return [
    {
      id: 'g',
      type: 'grid',
      props: {},
      children: [
        {
          id: 'c1',
          type: 'column',
          props: {},
          span: { base: 6 },
          children: [{ id: 'h', type: 'heading', props: {} }],
        },
        { id: 'c2', type: 'column', props: {}, span: { base: 6 }, children: [] },
      ],
    },
  ]
}

function endEvent(
  active: Record<string, unknown>,
  over: Record<string, unknown> | null
): DragEndEvent {
  return {
    active: { id: 'x', data: { current: active } },
    over: over ? { id: 'y', data: { current: over } } : null,
  } as unknown as DragEndEvent
}

describe('useCanvasDnd onDragEnd routing', () => {
  it('palette source → inserts into the drop container at the slot index (not root)', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() => useCanvasDnd({ tree: tree(), onChange }))
    result.current.onDragEnd(
      endEvent({ source: 'palette', widgetType: 'text' }, { container: 'c1', index: 1 })
    )
    expect(onChange).toHaveBeenCalledTimes(1)
    const next = onChange.mock.calls[0][0] as PageComponent[]
    const c1 = next[0].children!.find((n) => n.id === 'c1')!
    expect(c1.children!.map((n) => n.type)).toEqual(['heading', 'text'])
    // The inserted node has the descriptor's defaultProps.
    expect(c1.children![1].props).toMatchObject({ content: '' })
  })

  it('node source → moves the node to the drop container/index', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() => useCanvasDnd({ tree: tree(), onChange }))
    result.current.onDragEnd(
      endEvent({ source: 'node', nodeId: 'h', parentId: 'c1' }, { container: 'c2', index: 0 })
    )
    expect(onChange).toHaveBeenCalledTimes(1)
    const next = onChange.mock.calls[0][0] as PageComponent[]
    const c1 = next[0].children!.find((n) => n.id === 'c1')!
    const c2 = next[0].children!.find((n) => n.id === 'c2')!
    expect(c1.children).toEqual([])
    expect(c2.children!.map((n) => n.id)).toEqual(['h'])
  })

  it('dropped on nothing → onChange not called', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() => useCanvasDnd({ tree: tree(), onChange }))
    result.current.onDragEnd(endEvent({ source: 'node', nodeId: 'h', parentId: 'c1' }, null))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('no-op move (dropped back in place) → onChange not called', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() => useCanvasDnd({ tree: tree(), onChange }))
    // 'h' is at c1 index 0; moving it to c1 index 0 is a no-op.
    result.current.onDragEnd(
      endEvent({ source: 'node', nodeId: 'h', parentId: 'c1' }, { container: 'c1', index: 0 })
    )
    expect(onChange).not.toHaveBeenCalled()
  })

  it('illegal move (into a descendant) is swallowed → onChange not called', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() => useCanvasDnd({ tree: tree(), onChange }))
    result.current.onDragEnd(
      endEvent({ source: 'node', nodeId: 'g', parentId: null }, { container: 'c1', index: 0 })
    )
    expect(onChange).not.toHaveBeenCalled()
  })

  it('palette drop selects the new node via onSelectNew', () => {
    const onChange = vi.fn()
    const onSelectNew = vi.fn()
    const { result } = renderHook(() => useCanvasDnd({ tree: tree(), onChange, onSelectNew }))
    result.current.onDragEnd(
      endEvent({ source: 'palette', widgetType: 'text' }, { container: null, index: 0 })
    )
    expect(onSelectNew).toHaveBeenCalledTimes(1)
    expect(typeof onSelectNew.mock.calls[0][0]).toBe('string')
  })
})
