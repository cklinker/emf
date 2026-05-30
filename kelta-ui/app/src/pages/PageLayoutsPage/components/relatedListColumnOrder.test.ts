import { describe, it, expect } from 'vitest'
import {
  computeDisplayOrder,
  reorderColumns,
  selectedInDisplayOrder,
  swapAdjacent,
} from './relatedListColumnOrder'

describe('computeDisplayOrder', () => {
  it('returns available fields in natural order when no user order is set', () => {
    expect(computeDisplayOrder(['a', 'b', 'c'], [])).toEqual(['a', 'b', 'c'])
  })

  it('floats user-arranged fields to the front in their stored order', () => {
    expect(computeDisplayOrder(['a', 'b', 'c', 'd'], ['c', 'a'])).toEqual(['c', 'a', 'b', 'd'])
  })

  it('drops stored names that are no longer available', () => {
    expect(computeDisplayOrder(['a', 'b'], ['c', 'a'])).toEqual(['a', 'b'])
  })

  it('appends newly available fields after the user-arranged prefix', () => {
    expect(computeDisplayOrder(['a', 'b', 'newField'], ['b', 'a'])).toEqual(['b', 'a', 'newField'])
  })
})

describe('reorderColumns', () => {
  it('moves dragged item into the target position', () => {
    expect(reorderColumns(['a', 'b', 'c', 'd'], 'd', 'b')).toEqual(['a', 'd', 'b', 'c'])
  })

  it('returns a fresh copy when the move is a no-op', () => {
    const current = ['a', 'b', 'c']
    const result = reorderColumns(current, 'a', 'a')
    expect(result).toEqual(current)
    expect(result).not.toBe(current)
  })

  it('returns a fresh copy when either name is missing', () => {
    const current = ['a', 'b', 'c']
    expect(reorderColumns(current, 'missing', 'a')).toEqual(current)
    expect(reorderColumns(current, 'a', 'missing')).toEqual(current)
  })

  it('handles moving the first item to the last position', () => {
    expect(reorderColumns(['a', 'b', 'c'], 'a', 'c')).toEqual(['b', 'c', 'a'])
  })
})

describe('swapAdjacent', () => {
  it('moves the item up when direction is -1', () => {
    expect(swapAdjacent(['a', 'b', 'c'], 2, -1)).toEqual(['a', 'c', 'b'])
  })

  it('moves the item down when direction is +1', () => {
    expect(swapAdjacent(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c'])
  })

  it('is a no-op at the top boundary', () => {
    expect(swapAdjacent(['a', 'b', 'c'], 0, -1)).toEqual(['a', 'b', 'c'])
  })

  it('is a no-op at the bottom boundary', () => {
    expect(swapAdjacent(['a', 'b', 'c'], 2, 1)).toEqual(['a', 'b', 'c'])
  })
})

describe('selectedInDisplayOrder', () => {
  it('filters selected names to the order from displayOrder', () => {
    expect(selectedInDisplayOrder(['a', 'b', 'c', 'd'], ['d', 'b'])).toEqual(['b', 'd'])
  })

  it('drops selected names that are not in displayOrder', () => {
    expect(selectedInDisplayOrder(['a', 'b'], ['x', 'a'])).toEqual(['a'])
  })

  it('returns an empty array when nothing is selected', () => {
    expect(selectedInDisplayOrder(['a', 'b'], [])).toEqual([])
  })
})
