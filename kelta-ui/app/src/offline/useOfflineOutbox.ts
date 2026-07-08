/**
 * Live view of the offline outbox (Phase 4 slice 1): pending ops awaiting replay
 * and failed ops retained for review, with per-op retry/discard. Reacts to the
 * SyncEngine's onChange subscription (no polling). Inert (empty, no-op) outside
 * the OfflineProvider — admin pages keep their online-only behavior.
 */
import { useCallback, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useOffline } from './OfflineProvider'
import type { FailedOp, OutboxOp } from './types'

export interface UseOfflineOutboxReturn {
  /** Queued ops awaiting replay, FIFO. */
  pending: OutboxOp[]
  /** Rejected replays retained for review, oldest first. */
  failed: FailedOp[]
  pendingCount: number
  failedCount: number
  /** Re-queue a failed op (pushes immediately when online) and refresh the lists. */
  retry: (opId: string) => Promise<void>
  /** Drop a failed op permanently. */
  discard: (opId: string) => Promise<void>
}

export function useOfflineOutbox(): UseOfflineOutboxReturn {
  const offline = useOffline()
  const queryClient = useQueryClient()
  const [pending, setPending] = useState<OutboxOp[]>([])
  const [failed, setFailed] = useState<FailedOp[]>([])

  const engine = offline?.engine
  const store = offline?.store
  const online = offline?.online !== false

  useEffect(() => {
    if (!engine || !store) return
    let cancelled = false
    const refresh = () => {
      void Promise.all([store.listOutbox(), store.listFailed()]).then(([ops, fails]) => {
        if (cancelled) return
        setPending(ops)
        setFailed(fails)
      })
    }
    refresh()
    const unsubscribe = engine.onChange(refresh)
    return () => {
      cancelled = true
      unsubscribe()
    }
  }, [engine, store])

  const retry = useCallback(
    async (opId: string) => {
      if (!engine) return
      const requeued = await engine.retryFailed(opId)
      if (requeued && online) {
        await engine.push()
        // The replay may have created/updated records — show authoritative state.
        void queryClient.invalidateQueries({ queryKey: ['collection-records'] })
        void queryClient.invalidateQueries({ queryKey: ['record'] })
      }
    },
    [engine, online, queryClient]
  )

  const discard = useCallback(
    async (opId: string) => {
      if (!engine) return
      await engine.discardFailed(opId)
    },
    [engine]
  )

  return {
    pending,
    failed,
    pendingCount: pending.length,
    failedCount: failed.length,
    retry,
    discard,
  }
}
