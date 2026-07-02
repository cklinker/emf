/**
 * Offline replica provider (Rec 2B-2 wiring).
 *
 * Instantiates a tenant-scoped {@link IndexedDbOfflineStore} + {@link SyncEngine}
 * and exposes them to the end-user runtime so the shared data hooks can:
 *   - write-through cached reads into the replica while online,
 *   - serve the replica while offline,
 *   - queue mutations to the outbox while offline, and
 *   - flush the outbox + pull fresh on reconnect.
 *
 * Mounted only in the end-user shell (`EndUserShell`). Admin pages share the
 * same hooks but render outside this provider — {@link useOffline} returns
 * `undefined` there, so those hooks keep their online-only behavior unchanged.
 */
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { getTenantSlug } from '../context/TenantContext'
import { IndexedDbOfflineStore, InMemoryOfflineStore, type OfflineStore } from './store'
import { SyncEngine } from './syncEngine'
import { useOnlineStatus } from './useOnlineStatus'

export interface OfflineContextValue {
  /** The per-tenant replica store. */
  store: OfflineStore
  /** The sync engine (push/pull/queue), bound to `store` + the app `apiClient`. */
  engine: SyncEngine
  /** Live browser connectivity. */
  online: boolean
  /** Register a collection so reconnect knows to pull it. Called by read hooks. */
  registerCollection: (collection: string) => void
}

const OfflineContext = createContext<OfflineContextValue | undefined>(undefined)

export interface OfflineProviderProps {
  children: React.ReactNode
  /** Injectable store for tests/SSR; defaults to a tenant-scoped IndexedDB replica. */
  store?: OfflineStore
}

/** True when IndexedDB is available (false in SSR / some test envs). */
function hasIndexedDb(): boolean {
  return typeof indexedDB !== 'undefined'
}

export function OfflineProvider({
  children,
  store: storeProp,
}: OfflineProviderProps): React.ReactElement {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const online = useOnlineStatus()

  const tenantSlug = getTenantSlug()

  const store = useMemo<OfflineStore>(() => {
    if (storeProp) return storeProp
    return hasIndexedDb()
      ? new IndexedDbOfflineStore(`kelta-offline-${tenantSlug}`)
      : new InMemoryOfflineStore()
  }, [storeProp, tenantSlug])

  // `apiClient` structurally satisfies `SyncApi` (get/getPage/postResource/putResource/deleteResource).
  const engine = useMemo(() => new SyncEngine(store, apiClient), [store, apiClient])

  const knownCollections = useRef<Set<string>>(new Set())
  const registerCollection = useCallback((collection: string) => {
    if (collection) knownCollections.current.add(collection)
  }, [])

  // Reconnect flush: on a false→true transition, replay the outbox + pull fresh,
  // then invalidate the record queries so the UI shows authoritative server state.
  const prevOnline = useRef(online)
  const syncing = useRef(false)
  useEffect(() => {
    const wasOffline = prevOnline.current === false
    prevOnline.current = online
    if (!online || !wasOffline || syncing.current) return

    syncing.current = true
    void engine
      .sync([...knownCollections.current])
      .catch((err) => {
        console.warn('[offline] reconnect sync failed', err)
      })
      .finally(() => {
        syncing.current = false
        queryClient.invalidateQueries({ queryKey: ['collection-records'] })
        queryClient.invalidateQueries({ queryKey: ['record'] })
        queryClient.invalidateQueries({ queryKey: ['page-data'] })
      })
  }, [online, engine, queryClient])

  const value = useMemo<OfflineContextValue>(
    () => ({ store, engine, online, registerCollection }),
    [store, engine, online, registerCollection]
  )

  return <OfflineContext.Provider value={value}>{children}</OfflineContext.Provider>
}

/**
 * Access the offline replica context.
 *
 * Returns `undefined` when no {@link OfflineProvider} is mounted (e.g. admin
 * pages), letting the shared data hooks fall back to their online-only path.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useOffline(): OfflineContextValue | undefined {
  return useContext(OfflineContext)
}
