/**
 * Offline sync engine (Rec 2B-2).
 *
 * Drives the replica through a sync pass:
 *   1. push() — replay queued local mutations (outbox) to the server, FIFO.
 *   2. pull(collection) — apply server deletions (`_changes`) then upserts
 *      (`filter[updatedAt][gt]`), resolving collisions against pending edits.
 *
 * The engine depends only on the {@link OfflineStore} seam and a minimal
 * {@link SyncApi} (structurally satisfied by `ApiClient`), so it is fully
 * unit-testable without IndexedDB or a live backend.
 */
import { resolveConflict } from './conflict'
import type { OfflineStore } from './store'
import type { ChangesFeed, ConflictPolicy, OutboxOp, ReplicaRecord, SyncResult } from './types'

/** Minimal subset of `ApiClient` the engine needs (structurally compatible). */
export interface SyncApi {
  get<T = unknown>(url: string): Promise<T>
  getPage<T = unknown>(url: string): Promise<{ content: T[] }>
  postResource<T = unknown>(url: string, data?: unknown): Promise<T>
  putResource<T = unknown>(url: string, data?: unknown): Promise<T>
  deleteResource(url: string): Promise<void>
}

export interface SyncEngineOptions {
  conflictPolicy?: ConflictPolicy
  pageSize?: number
  /** Injectable for deterministic tests. */
  idGen?: () => string
  now?: () => string
}

/** Thrown errors that carry an HTTP status (e.g. `ApiError`). */
function statusOf(error: unknown): number | undefined {
  if (error && typeof error === 'object' && 'status' in error) {
    const s = (error as { status?: unknown }).status
    return typeof s === 'number' ? s : undefined
  }
  return undefined
}

const MAX_PAGES = 50

export class SyncEngine {
  private readonly policy: ConflictPolicy
  private readonly pageSize: number
  private readonly idGen: () => string
  private readonly now: () => string

  private readonly store: OfflineStore
  private readonly api: SyncApi

  constructor(store: OfflineStore, api: SyncApi, opts: SyncEngineOptions = {}) {
    this.store = store
    this.api = api
    this.policy = opts.conflictPolicy ?? 'last-write-wins'
    this.pageSize = opts.pageSize ?? 200
    this.idGen = opts.idGen ?? (() => globalThis.crypto.randomUUID())
    this.now = opts.now ?? (() => new Date().toISOString())
  }

  /** Full pass: push local changes, then pull each collection. */
  async sync(collections: string[]): Promise<SyncResult> {
    const pushed = await this.push()
    const result: SyncResult = { ...pushed, pulled: 0, deleted: 0 }
    for (const collection of collections) {
      const r = await this.pull(collection)
      result.pulled += r.pulled
      result.deleted += r.deleted
      result.conflicts += r.conflicts
    }
    return result
  }

  /**
   * Queue a local mutation and apply it optimistically to the replica.
   * Returns the queued op (its id, and for creates the temp record id).
   */
  async queue(
    collection: string,
    op: 'create' | 'update' | 'delete',
    args: { recordId?: string; payload?: Record<string, unknown> }
  ): Promise<OutboxOp> {
    const entry: OutboxOp = {
      id: this.idGen(),
      collection,
      op,
      recordId: args.recordId,
      payload: args.payload,
      queuedAt: this.now(),
    }

    if (op === 'create') {
      const tempId = `tmp-${this.idGen()}`
      entry.tempId = tempId
      entry.recordId = tempId
      await this.store.putRecords(collection, [
        { id: tempId, ...(args.payload ?? {}), updatedAt: entry.queuedAt },
      ])
    } else if (op === 'update' && args.recordId) {
      const existing = await this.store.get(collection, args.recordId)
      await this.store.putRecords(collection, [
        {
          ...(existing ?? {}),
          id: args.recordId,
          ...(args.payload ?? {}),
          updatedAt: entry.queuedAt,
        },
      ])
    } else if (op === 'delete' && args.recordId) {
      await this.store.deleteRecords(collection, [args.recordId])
    }

    await this.store.enqueue(entry)
    return entry
  }

