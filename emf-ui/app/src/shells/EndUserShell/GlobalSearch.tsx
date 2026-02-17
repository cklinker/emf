/**
 * GlobalSearch Component
 *
 * A command palette (Cmd+K) for searching across all collections.
 * Uses shadcn's Command dialog with grouped search results.
 *
 * Features:
 * - Debounced search across configured collections
 * - Results grouped by collection type
 * - Recent items shown when no query
 * - Navigation shortcuts (Home, Setup)
 * - Full-page search link for deeper exploration
 */

import React, { useState, useCallback, useMemo, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Search, FileText, ArrowRight, Clock, Database, Settings, Home } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command'
import { Badge } from '@/components/ui/badge'
import { useApi } from '@/context/ApiContext'
import { useConfig } from '@/context/ConfigContext'
import { useAppContext } from '@/context/AppContext'
import { unwrapCollection } from '@/utils/jsonapi'

interface SearchResult {
  id: string
  collectionName: string
  collectionLabel: string
  displayValue: string
}

/**
 * Extract collection tabs from menu config.
 */
function getCollectionNames(
  config: { menus?: Array<{ items?: Array<{ path?: string; label?: string }> }> } | null
): Array<{ name: string; label: string }> {
  if (!config?.menus) return []
  const collections: Array<{ name: string; label: string }> = []
  for (const menu of config.menus) {
    if (menu.items) {
      for (const item of menu.items) {
        if (item.path?.startsWith('/resources/')) {
          const name = item.path.replace('/resources/', '').split('/')[0]
          if (name) {
            collections.push({
              name,
              label: item.label || name.charAt(0).toUpperCase() + name.slice(1),
            })
          }
        }
      }
    }
  }
  return collections
}

/**
 * Format a timestamp into relative time.
 */
