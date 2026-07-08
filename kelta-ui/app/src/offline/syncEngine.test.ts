import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InMemoryOfflineStore } from './store'
import { SyncEngine, type SyncApi } from './syncEngine'
import type { ChangesFeed, ReplicaRecord } from './types'

class ApiStatusError extends Error {
  readonly status: number
  constructor(status: number) {
    super(`status ${status}`)
    this.status = status
  }
}

/** Programmable SyncApi double with call recording. */
class FakeApi implements SyncApi {
  changes: ChangesFeed = { deletions: [], deletionCount: 0, cursor: 'SERVER-CURSOR' }
  pages: ReplicaRecord[][] = [[]]
  calls: string[] = []
  post = vi.fn(async (url: string, data?: unknown) => {
    void url
    return { id: 'srv-1', ...(data as object) }
  })
  put = vi.fn(async (url: string, data?: unknown) => {
    void url
    return { id: 'x', ...(data as object) }
  })
  del = vi.fn(async (url: string) => {
    void url
    return undefined
  })

  async get<T>(url: string): Promise<T> {
    this.calls.push(`GET ${url}`)
    return { data: this.changes } as T
  }
  async getPage<T>(url: string): Promise<{ content: T[] }> {
    this.calls.push(`PAGE ${url}`)
    const n = Number(url.match(/page\[number\]=(\d+)/)?.[1] ?? 0)
    return { content: (this.pages[n] ?? []) as T[] }
  }
  postResource<T>(url: string, data?: unknown): Promise<T> {
    return this.post(url, data) as Promise<T>
  }
  putResource<T>(url: string, data?: unknown): Promise<T> {
    return this.put(url, data) as Promise<T>
  }
  deleteResource(url: string): Promise<void> {
    return this.del(url)
  }
}

let idSeq = 0
function engineFor(store: InMemoryOfflineStore, api: SyncApi, policy?: 'last-write-wins') {
  return new SyncEngine(store, api, {
    conflictPolicy: policy ?? 'last-write-wins',
    pageSize: 2,
    idGen: () => `id-${++idSeq}`,
    now: () => '2026-06-21T12:00:00Z',
  })
}

