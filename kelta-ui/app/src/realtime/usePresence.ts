/**
 * usePresence (app-intelligence slice 3): live list of users present on a resource
 * (e.g. `record:orders/123`). Joins on mount, leaves on unmount/resource change,
 * re-joins automatically on socket reconnect (RealtimeClient owns that). Empty when
 * no realtime client is mounted (admin shell) or the resource is null.
 */
import { useEffect, useState } from 'react'
import { useRealtimeClient } from './RealtimeProvider'
import type { PresenceUser } from './RealtimeClient'

export function usePresence(resource: string | null): PresenceUser[] {
  const client = useRealtimeClient()
  const [users, setUsers] = useState<PresenceUser[]>([])

  useEffect(() => {
    if (!client || !resource) return
    const listener = (next: PresenceUser[]) => setUsers(next)
    client.joinPresence(resource, listener)
    return () => {
      client.leavePresence(resource, listener)
      // Reset in cleanup (never synchronously in the effect body — compiler rule)
      // so a resource/client change doesn't show the previous resource's viewers.
      setUsers([])
    }
  }, [client, resource])

  return users
}
