/**
 * AppContext
 *
 * Manages end-user application state: recent items, favorites,
 * and active app/tab selection. State is persisted to localStorage.
 */

import React, { createContext, useContext, useState, useCallback, useMemo, useEffect } from 'react'

// ---- Types ----

export interface RecentItem {
  /** Record ID */
  id: string
  /** Collection API name */
  collectionName: string
  /** Display label (e.g., record name) */
  label: string
  /** Timestamp of last access (ms since epoch) */
  timestamp: number
}

export interface Favorite {
  /** Unique key (e.g., "collection:accounts" or "record:accounts:abc-123") */
  key: string
  /** Display label */
  label: string
  /** Type of favorite */
  type: 'collection' | 'record' | 'listview'
  /** Collection API name */
  collectionName: string
  /** Record ID (for record favorites) */
  recordId?: string
}

export interface AppContextValue {
  /** Recently accessed records */
  recentItems: RecentItem[]
  /** User favorites */
  favorites: Favorite[]
  /** Add a record to recent items */
  addRecentItem: (item: Omit<RecentItem, 'timestamp'>) => void
  /** Toggle a favorite on/off */
  toggleFavorite: (item: Favorite) => void
  /** Check if an item is favorited */
  isFavorite: (key: string) => boolean
  /** Clear all recent items */
  clearRecentItems: () => void
}

// ---- Constants ----

const MAX_RECENT_ITEMS = 25
const RECENT_ITEMS_KEY = 'emf_recent_items'
const FAVORITES_KEY = 'emf_favorites'

// ---- Storage helpers ----

function loadFromStorage<T>(key: string, fallback: T): T {
  try {
    const stored = localStorage.getItem(key)
    if (stored) {
      return JSON.parse(stored) as T
    }
  } catch {
    // localStorage may not be available
  }
  return fallback
}

function saveToStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // localStorage may not be available
  }
}

// ---- Context ----

const AppContext = createContext<AppContextValue | undefined>(undefined)

interface AppContextProviderProps {
  children: React.ReactNode
}

export function AppContextProvider({ children }: AppContextProviderProps): React.ReactElement {
  const [recentItems, setRecentItems] = useState<RecentItem[]>(() =>
    loadFromStorage<RecentItem[]>(RECENT_ITEMS_KEY, [])
  )
  const [favorites, setFavorites] = useState<Favorite[]>(() =>
    loadFromStorage<Favorite[]>(FAVORITES_KEY, [])
  )

  // Persist recent items to localStorage
  useEffect(() => {
    saveToStorage(RECENT_ITEMS_KEY, recentItems)
  }, [recentItems])

  // Persist favorites to localStorage
  useEffect(() => {
    saveToStorage(FAVORITES_KEY, favorites)
  }, [favorites])

  const addRecentItem = useCallback((item: Omit<RecentItem, 'timestamp'>) => {
    setRecentItems((prev) => {
      // Remove existing entry for same record
      const filtered = prev.filter(
        (r) => !(r.id === item.id && r.collectionName === item.collectionName)
      )
      // Add to front with current timestamp
      const updated = [{ ...item, timestamp: Date.now() }, ...filtered]
      // Cap at max
      return updated.slice(0, MAX_RECENT_ITEMS)
    })
  }, [])

  const toggleFavorite = useCallback((item: Favorite) => {
    setFavorites((prev) => {
      const exists = prev.some((f) => f.key === item.key)
      if (exists) {
        return prev.filter((f) => f.key !== item.key)
      }
      return [...prev, item]
    })
  }, [])

  const isFavorite = useCallback(
    (key: string) => {
      return favorites.some((f) => f.key === key)
    },
    [favorites]
  )

  const clearRecentItems = useCallback(() => {
    setRecentItems([])
  }, [])

  const value = useMemo<AppContextValue>(
    () => ({
      recentItems,
      favorites,
      addRecentItem,
      toggleFavorite,
      isFavorite,
      clearRecentItems,
    }),
    [recentItems, favorites, addRecentItem, toggleFavorite, isFavorite, clearRecentItems]
  )

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

/**
 * Hook to access end-user application context.
 * @throws Error if used outside of AppContextProvider
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useAppContext(): AppContextValue {
  const context = useContext(AppContext)
  if (context === undefined) {
    throw new Error('useAppContext must be used within an AppContextProvider')
  }
  return context
}
