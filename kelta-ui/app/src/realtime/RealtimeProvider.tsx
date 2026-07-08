import React, { useEffect, useMemo, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/context/AuthContext'
import { useConfig } from '@/context/ConfigContext'
import { useOnlineStatus } from '@/offline/useOnlineStatus'
import { buildNavTabs } from '@/shells/EndUserShell/navTabs'
import { MAX_SUBSCRIPTIONS, RealtimeClient, type RecordChangedEvent } from './RealtimeClient'
import { queryKeysForEvent } from './invalidation'

const DEBOUNCE_MS = 250

/** Builds the wss:// connect URL against the current origin with a fresh token. */
function buildSocketUrl(token: string): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws/realtime?token=${encodeURIComponent(token)}`
}

/**
 * Mounts one realtime socket for the end-user shell and turns record.changed events into
 * React Query invalidations (INVALIDATION-ONLY — pushed data never enters caches; the
 * server does no per-subscriber FLS). Subscribes to the nav collections plus the approval
 * collections; bursts are debounced per collection. Offline → socket closed; the offline
 * SyncEngine owns reconnect data sync, this provider just reconnects the socket.
 */
export function RealtimeProvider({ children }: { children: React.ReactNode }) {
  const { getAccessToken, isAuthenticated } = useAuth()
  const { config } = useConfig()
  const online = useOnlineStatus()
  const queryClient = useQueryClient()
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())

  const collections = useMemo(() => {
    const fromNav = buildNavTabs(config?.menus)
      .filter((tab) => tab.kind === 'collection')
      .map((tab) => tab.target)
    const set = new Set<string>([...fromNav, 'approval-instances', 'approval-step-instances'])
    return [...set].slice(0, MAX_SUBSCRIPTIONS)
  }, [config?.menus])

  useEffect(() => {
    if (!isAuthenticated || !online || collections.length === 0) return

    const timers = timersRef.current
    const handleEvent = (event: RecordChangedEvent) => {
      // Debounce per collection: one invalidation sweep per burst.
      const existing = timers.get(event.collection)
      if (existing) clearTimeout(existing)
      timers.set(
        event.collection,
        setTimeout(() => {
          timers.delete(event.collection)
          for (const key of queryKeysForEvent(event)) {
            void queryClient.invalidateQueries({ queryKey: key })
          }
        }, DEBOUNCE_MS)
      )
    }

    const client = new RealtimeClient({
      urlFactory: async () => buildSocketUrl(await getAccessToken()),
      onEvent: handleEvent,
    })
    for (const collection of collections) {
      client.subscribe(collection)
    }
    void client.connect()

    return () => {
      client.close()
      for (const timer of timers.values()) clearTimeout(timer)
      timers.clear()
    }
  }, [isAuthenticated, online, collections, getAccessToken, queryClient])

  return <>{children}</>
}