  /** Replay every queued outbox op to the server, FIFO. */
  async push(): Promise<{ pushed: number; failed: number; conflicts: number }> {
    const ops = await this.store.listOutbox()
    let pushed = 0
    let failed = 0
    let conflicts = 0

    for (const op of ops) {
      try {
        if (op.op === 'create') {
          const created = await this.api.postResource<ReplicaRecord>(
            `/api/${op.collection}`,
            op.payload
          )
          if (op.tempId) await this.store.deleteRecords(op.collection, [op.tempId])
          if (created?.id) await this.store.putRecords(op.collection, [created])
        } else if (op.op === 'update' && op.recordId) {
          const updated = await this.api.putResource<ReplicaRecord>(
            `/api/${op.collection}/${op.recordId}`,
            op.payload
          )
          if (updated?.id) await this.store.putRecords(op.collection, [updated])
        } else if (op.op === 'delete' && op.recordId) {
          await this.api.deleteResource(`/api/${op.collection}/${op.recordId}`)
        }
        await this.store.removeOutbox(op.id)
        pushed++
      } catch (error) {
        const status = statusOf(error)
        if (status === 409) {
          // Server rejected on a version/uniqueness conflict — drop the op and
          // let the subsequent pull bring authoritative server state down.
          conflicts++
          await this.store.removeOutbox(op.id)
        } else if (status !== undefined && status >= 400 && status < 500) {
          // Permanently bad op (validation/authz) — drop so it can't wedge the queue.
          failed++
          await this.store.removeOutbox(op.id)
        } else {
          // Network or 5xx — keep queued and stop, so order is preserved on retry.
          failed++
          break
        }
      }
    }

    return { pushed, failed, conflicts }
  }

  /** Pull server-side deletions then upserts for one collection. */
  async pull(collection: string): Promise<{ pulled: number; deleted: number; conflicts: number }> {
    const cursor = await this.store.getCursor(collection)

    // 1) Deletions feed (Rec 2B-1). Its server-clock cursor becomes the new watermark.
    const changesUrl = `/api/${collection}/_changes${
      cursor ? `?since=${encodeURIComponent(cursor)}` : ''
    }`
    const feed = await this.api.get<{ data: ChangesFeed }>(changesUrl)
    const deletions = feed?.data?.deletions ?? []
    const nextCursor = feed?.data?.cursor ?? cursor ?? ''
    if (deletions.length) await this.store.deleteRecords(collection, deletions)

    // 2) Upserts via the existing authorized JSON:API path (Cerbos/FLS enforced).
    const pending = new Set(
      (await this.store.listOutbox())
        .filter((o) => o.collection === collection && o.recordId)
        .map((o) => o.recordId as string)
    )

    let pulled = 0
    let conflicts = 0
    for (let page = 0; page < MAX_PAGES; page++) {
      const filter = cursor ? `&filter[updatedAt][gt]=${encodeURIComponent(cursor)}` : ''
      const url = `/api/${collection}?sort=updatedAt&page[size]=${this.pageSize}&page[number]=${page}${filter}`
      const res = await this.api.getPage<ReplicaRecord>(url)
      const items = res.content ?? []

      const toPut: ReplicaRecord[] = []
      for (const remote of items) {
        if (pending.has(remote.id)) {
          const local = await this.store.get(collection, remote.id)
          if (local && resolveConflict(local, remote, this.policy) === 'local') {
            conflicts++
            continue // keep the local edit; it will replay on the next push
          }
          conflicts++
        }
        toPut.push(remote)
      }
      if (toPut.length) await this.store.putRecords(collection, toPut)
      pulled += toPut.length

      if (items.length < this.pageSize) break
    }

    await this.store.setCursor(collection, nextCursor)
    return { pulled, deleted: deletions.length, conflicts }
  }
}
