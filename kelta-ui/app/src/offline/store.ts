/**
 * Offline replica storage (Rec 2B-2).
 *
 * `OfflineStore` is the persistence seam the sync engine drives. The engine is
 * written against this interface (not IndexedDB) so its orchestration and
 * conflict logic is unit-testable with `InMemoryOfflineStore`; the
 * `IndexedDbOfflineStore` is the thin production implementation.
 */
import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import type { OutboxOp, ReplicaRecord } from './types'

export interface OfflineStore {
  /** Last sync watermark for a collection, or null if never synced. */
  getCursor(collection: string): Promise<string | null>
  setCursor(collection: string, cursor: string): Promise<void>

  /** All cached records for a collection. */
  getAll(collection: string): Promise<ReplicaRecord[]>
  get(collection: string, id: string): Promise<ReplicaRecord | undefined>
  putRecords(collection: string, records: ReplicaRecord[]): Promise<void>
  deleteRecords(collection: string, ids: string[]): Promise<void>

  /** Outbox (pending local mutations), returned in FIFO (queuedAt) order. */
  enqueue(op: OutboxOp): Promise<void>
  listOutbox(): Promise<OutboxOp[]>
  removeOutbox(opId: string): Promise<void>

  /** Cached page render contract for a custom-page slug (cold-offline page loads). */
  getPageContract(slug: string): Promise<unknown | undefined>
  putPageContract(slug: string, contract: unknown): Promise<void>
}

function byQueuedAt(a: OutboxOp, b: OutboxOp): number {
  return a.queuedAt < b.queuedAt ? -1 : a.queuedAt > b.queuedAt ? 1 : 0
}

// ---------------------------------------------------------------------------
// In-memory implementation — for tests and as a non-persistent fallback.
// ---------------------------------------------------------------------------

export class InMemoryOfflineStore implements OfflineStore {
  private cursors = new Map<string, string>()
  private records = new Map<string, Map<string, ReplicaRecord>>()
  private outbox = new Map<string, OutboxOp>()
  private pageContracts = new Map<string, unknown>()

  private bucket(collection: string): Map<string, ReplicaRecord> {
    let b = this.records.get(collection)
    if (!b) {
      b = new Map()
      this.records.set(collection, b)
    }
    return b
  }

  async getCursor(collection: string): Promise<string | null> {
    return this.cursors.get(collection) ?? null
  }

  async setCursor(collection: string, cursor: string): Promise<void> {
    this.cursors.set(collection, cursor)
  }

  async getAll(collection: string): Promise<ReplicaRecord[]> {
    return [...this.bucket(collection).values()]
  }

  async get(collection: string, id: string): Promise<ReplicaRecord | undefined> {
    return this.bucket(collection).get(id)
  }

  async putRecords(collection: string, records: ReplicaRecord[]): Promise<void> {
    const b = this.bucket(collection)
    for (const r of records) b.set(r.id, r)
  }

  async deleteRecords(collection: string, ids: string[]): Promise<void> {
    const b = this.bucket(collection)
    for (const id of ids) b.delete(id)
  }

  async enqueue(op: OutboxOp): Promise<void> {
    this.outbox.set(op.id, op)
  }

  async listOutbox(): Promise<OutboxOp[]> {
    return [...this.outbox.values()].sort(byQueuedAt)
  }

  async removeOutbox(opId: string): Promise<void> {
    this.outbox.delete(opId)
  }

  async getPageContract(slug: string): Promise<unknown | undefined> {
    return this.pageContracts.get(slug)
  }

  async putPageContract(slug: string, contract: unknown): Promise<void> {
    this.pageContracts.set(slug, contract)
  }
}

// ---------------------------------------------------------------------------
// IndexedDB implementation.
// ---------------------------------------------------------------------------

/** A record wrapper keyed by [collection, id] so one store holds all collections. */
interface RecordRow {
  collection: string
  id: string
  record: ReplicaRecord
}

