/**
 * Unit tests for the pure tree mutations (slice 2c). Every function returns a NEW immutable tree; the
 * input is deep-frozen so an accidental mutation throws. `moveNode`/`setSpan` were the 2c additions;
 * `insertNode`/`removeNode`/`updateProps` round out the suite.
 */
import { describe, it, expect } from 'vitest'
import type { PageComponent } from './pageModel'
import { insertNode, removeNode, updateProps, moveNode, setSpan, findNode } from './treeOps'

/** Deep-freeze a tree so any in-place mutation by an op throws (proves immutability). */
function deepFreeze<T>(value: T): T {
  if (value && typeof value === 'object') {
    Object.values(value).forEach(deepFreeze)
    Object.freeze(value)
  }
  return value
}

function sample(): PageComponent[] {
  return [
    { id: 'a', type: 'heading', props: {} },
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
          children: [{ id: 'h', type: 'text', props: {} }],
        },
        { id: 'c2', type: 'column', props: {}, span: { base: 6 }, children: [] },
      ],
    },
  ]
}

describe('treeOps.insertNode', () => {
  it('appends to root when no index', () => {
    const tree = deepFreeze(sample())
    const next = insertNode(tree, { id: 'z', type: 'text', props: {} }, null)
    expect(next.map((n) => n.id)).toEqual(['a', 'g', 'z'])
    expect(tree).toHaveLength(2) // unmutated
  })

  it('inserts at a specific root index', () => {
    const tree = deepFreeze(sample())
    const next = insertNode(tree, { id: 'z', type: 'text', props: {} }, null, 0)
    expect(next.map((n) => n.id)).toEqual(['z', 'a', 'g'])
  })

  it('inserts under a parent at an index', () => {
    const tree = deepFreeze(sample())
    const next = insertNode(tree, { id: 'z', type: 'text', props: {} }, 'c1', 0)
    expect(findNode(next, 'c1')!.node.children!.map((n) => n.id)).toEqual(['z', 'h'])
  })

  it('out-of-range index appends', () => {
    const tree = deepFreeze(sample())
    const next = insertNode(tree, { id: 'z', type: 'text', props: {} }, 'c2', 99)
    expect(findNode(next, 'c2')!.node.children!.map((n) => n.id)).toEqual(['z'])
  })
})

describe('treeOps.removeNode', () => {
  it('removes at root', () => {
    const tree = deepFreeze(sample())
    expect(removeNode(tree, 'a').map((n) => n.id)).toEqual(['g'])
  })

  it('removes a deeply nested node', () => {
    const tree = deepFreeze(sample())
    const next = removeNode(tree, 'h')
    expect(findNode(next, 'c1')!.node.children).toEqual([])
    expect(findNode(next, 'h')).toBeNull()
  })

  it('is a no-op (equivalent tree) for a missing id', () => {
    const tree = deepFreeze(sample())
    const next = removeNode(tree, 'missing')
    expect(next.map((n) => n.id)).toEqual(['a', 'g'])
  })
})

describe('treeOps.updateProps', () => {
  it('shallow-merges the patch into props', () => {
    const tree = deepFreeze([{ id: 'a', type: 'heading', props: { text: 'x', level: 'h2' } }])
    const next = updateProps(tree, 'a', { text: 'y' })
    expect(next[0].props).toEqual({ text: 'y', level: 'h2' })
  })

  it('is a no-op on a missing id', () => {
    const tree = deepFreeze(sample())
    const next = updateProps(tree, 'missing', { x: 1 })
    expect(next.map((n) => n.id)).toEqual(['a', 'g'])
  })
})

describe('treeOps.setSpan', () => {
  it('sets a span on a node', () => {
    const tree = deepFreeze(sample())
    const next = setSpan(tree, 'a', { base: 4 })
    expect(findNode(next, 'a')!.node.span).toEqual({ base: 4 })
  })

  it('overwrites an existing span', () => {
    const tree = deepFreeze(sample())
    const next = setSpan(tree, 'c1', { base: 8, md: 4 })
    expect(findNode(next, 'c1')!.node.span).toEqual({ base: 8, md: 4 })
  })

  it('clears the span key with undefined', () => {
    const tree = deepFreeze(sample())
    const next = setSpan(tree, 'c1', undefined)
    expect(findNode(next, 'c1')!.node.span).toBeUndefined()
    expect('span' in findNode(next, 'c1')!.node).toBe(false)
  })

  it('is a no-op on a missing id', () => {
    const tree = deepFreeze(sample())
    expect(setSpan(tree, 'missing', { base: 4 }).map((n) => n.id)).toEqual(['a', 'g'])
  })
})

describe('treeOps.moveNode', () => {
  it('reorders within the same parent (down-by-one is stable)', () => {
    const tree = deepFreeze([
      {
        id: 'r',
        type: 'row',
        props: {},
        children: [
          { id: 'A', type: 'text', props: {} },
          { id: 'B', type: 'text', props: {} },
          { id: 'C', type: 'text', props: {} },
        ],
      },
    ] as PageComponent[])
    const next = moveNode(tree, 'A', 'r', 1)
    expect(findNode(next, 'r')!.node.children!.map((n) => n.id)).toEqual(['B', 'A', 'C'])
  })

  it('reorders C to index 0 within a parent', () => {
    const tree = deepFreeze([
      {
        id: 'r',
        type: 'row',
        props: {},
        children: [
          { id: 'A', type: 'text', props: {} },
          { id: 'B', type: 'text', props: {} },
          { id: 'C', type: 'text', props: {} },
        ],
      },
    ] as PageComponent[])
    const next = moveNode(tree, 'C', 'r', 0)
    expect(findNode(next, 'r')!.node.children!.map((n) => n.id)).toEqual(['C', 'A', 'B'])
  })

  it('moves into another container at index 0', () => {
    const tree = deepFreeze(sample())
    const next = moveNode(tree, 'h', 'c2', 0)
    expect(findNode(next, 'c1')!.node.children).toEqual([])
    expect(findNode(next, 'c2')!.node.children!.map((n) => n.id)).toEqual(['h'])
  })

  it('moves to root', () => {
    const tree = deepFreeze(sample())
    const next = moveNode(tree, 'h', null, 0)
    expect(next[0].id).toBe('h')
    expect(findNode(next, 'c1')!.node.children).toEqual([])
  })

  it('never duplicates or loses a node when moving between containers', () => {
    const tree = deepFreeze(sample())
    const next = moveNode(tree, 'h', 'c2', 0)
    const count = (nodes: PageComponent[]): number =>
      nodes.reduce((acc, n) => acc + 1 + (n.children ? count(n.children) : 0), 0)
    expect(count(next)).toBe(count(tree))
  })

  it('no-op guard: same parent + same index returns the INPUT reference', () => {
    const tree = deepFreeze(sample())
    const found = findNode(tree, 'a')!
    void found
    // 'a' is at root index 0; moving it to root index 0 is a no-op.
    expect(moveNode(tree, 'a', null, 0)).toBe(tree)
  })

  it('cycle guard: moving a node into itself throws', () => {
    const tree = deepFreeze(sample())
    expect(() => moveNode(tree, 'g', 'g', 0)).toThrow(/itself or a descendant/)
  })

  it('cycle guard: moving a node into a descendant throws', () => {
    const tree = deepFreeze(sample())
    expect(() => moveNode(tree, 'g', 'c1', 0)).toThrow(/itself or a descendant/)
  })

  it('returns the input tree when the node is absent', () => {
    const tree = deepFreeze(sample())
    expect(moveNode(tree, 'missing', null, 0)).toBe(tree)
  })
})
