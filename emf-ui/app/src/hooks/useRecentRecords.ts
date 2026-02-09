/**
 * useRecentRecords Hook
 *
 * Manages a list of recently viewed/edited records stored in localStorage.
 * Provides reactive updates across tabs via storage events.
 */

import { useState, useCallback, useEffect } from 'react'

/**
 * A recently viewed record entry
 */
export interface RecentRecord {
  id: string
  collectionName: string
  collectionDisplayName: string
  displayValue: string
  viewedAt: string
}

const MAX_ITEMS = 50
const STORAGE_KEY_PREFIX = 'emf_recent_'

function getStorageKey(userId: string): string {
  return `${STORAGE_KEY_PREFIX}${userId}`
}

function loadRecords(userId: string): RecentRecord[] {
  try {
    const raw = localStorage.getItem(getStorageKey(userId))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function saveRecords(userId: string, records: RecentRecord[]): void {
  try {
    localStorage.setItem(getStorageKey(userId), JSON.stringify(records))
  } catch {
    // localStorage full or unavailable
  }
}

export interface UseRecentRecordsReturn {
  recentRecords: RecentRecord[]
  addRecentRecord: (record: Omit<RecentRecord, 'viewedAt'>) => void
  clearRecentRecords: () => void
}

/**
 * Hook to manage recently viewed records.
 * Records are stored in localStorage and synced across tabs.
 *
 * @param userId - The current user's ID (used as storage key namespace)
 */
export function useRecentRecords(userId: string): UseRecentRecordsReturn {
  const [records, setRecords] = useState<RecentRecord[]>(() => loadRecords(userId))

  // Sync across tabs
  useEffect(() => {
    const handleStorage = (e: StorageEvent) => {
      if (e.key === getStorageKey(userId)) {
        setRecords(loadRecords(userId))
      }
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [userId])

  // Reload when userId changes
  useEffect(() => {
    setRecords(loadRecords(userId))
  }, [userId])

  const addRecentRecord = useCallback(
    (record: Omit<RecentRecord, 'viewedAt'>) => {
      setRecords((prev) => {
        // Remove duplicate (move to top)
        const filtered = prev.filter(
          (r) => !(r.id === record.id && r.collectionName === record.collectionName)
        )
        const entry: RecentRecord = { ...record, viewedAt: new Date().toISOString() }
        const updated = [entry, ...filtered].slice(0, MAX_ITEMS)
        saveRecords(userId, updated)
        return updated
      })
    },
    [userId]
  )

  const clearRecentRecords = useCallback(() => {
    setRecords([])
    try {
      localStorage.removeItem(getStorageKey(userId))
    } catch {
      // ignore
    }
  }, [userId])

  return { recentRecords: records, addRecentRecord, clearRecentRecords }
}
