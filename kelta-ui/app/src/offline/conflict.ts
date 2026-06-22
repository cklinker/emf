/**
 * Conflict resolution (Rec 2B-2) — pure, side-effect free.
 *
 * Decides the winner when a record has both a pending local edit (outbox) and
 * a newer server version pulled during sync.
 */
import type { ConflictPolicy, ReplicaRecord } from './types'

/**
 * Resolve a local-vs-server collision.
 *
 * @returns `'local'` to keep the local edit (skip the server version) or
 *          `'remote'` to accept the server version (overwriting the local edit).
 *
 * `last-write-wins` compares `updatedAt` (ISO-8601, lexicographically ordered);
 * the server wins ties and any case where a timestamp is missing/invalid, so an
 * ambiguous collision never silently discards authoritative server state.
 */
export function resolveConflict(
  local: ReplicaRecord,
  remote: ReplicaRecord,
  policy: ConflictPolicy
): 'local' | 'remote' {
  switch (policy) {
    case 'server-wins':
      return 'remote'
    case 'client-wins':
      return 'local'
    case 'last-write-wins': {
      const localTs = parseTimestamp(local.updatedAt)
      const remoteTs = parseTimestamp(remote.updatedAt)
      if (localTs === null || remoteTs === null) return 'remote'
      return localTs > remoteTs ? 'local' : 'remote'
    }
    default:
      return 'remote'
  }
}

function parseTimestamp(value: unknown): number | null {
  if (typeof value !== 'string') return null
  const ms = Date.parse(value)
  return Number.isNaN(ms) ? null : ms
}