interface CursorRow {
  collection: string
  cursor: string
}

/** Cached render contract for a custom-page slug, so the page shell loads cold-offline. */
interface PageContractRow {
  slug: string
  contract: unknown
  cachedAt: string
}

interface OfflineDbSchema extends DBSchema {
  records: {
    key: [string, string]
    value: RecordRow
    indexes: { 'by-collection': string }
  }
  cursors: {
    key: string
    value: CursorRow
  }
  outbox: {
    key: string
    value: OutboxOp
    indexes: { 'by-queuedAt': string }
  }
  pages: {
    key: string
    value: PageContractRow
  }
}

// v1: records/cursors/outbox · v2: + pages (custom-page render contracts).
const DB_VERSION = 2

export class IndexedDbOfflineStore implements OfflineStore {
  private dbPromise: Promise<IDBPDatabase<OfflineDbSchema>>

  /**
   * @param dbName per-tenant DB name (callers should namespace by tenant id to
   *               keep replicas isolated, e.g. `kelta-offline-<tenantId>`).
   */
  constructor(dbName = 'kelta-offline') {
    this.dbPromise = openDB<OfflineDbSchema>(dbName, DB_VERSION, {
      // Incremental per-version upgrade so both fresh DBs and live v1 replicas converge on v2
      // without dropping cached data.
      upgrade(db, oldVersion) {
        if (oldVersion < 1) {
          const records = db.createObjectStore('records', { keyPath: ['collection', 'id'] })
          records.createIndex('by-collection', 'collection')
          db.createObjectStore('cursors', { keyPath: 'collection' })
          const outbox = db.createObjectStore('outbox', { keyPath: 'id' })
          outbox.createIndex('by-queuedAt', 'queuedAt')
        }
        if (oldVersion < 2) {
          db.createObjectStore('pages', { keyPath: 'slug' })
        }
      },
    })
  }

  async getCursor(collection: string): Promise<string | null> {
    const row = await (await this.dbPromise).get('cursors', collection)
    return row?.cursor ?? null
  }

  async setCursor(collection: string, cursor: string): Promise<void> {
    await (await this.dbPromise).put('cursors', { collection, cursor })
  }

  async getAll(collection: string): Promise<ReplicaRecord[]> {
    const rows = await (
      await this.dbPromise
    ).getAllFromIndex('records', 'by-collection', collection)
    return rows.map((r) => r.record)
  }

  async get(collection: string, id: string): Promise<ReplicaRecord | undefined> {
    const row = await (await this.dbPromise).get('records', [collection, id])
    return row?.record
  }

  async putRecords(collection: string, records: ReplicaRecord[]): Promise<void> {
    const db = await this.dbPromise
    const tx = db.transaction('records', 'readwrite')
    await Promise.all(records.map((record) => tx.store.put({ collection, id: record.id, record })))
    await tx.done
  }

  async deleteRecords(collection: string, ids: string[]): Promise<void> {
    const db = await this.dbPromise
    const tx = db.transaction('records', 'readwrite')
    await Promise.all(ids.map((id) => tx.store.delete([collection, id])))
    await tx.done
  }

  async enqueue(op: OutboxOp): Promise<void> {
    await (await this.dbPromise).put('outbox', op)
  }

  async listOutbox(): Promise<OutboxOp[]> {
    const rows = await (await this.dbPromise).getAllFromIndex('outbox', 'by-queuedAt')
    return rows.sort(byQueuedAt)
  }

  async removeOutbox(opId: string): Promise<void> {
    await (await this.dbPromise).delete('outbox', opId)
  }

  async getPageContract(slug: string): Promise<unknown | undefined> {
    const row = await (await this.dbPromise).get('pages', slug)
    return row?.contract
  }

  async putPageContract(slug: string, contract: unknown): Promise<void> {
    await (
      await this.dbPromise
    ).put('pages', {
      slug,
      contract,
      cachedAt: new Date().toISOString(),
    })
  }
}
