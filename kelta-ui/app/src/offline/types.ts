/**
 * Offline data sync — shared types (Rec 2B-2).
 *
 * The offline layer keeps a local IndexedDB replica of one or more user
 * collections so the PWA shell can read records while disconnected and
 * replay local mutations when the connection returns.
 *
 * The server contract this consumes:
 *  - Upserts: `GET /api/{collection}?filter[updatedAt][gt]=<cursor>&sort=updatedAt`
 *    — the existing authorized JSON:API path (Cerbos/FLS enforced server-side).
 *  - Deletions: `GET /api/{collection}/_changes?since=<cursor>` (Rec 2B-1)
 *    — returns `{ deletions, deletionCount, cursor }`.
 *
 * A single server-issued `cursor` (the `_changes` cursor, server-clock) is the
 * canonical sync watermark for a collection — both queries are driven from it,
 * so we never mix client and server clocks.
 */

/** A flattened business record as returned by `apiClient.getList`/`getPage`. */
export interface ReplicaRecord {
  id: string
  /** ISO-8601; used as the conflict tiebreaker. May be absent on legacy rows. */
  updatedAt?: string
  [key: string]: unknown
}

/** A locally-queued mutation awaiting replay to the server. */
export interface OutboxOp {
  /** Stable op id (FIFO key). */
  id: string
  collection: string
  op: 'create' | 'update' | 'delete'
  /** Target record id for update/delete; the optimistic temp id for create. */
  recordId?: string
  /** For create, the temp id under which the record is held locally. */
  tempId?: string
  /** Attribute payload for create/update. */
  payload?: Record<string, unknown>
  /** ISO-8601 enqueue time — defines replay order. */
  queuedAt: string
}

/**
 * An outbox op the server rejected on replay (409 conflict or other 4xx),
 * retained for user review instead of being silently dropped (Phase 4 slice 1).
 * 5xx/network failures are never retained — they stay queued and retry.
 */
export interface FailedOp extends OutboxOp {
  /** HTTP status the replay failed with, when known. */
  status?: number
  /** Human-readable server error. */
  error: string
  /** ISO-8601 time of the failed replay. */
  failedAt: string
}

/**
 * How to resolve a record that was edited both locally (pending outbox) and
 * on the server (pulled) since the last sync.
 */
export type ConflictPolicy = 'server-wins' | 'client-wins' | 'last-write-wins'

/** Shape of the `_changes` feed body (Rec 2B-1), unwrapped from `{ data }`. */
export interface ChangesFeed {
  deletions: string[]
  deletionCount: number
  cursor: string
}

/** Aggregate outcome of a sync pass. */
export interface SyncResult {
  /** Records written to the replica from the server. */
  pulled: number
  /** Records removed from the replica (tombstones). */
  deleted: number
  /** Outbox ops successfully replayed. */
  pushed: number
  /** Outbox ops that could not be replayed and remain queued or were dropped. */
  failed: number
  /** Records where a local edit and a server edit collided. */
  conflicts: number
}