describe('SyncEngine.pull', () => {
  let store: InMemoryOfflineStore
  let api: FakeApi

  beforeEach(() => {
    idSeq = 0
    store = new InMemoryOfflineStore()
    api = new FakeApi()
  })

  it('initial sync (no cursor) pulls upserts, applies no filter, and sets the cursor', async () => {
    api.pages = [[{ id: 'o1', updatedAt: 't1' }]]

    const r = await engineFor(store, api).pull('orders')

    expect(r.pulled).toBe(1)
    expect((await store.getAll('orders')).map((x) => x.id)).toEqual(['o1'])
    expect(await store.getCursor('orders')).toBe('SERVER-CURSOR')
    // initial sync must not send an updatedAt filter or a `since`
    expect(api.calls.some((c) => c.includes('filter[updatedAt]'))).toBe(false)
    expect(api.calls.find((c) => c.startsWith('GET'))).toBe('GET /api/orders/_changes')
  })

  it('incremental sync applies deletions and filters upserts by the stored cursor', async () => {
    await store.setCursor('orders', '2026-06-20T00:00:00Z')
    await store.putRecords('orders', [{ id: 'gone', v: 1 }])
    api.changes = { deletions: ['gone'], deletionCount: 1, cursor: 'NEXT' }
    api.pages = [[{ id: 'o2', updatedAt: 't2' }]]

    const r = await engineFor(store, api).pull('orders')

    expect(r.deleted).toBe(1)
    expect(await store.get('orders', 'gone')).toBeUndefined()
    expect(await store.getCursor('orders')).toBe('NEXT')
    expect(
      api.calls.some(
        (c) => c.includes('filter%5BupdatedAt%5D%5Bgt%5D') || c.includes('filter[updatedAt][gt]')
      )
    ).toBe(true)
    expect(api.calls.some((c) => c.includes('since='))).toBe(true)
  })

  it('paginates until a short page is returned', async () => {
    api.pages = [
      [
        { id: 'a', updatedAt: 't' },
        { id: 'b', updatedAt: 't' },
      ],
      [{ id: 'c', updatedAt: 't' }],
    ]

    const r = await engineFor(store, api).pull('orders')

    expect(r.pulled).toBe(3)
    expect(api.calls.filter((c) => c.startsWith('PAGE')).length).toBe(2)
  })

  it('keeps a newer local edit over an older server version (last-write-wins)', async () => {
    await store.setCursor('orders', '2026-06-20T00:00:00Z')
    await store.putRecords('orders', [{ id: 'o1', updatedAt: '2026-06-21T15:00:00Z', local: true }])
    // a pending outbox edit marks o1 as locally dirty
    await store.enqueue({
      id: 'op1',
      collection: 'orders',
      op: 'update',
      recordId: 'o1',
      queuedAt: '2026-06-21T15:00:00Z',
    })
    api.pages = [[{ id: 'o1', updatedAt: '2026-06-21T09:00:00Z', local: false }]]

    const r = await engineFor(store, api).pull('orders')

    expect(r.conflicts).toBe(1)
    expect(await store.get('orders', 'o1')).toMatchObject({ local: true })
  })

  it('accepts a newer server version over a stale local edit', async () => {
    await store.setCursor('orders', '2026-06-20T00:00:00Z')
    await store.putRecords('orders', [{ id: 'o1', updatedAt: '2026-06-21T08:00:00Z', local: true }])
    await store.enqueue({
      id: 'op1',
      collection: 'orders',
      op: 'update',
      recordId: 'o1',
      queuedAt: '2026-06-21T08:00:00Z',
    })
    api.pages = [[{ id: 'o1', updatedAt: '2026-06-21T20:00:00Z', local: false }]]

    const r = await engineFor(store, api).pull('orders')

    expect(r.conflicts).toBe(1)
    expect(await store.get('orders', 'o1')).toMatchObject({ local: false })
  })
})

