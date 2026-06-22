/**
 * Offline data sync (Rec 2B-2) — public surface.
 *
 * Wiring sketch (consumed by the PWA end-user shell once deployed):
 *   const store = new IndexedDbOfflineStore(`kelta-offline-${tenantId}`)
 *   const engine = new SyncEngine(store, apiClient)
 *   // read cached rows offline: store.getAll(collection)
 *   // on reconnect (useOnlineStatus → true): engine.sync([collection])
 */
export type { ChangesFeed, ConflictPolicy, OutboxOp, ReplicaRecord, SyncResult } from './types'
export { resolveConflict } from './conflict'
export { InMemoryOfflineStore, IndexedDbOfflineStore, type OfflineStore } from './store'
export { SyncEngine, type SyncApi, type SyncEngineOptions } from './syncEngine'
export { useOnlineStatus } from './useOnlineStatus'