function formatRelativeTime(timestamp: number): string {
  const diffMs = Date.now() - timestamp
  const diffMinutes = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffMinutes < 1) return 'just now'
  if (diffMinutes < 60) return `${diffMinutes}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays === 1) return 'yesterday'
  return `${diffDays}d ago`
}

/**
 * Extract a display value from a flat record object.
 * Looks for common display fields, then falls back to id.
 */
function extractDisplayValue(record: Record<string, unknown>): string {
  const displayFields = ['name', 'title', 'label', 'subject', 'displayName', 'display_name']
  for (const field of displayFields) {
    if (record[field] && typeof record[field] === 'string') {
      return record[field] as string
    }
  }
  return String(record.id || 'Unnamed')
}

/**
 * Simple debounce hook.
 */
function useDebounced(value: string, delay: number): string {
  const [debouncedValue, setDebouncedValue] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value)
    }, delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debouncedValue
}

interface GlobalSearchProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function GlobalSearch({ open, onOpenChange }: GlobalSearchProps): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const { apiClient } = useApi()
  const { config } = useConfig()
  const { recentItems } = useAppContext()
  const [query, setQuery] = useState('')

  const collections = useMemo(() => getCollectionNames(config), [config])
  const debouncedQuery = useDebounced(query, 300)
  const hasQuery = debouncedQuery.trim().length >= 2

  // Search across all collections in parallel
  const { data: searchResults = [], isLoading } = useQuery({
    queryKey: ['global-search', debouncedQuery, collections.map((c) => c.name).join(',')],
    queryFn: async () => {
      if (!hasQuery || collections.length === 0) return []

      const results: SearchResult[] = []
      const searchPromises = collections.map(async (col) => {
        try {
          const response = await apiClient.get(`/api/${col.name}`, {
            params: {
              'page[size]': '5',
              'filter[_search]': debouncedQuery,
            },
          })
          const unwrapped = unwrapCollection(response)
          if (unwrapped?.data) {
            for (const record of unwrapped.data) {
              const displayValue = extractDisplayValue(record as Record<string, unknown>)
              results.push({
                id: String(record.id),
                collectionName: col.name,
                collectionLabel: col.label,
                displayValue,
              })
            }
          }
        } catch {
          // Skip collections that fail to search
        }
      })

      await Promise.all(searchPromises)
      return results
    },
    enabled: open && hasQuery,
    staleTime: 10 * 1000,
  })

  // Group search results by collection
  const groupedResults = useMemo(() => {
    const groups = new Map<string, SearchResult[]>()
    for (const result of searchResults) {
      const existing = groups.get(result.collectionName) || []
      existing.push(result)
      groups.set(result.collectionName, existing)
    }
    return groups
  }, [searchResults])

  const handleSelect = useCallback(
    (path: string) => {
      onOpenChange(false)
      setQuery('')
      navigate(path)
    },
    [navigate, onOpenChange]
  )

  const handleOpenChange = useCallback(
    (newOpen: boolean) => {
      if (!newOpen) {
        setQuery('')
      }
      onOpenChange(newOpen)
    },
    [onOpenChange]
  )

  const basePath = `/${tenantSlug}/app`

  return (
    <CommandDialog open={open} onOpenChange={handleOpenChange}>
      <CommandInput
        placeholder="Search records, collections, pages..."
        value={query}
        onValueChange={setQuery}
      />
      <CommandList>
        <CommandEmpty>
          <div className="flex flex-col items-center gap-2 py-6 text-center">
            <Search className="h-8 w-8 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">
              {hasQuery && !isLoading
                ? 'No results found. Try a different query.'
                : 'Type at least 2 characters to search.'}
            </p>
          </div>
        </CommandEmpty>

        {/* Search results grouped by collection */}
        {hasQuery &&
          Array.from(groupedResults.entries()).map(([collectionName, results]) => (
            <CommandGroup
              key={collectionName}
              heading={results[0]?.collectionLabel || collectionName}
            >
              {results.map((result) => (
                <CommandItem
                  key={`${result.collectionName}-${result.id}`}
                  onSelect={() =>
                    handleSelect(`${basePath}/o/${result.collectionName}/${result.id}`)
                  }
                >
                  <FileText className="mr-2 h-4 w-4 text-muted-foreground" />
                  <span className="flex-1 truncate">{result.displayValue}</span>
                  <Badge variant="secondary" className="ml-2 text-[10px]">
                    {result.collectionLabel}
                  </Badge>
                </CommandItem>
              ))}
            </CommandGroup>
          ))}

        {/* Full search page link */}
        {hasQuery && searchResults.length > 0 && (
          <>
            <CommandSeparator />
            <CommandGroup>
              <CommandItem
                onSelect={() =>
                  handleSelect(`${basePath}/search?q=${encodeURIComponent(debouncedQuery)}`)
                }
              >
                <Search className="mr-2 h-4 w-4" />
                <span>View all results for &ldquo;{debouncedQuery}&rdquo;</span>
                <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground" />
              </CommandItem>
            </CommandGroup>
          </>
        )}

        {/* Recent items (when no query) */}
        {!hasQuery && recentItems.length > 0 && (
          <CommandGroup heading="Recent">
            {recentItems.slice(0, 5).map((item) => (
              <CommandItem
                key={`recent-${item.collectionName}-${item.id}`}
                onSelect={() => handleSelect(`${basePath}/o/${item.collectionName}/${item.id}`)}
              >
                <Clock className="mr-2 h-4 w-4 text-muted-foreground" />
                <span className="flex-1 truncate">{item.label}</span>
                <span className="text-xs text-muted-foreground">
                  {formatRelativeTime(item.timestamp)}
                </span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}

        {/* Collections (when no query) */}
        {!hasQuery && collections.length > 0 && (
          <>
            <CommandSeparator />
            <CommandGroup heading="Collections">
              {collections.map((col) => (
                <CommandItem
                  key={`col-${col.name}`}
                  onSelect={() => handleSelect(`${basePath}/o/${col.name}`)}
                >
                  <Database className="mr-2 h-4 w-4 text-muted-foreground" />
                  <span>{col.label}</span>
                  <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground" />
                </CommandItem>
              ))}
            </CommandGroup>
          </>
        )}

        {/* Quick navigation */}
        {!hasQuery && (
          <>
            <CommandSeparator />
            <CommandGroup heading="Navigation">
              <CommandItem onSelect={() => handleSelect(`${basePath}/home`)}>
                <Home className="mr-2 h-4 w-4" />
                <span>Go to Home</span>
                <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground" />
              </CommandItem>
              <CommandItem onSelect={() => handleSelect(`/${tenantSlug}/setup`)}>
                <Settings className="mr-2 h-4 w-4" />
                <span>Switch to Setup</span>
                <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground" />
              </CommandItem>
            </CommandGroup>
          </>
        )}
      </CommandList>
    </CommandDialog>
  )
}