describe('SyncEngine.push', () => {
  let store: InMemoryOfflineStore
  let api: FakeApi

  beforeEach(() => {
    idSeq = 0
    store = new InMemoryOfflineStore()
    api = new FakeApi()
  })

  it('replays a create, swapping the optimistic temp record for the server record', async () => {
    const engine = engineFor(store, api)
    const queued = await engine.queue('orders', 'create', { payload: { name: 'New' } })
    expect(await store.get('orders', queued.tempId!)).toBeDefined()

    const r = await engine.push()

    expect(r.pushed).toBe(1)
    expect(api.post).toHaveBeenCalledWith('/api/orders', { name: 'New' })
    expect(await store.get('orders', queued.tempId!)).toBeUndefined()
    expect(await store.get('orders', 'srv-1')).toMatchObject({ name: 'New' })
    expect(await store.listOutbox()).toHaveLength(0)
  })

  it('replays update and delete ops in FIFO order and clears the outbox', async () => {
    const engine = engineFor(store, api)
    await store.putRecords('orders', [{ id: 'o1', v: 1 }])
    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })
    await engine.queue('orders', 'delete', { recordId: 'o2' })

    const r = await engine.push()

    expect(r.pushed).toBe(2)
    expect(api.put).toHaveBeenCalledWith('/api/orders/o1', { v: 2 })
    expect(api.del).toHaveBeenCalledWith('/api/orders/o2')
    expect(await store.listOutbox()).toHaveLength(0)
  })

  it('drops a 409 op from the queue but RETAINS it as a failed op (slice 1)', async () => {
    api.put.mockRejectedValueOnce(new ApiStatusError(409))
    const engine = engineFor(store, api)
    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })

    const r = await engine.push()

    expect(r).toMatchObject({ pushed: 0, conflicts: 1 })
    expect(await store.listOutbox()).toHaveLength(0)
    const failed = await store.listFailed()
    expect(failed).toHaveLength(1)
    expect(failed[0]).toMatchObject({ op: 'update', recordId: 'o1', status: 409 })
  })

  it('retains a permanently bad op (4xx) with its server error instead of dropping silently', async () => {
    api.put.mockRejectedValueOnce(new ApiStatusError(422))
    const engine = engineFor(store, api)
    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })

    const r = await engine.push()

    expect(r).toMatchObject({ pushed: 0, failed: 1 })
    expect(await store.listOutbox()).toHaveLength(0)
    const failed = await store.listFailed()
    expect(failed).toHaveLength(1)
    expect(failed[0]).toMatchObject({ status: 422, error: 'status 422' })
  })

  it('keeps the queue intact (and retains nothing) on a network/5xx error', async () => {
    api.put.mockRejectedValueOnce(new ApiStatusError(0))
    const engine = engineFor(store, api)
    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })
    await engine.queue('orders', 'delete', { recordId: 'o2' })

    const r = await engine.push()

    expect(r.failed).toBe(1)
    expect(api.del).not.toHaveBeenCalled() // stopped before the second op
    expect(await store.listOutbox()).toHaveLength(2)
    expect(await store.listFailed()).toHaveLength(0)
  })

  it('retryFailed re-queues the op and a subsequent push replays it', async () => {
    api.put.mockRejectedValueOnce(new ApiStatusError(422))
    const engine = engineFor(store, api)
    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })
    await engine.push()
    const failed = await store.listFailed()
    expect(failed).toHaveLength(1)

    const requeued = await engine.retryFailed(failed[0].id)
    expect(requeued).toMatchObject({ op: 'update', recordId: 'o1' })
    expect(await store.listFailed()).toHaveLength(0)
    expect(await store.listOutbox()).toHaveLength(1)

    const r = await engine.push()
    expect(r.pushed).toBe(1)
    expect(await store.listOutbox()).toHaveLength(0)
  })

  it('retryFailed returns null for an unknown id', async () => {
    const engine = engineFor(store, api)
    expect(await engine.retryFailed('nope')).toBeNull()
  })

  it('discardFailed drops the retained op permanently', async () => {
    api.put.mockRejectedValueOnce(new ApiStatusError(422))
    const engine = engineFor(store, api)
    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })
    await engine.push()
    const failed = await store.listFailed()

    await engine.discardFailed(failed[0].id)
    expect(await store.listFailed()).toHaveLength(0)
    expect(await store.listOutbox()).toHaveLength(0)
  })

  it('notifies onChange subscribers on queue, push, retry, and discard', async () => {
    api.put.mockRejectedValueOnce(new ApiStatusError(422))
    const engine = engineFor(store, api)
    const listener = vi.fn()
    const unsubscribe = engine.onChange(listener)

    await engine.queue('orders', 'update', { recordId: 'o1', payload: { v: 2 } })
    expect(listener).toHaveBeenCalledTimes(1)
    await engine.push() // fails with 422 → retained
    expect(listener).toHaveBeenCalledTimes(2)
    const failed = await store.listFailed()
    await engine.retryFailed(failed[0].id)
    expect(listener).toHaveBeenCalledTimes(3)
    await engine.push()
    expect(listener).toHaveBeenCalledTimes(4)

    unsubscribe()
    await engine.queue('orders', 'delete', { recordId: 'o9' })
    expect(listener).toHaveBeenCalledTimes(4)
  })
})

describe('SyncEngine.sync', () => {
  it('pushes then pulls each collection and aggregates the result', async () => {
    idSeq = 0
    const store = new InMemoryOfflineStore()
    const api = new FakeApi()
    api.pages = [[{ id: 'o1', updatedAt: 't1' }]]
    const engine = engineFor(store, api)
    await engine.queue('orders', 'delete', { recordId: 'o2' })

    const r = await engine.sync(['orders'])

    expect(r.pushed).toBe(1)
    expect(r.pulled).toBe(1)
    expect(api.del).toHaveBeenCalledWith('/api/orders/o2')
  })
})
