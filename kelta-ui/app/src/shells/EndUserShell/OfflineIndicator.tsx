/**
 * OfflineIndicator
 *
 * A thin banner shown in the end-user shell while the browser is offline, so
 * users understand that reads are served from cached data and any edits will
 * sync once connectivity returns. This is the first wiring of the offline
 * module (`@/offline`, Rec 2B) into `EndUserShell` — it consumes only
 * `useOnlineStatus` (no data-layer changes); cached reads + outbox flush on
 * reconnect land in follow-up slices.
 */

import React from 'react'
import { WifiOff } from 'lucide-react'
import { useOnlineStatus } from '@/offline'

export function OfflineIndicator(): React.ReactElement | null {
  const online = useOnlineStatus()
  if (online) return null
  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="offline-indicator"
      className="flex items-center justify-center gap-2 bg-amber-500/15 px-4 py-1.5 text-center text-xs font-medium text-amber-700 dark:text-amber-300"
    >
      <WifiOff className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>You&rsquo;re offline — showing saved data; changes sync when you reconnect.</span>
    </div>
  )
}

export default OfflineIndicator
