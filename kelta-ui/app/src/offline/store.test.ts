import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { openDB } from 'idb'
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

  it('returns undefined for an uncached page contract and round-trips one by slug', async () => {
    expect(await store.getPageContract('dashboard')).toBeUndefined()

    const contract = { version: '1.0', slug: 'dashboard', tree: { components: [] } }
    await store.putPageContract('dashboard', contract)
    expect(await store.getPageContract('dashboard')).toEqual(contract)

    // Upsert overwrites, and other slugs stay isolated.
    await store.putPageContract('dashboard', { ...contract, version: '2.0' })
    expect(await store.getPageContract('dashboard')).toMatchObject({ version: '2.0' })
    expect(await store.getPageContract('other')).toBeUndefined()
  })
})

describe('IndexedDbOfflineStore v1 → v2 upgrade', () => {
  it('keeps existing v1 records/cursors and adds the pages store', async () => {
    const dbName = `test-db-upgrade-${++dbCounter}`

    // Simulate a live v1 replica (records/cursors/outbox only — no `pages` store).
    const v1 = await openDB(dbName, 1, {
      upgrade(db) {
        const records = db.createObjectStore('records', { keyPath: ['collection', 'id'] })
        records.createIndex('by-collection', 'collection')
        db.createObjectStore('cursors', { keyPath: 'collection' })
        const outbox = db.createObjectStore('outbox', { keyPath: 'id' })
        outbox.createIndex('by-queuedAt', 'queuedAt')
      },
    })
    await v1.put('records', { collection: 'orders', id: 'o1', record: { id: 'o1', total: 10 } })
    await v1.put('cursors', { collection: 'orders', cursor: '2026-06-21T00:00:00Z' })
    v1.close()

    // Reopening through the store runs the v1→v2 upgrade path.
    const store = new IndexedDbOfflineStore(dbName)
    expect(await store.get('orders', 'o1')).toMatchObject({ id: 'o1', total: 10 })
    expect(await store.getCursor('orders')).toBe('2026-06-21T00:00:00Z')

    // The new pages store works, and records writes still do too.
    await store.putPageContract('home', { slug: 'home', tree: {} })
    expect(await store.getPageContract('home')).toMatchObject({ slug: 'home' })
    await store.putRecords('orders', [{ id: 'o2', total: 20 }])
    expect((await store.getAll('orders')).map((r) => r.id).sort()).toEqual(['o1', 'o2'])
  })
})
