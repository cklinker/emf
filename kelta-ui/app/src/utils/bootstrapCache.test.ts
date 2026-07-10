/**
 * Unit tests for the bootstrap menu-item tree assembly (submenus via parentId).
 */
import { describe, it, expect } from 'vitest'
import { buildItemTree } from './bootstrapCache'

const item = (id: string, parentId?: string | null) => ({
  id,
  label: id,
  parentId: parentId ?? null,
})

describe('buildItemTree', () => {
  it('keeps a flat list flat', () => {
    const tree = buildItemTree([item('a'), item('b')])
    expect(tree.map((i) => i.id)).toEqual(['a', 'b'])
    expect(tree[0].children).toBeUndefined()
  })

  it('nests children under their parent preserving order', () => {
    const tree = buildItemTree([item('group'), item('a', 'group'), item('b', 'group'), item('c')])
    expect(tree.map((i) => i.id)).toEqual(['group', 'c'])
    const children = tree[0].children as Array<{ id: string }>
    expect(children.map((i) => i.id)).toEqual(['a', 'b'])
  })

  it('keeps an item with an unresolvable parentId at the top level', () => {
    const tree = buildItemTree([item('a', 'missing'), item('b')])
    expect(tree.map((i) => i.id)).toEqual(['a', 'b'])
  })

  it('falls back to the flat list when a cycle would swallow every item', () => {
    const tree = buildItemTree([item('a', 'b'), item('b', 'a')])
    expect(tree.map((i) => i.id)).toEqual(['a', 'b'])
    expect(tree[0].children).toBeUndefined()
  })

  it('ignores a self-referencing parentId', () => {
    const tree = buildItemTree([item('a', 'a')])
    expect(tree.map((i) => i.id)).toEqual(['a'])
  })
})
