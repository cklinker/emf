import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { IndexedDbOfflineStore, InMemoryOfflineStore, type OfflineStore } from './store'
import type { OutboxOp } from './types'

function op(id: string, queuedAt: string, collection = 'orders'): OutboxOp {
  return { id, collection, op: 'update', recordId: id, payload: { id }, queuedAt }
}

let dbCounter = 0

const factories: Array<[string, () => OfflineStore]> = [
  ['InMemoryOfflineStore', () => new InMemoryOfflineStore()],
  ['IndexedDbOfflineStore', () => new IndexedDbOfflineStore(`test-db-${++dbCounter}`)],
]

describe.each(factories)('%s (OfflineStore contract)', (_name, make) => {
  let store: OfflineStore

  beforeEach(() => {
    store = make()
  })

  it('returns null for an unset cursor and round-trips it', async () => {
    expect(await store.getCursor('orders')).toBeNull()
    await store.setCursor('orders', '2026-06-21T00:00:00Z')
    expect(await store.getCursor('orders')).toBe('2026-06-21T00:00:00Z')
  })

  it('stores, reads, and deletes records scoped per collection', async () => {
    await store.putRecords('orders', [
      { id: 'o1', total: 10 },
      { id: 'o2', total: 20 },
    ])
    await store.putRecords('customers', [{ id: 'c1', name: 'Acme' }])

    expect(await store.get('orders', 'o1')).toMatchObject({ id: 'o1', total: 10 })
    expect((await store.getAll('orders')).map((r) => r.id).sort()).toEqual(['o1', 'o2'])
    expect((await store.getAll('customers')).map((r) => r.id)).toEqual(['c1'])

    await store.deleteRecords('orders', ['o1'])
    expect(await store.get('orders', 'o1')).toBeUndefined()
    expect((await store.getAll('orders')).map((r) => r.id)).toEqual(['o2'])
  })

  it('upserts an existing record id rather than duplicating it', async () => {
    await store.putRecords('orders', [{ id: 'o1', total: 10 }])
    await store.putRecords('orders', [{ id: 'o1', total: 99 }])
    expect(await store.getAll('orders')).toHaveLength(1)
    expect(await store.get('orders', 'o1')).toMatchObject({ total: 99 })
  })

  it('returns the outbox in FIFO (queuedAt) order regardless of insert order', async () => {
    await store.enqueue(op('b', '2026-06-21T02:00:00Z'))
    await store.enqueue(op('a', '2026-06-21T01:00:00Z'))
    await store.enqueue(op('c', '2026-06-21T03:00:00Z'))

    expect((await store.listOutbox()).map((o) => o.id)).toEqual(['a', 'b', 'c'])

    await store.removeOutbox('a')
    expect((await store.listOutbox()).map((o) => o.id)).toEqual(['b', 'c'])
  })
})
