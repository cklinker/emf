/**
 * useSavedViews Hook
 *
 * Manages saved list views for collection record browsers.
 * Each view captures filter conditions, sort configuration,
 * visible columns, and page size. Views are persisted in
 * localStorage keyed by collection name.
 */

import { useState, useCallback } from 'react'

/**
 * A single filter condition within a saved view.
 */
export interface FilterCondition {
  id: string
  field: string
  operator: string
  value: string
}

/**
 * A saved list view configuration for a collection.
 */
export interface SavedView {
  id: string
  name: string
  collectionName: string
  filters: FilterCondition[]
  sortField: string | null
  sortDirection: 'asc' | 'desc'
  visibleColumns: string[]
  pageSize: number
  isDefault: boolean
  createdAt: string
}

export interface UseSavedViewsReturn {
  views: SavedView[]
  activeView: SavedView | null
  saveView: (
    name: string,
    config: Omit<SavedView, 'id' | 'name' | 'collectionName' | 'createdAt'>
  ) => void
  deleteView: (viewId: string) => void
  renameView: (viewId: string, newName: string) => void
  setDefaultView: (viewId: string) => void
  selectView: (viewId: string | null) => void
  getDefaultView: () => SavedView | null
}

function getStorageKey(collectionName: string): string {
  return `emf_views_${collectionName}`
}

function loadViews(collectionName: string): SavedView[] {
  try {
    const raw = localStorage.getItem(getStorageKey(collectionName))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function persistViews(collectionName: string, views: SavedView[]): void {
  try {
    localStorage.setItem(getStorageKey(collectionName), JSON.stringify(views))
  } catch {
    // localStorage full or unavailable
  }
}

/**
 * Hook to manage saved list views for a collection.
 * Views are stored in localStorage and include filters, sort,
 * visible columns, page size, and a default flag.
 *
 * On mount, the default view (if one exists) is automatically selected.
 *
 * @param collectionName - The collection these views belong to
 */
export function useSavedViews(collectionName: string): UseSavedViewsReturn {
  const [currentCollection, setCurrentCollection] = useState(collectionName)
  const [views, setViews] = useState<SavedView[]>(() => loadViews(collectionName))
  const [activeView, setActiveView] = useState<SavedView | null>(() => {
    const loaded = loadViews(collectionName)
    return loaded.find((v) => v.isDefault) ?? null
  })

  // Reload views when collectionName changes (derived state pattern)
  if (collectionName !== currentCollection) {
    setCurrentCollection(collectionName)
    const loaded = loadViews(collectionName)
    setViews(loaded)
    setActiveView(loaded.find((v) => v.isDefault) ?? null)
  }

  const saveView = useCallback(
    (name: string, config: Omit<SavedView, 'id' | 'name' | 'collectionName' | 'createdAt'>) => {
      setViews((prev) => {
        const existingIndex = prev.findIndex((v) => v.name === name)

        const newView: SavedView = {
          id: existingIndex >= 0 ? prev[existingIndex].id : crypto.randomUUID(),
          name,
          collectionName,
          filters: config.filters,
          sortField: config.sortField,
          sortDirection: config.sortDirection,
          visibleColumns: config.visibleColumns,
          pageSize: config.pageSize,
          isDefault: config.isDefault,
          createdAt: existingIndex >= 0 ? prev[existingIndex].createdAt : new Date().toISOString(),
        }

        let updated: SavedView[]
        if (existingIndex >= 0) {
          updated = [...prev]
          updated[existingIndex] = newView
        } else {
          updated = [...prev, newView]
        }

        // If this view is set as default, clear default from all others
        if (newView.isDefault) {
          updated = updated.map((v) => (v.id === newView.id ? v : { ...v, isDefault: false }))
        }

        persistViews(collectionName, updated)
        return updated
      })
    },
    [collectionName]
  )

  const deleteView = useCallback(
    (viewId: string) => {
      setViews((prev) => {
        const updated = prev.filter((v) => v.id !== viewId)
        persistViews(collectionName, updated)
        return updated
      })
      setActiveView((prev) => (prev?.id === viewId ? null : prev))
    },
    [collectionName]
  )

  const renameView = useCallback(
    (viewId: string, newName: string) => {
      setViews((prev) => {
        const updated = prev.map((v) => (v.id === viewId ? { ...v, name: newName } : v))
        persistViews(collectionName, updated)
        return updated
      })
      setActiveView((prev) => (prev?.id === viewId ? { ...prev, name: newName } : prev))
    },
    [collectionName]
  )

  const setDefaultView = useCallback(
    (viewId: string) => {
      setViews((prev) => {
        const updated = prev.map((v) => ({
          ...v,
          isDefault: v.id === viewId,
        }))
        persistViews(collectionName, updated)
        return updated
      })
      setActiveView((prev) => {
        if (prev?.id === viewId) {
          return { ...prev, isDefault: true }
        }
        if (prev) {
          return { ...prev, isDefault: false }
        }
        return prev
      })
    },
    [collectionName]
  )

  const selectView = useCallback((viewId: string | null) => {
    if (viewId === null) {
      setActiveView(null)
      return
    }
    setViews((prev) => {
      const found = prev.find((v) => v.id === viewId) ?? null
      setActiveView(found)
      return prev
    })
  }, [])

  const getDefaultView = useCallback((): SavedView | null => {
    return views.find((v) => v.isDefault) ?? null
  }, [views])

  return {
    views,
    activeView,
    saveView,
    deleteView,
    renameView,
    setDefaultView,
    selectView,
    getDefaultView,
  }
}
