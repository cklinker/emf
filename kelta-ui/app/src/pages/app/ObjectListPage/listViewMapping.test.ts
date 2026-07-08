import { describe, it, expect } from 'vitest'
import {
  isSharedViewId,
  mapSharedListView,
  orderFieldsByView,
  SHARED_VIEW_PREFIX,
} from './listViewMapping'

describe('mapSharedListView', () => {
  it('maps an admin list-views row into a shared SavedView', () => {
    const view = mapSharedListView(
      {
        id: 'lv-1',
        name: 'Hot deals',
        columns: ['name', 'stage', 'amount'],
        filters: [{ id: 'f1', field: 'stage', operator: 'equals', value: 'hot' }],
        sortField: 'amount',
        sortDirection: 'DESC',
        rowLimit: 50,
        isDefault: true,
      },
      'opportunities'
    )
    expect(view.id).toBe(`${SHARED_VIEW_PREFIX}lv-1`)
    expect(isSharedViewId(view.id)).toBe(true)
    expect(view.visibleColumns).toEqual(['name', 'stage', 'amount'])
    expect(view.filters).toHaveLength(1)
    expect(view.sortDirection).toBe('desc')
    expect(view.pageSize).toBe(50)
    expect(view.isDefault).toBe(true)
  })

  it('parses JSON-string columns and drops non-conforming filters', () => {
    const view = mapSharedListView(
      {
        id: 'lv-2',
        name: 'Weird',
        columns: '["a","b"]',
        filters: [
          { fieldName: 'x', op: 'EQ' },
          { field: 'ok', operator: 'equals', value: '1' },
        ],
        rowLimit: 37,
      },
      'orders'
    )
    expect(view.visibleColumns).toEqual(['a', 'b'])
    expect(view.filters).toHaveLength(1)
    expect(view.filters[0].field).toBe('ok')
    // invalid rowLimit falls back to the default page size
    expect(view.pageSize).toBe(25)
  })
})

describe('orderFieldsByView', () => {
  const fields = [{ name: 'a' }, { name: 'b' }, { name: 'c' }]

  it('orders and filters fields by the view columns', () => {
    expect(orderFieldsByView(fields, ['c', 'a', 'missing'])).toEqual([{ name: 'c' }, { name: 'a' }])
  })

  it('returns null when the view has no columns (caller falls back to first-6)', () => {
    expect(orderFieldsByView(fields, [])).toBeNull()
    expect(orderFieldsByView(fields, ['nope'])).toBeNull()
  })
})
