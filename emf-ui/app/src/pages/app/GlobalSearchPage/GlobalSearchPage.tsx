/**
 * GlobalSearchPage
 *
 * Full-page search results view. Searches across all configured collections
 * and displays results grouped by collection type.
 *
 * Features:
 * - Multi-collection parallel search
 * - Results grouped by collection with record counts
 * - Clickable results navigate to record detail
 * - Search input with debounced query
 * - Empty states and loading indicators
 * - Breadcrumb navigation
 */

import React, { useState, useMemo, useCallback, useEffect } from 'react'
import { useNavigate, useParams, useSearchParams, Link } from 'react-router-dom'
import { Search, Loader2, FileText, ArrowRight, Database } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { useApi } from '@/context/ApiContext'
import { useConfig } from '@/context/ConfigContext'
import { unwrapCollection } from '@/utils/jsonapi'

interface SearchResult {
  id: string
  collectionName: string
  collectionLabel: string
  displayValue: string
  subtitle?: string
}

/**
 * Extract collection names from menu config.
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
 * Extract a display value from a flat record object.
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
 * Extract a subtitle from a record (secondary fields like email, description).
 */
function extractSubtitle(record: Record<string, unknown>): string | undefined {
  const subtitleFields = ['email', 'description', 'status', 'type', 'category']
  for (const field of subtitleFields) {
    if (record[field] && typeof record[field] === 'string') {
      return record[field] as string
    }
  }
  return undefined
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

export function GlobalSearchPage(): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { apiClient } = useApi()
  const { config } = useConfig()

  const initialQuery = searchParams.get('q') || ''
  const [query, setQuery] = useState(initialQuery)
  const debouncedQuery = useDebounced(query, 300)
  const hasQuery = debouncedQuery.trim().length >= 2
  const basePath = `/${tenantSlug}/app`

  const collections = useMemo(() => getCollectionNames(config), [config])

  // Update URL params when debounced query changes
  const updateSearchParams = useCallback(
    (q: string) => {
      if (q.trim()) {
        setSearchParams({ q: q.trim() }, { replace: true })
      } else {
        setSearchParams({}, { replace: true })
      }
    },
    [setSearchParams]
  )

  // Sync URL on debounced query change
  useEffect(() => {
    updateSearchParams(debouncedQuery)
  }, [debouncedQuery, updateSearchParams])

  // Search across all collections in parallel
  const {
    data: searchResults = [],
    isLoading,
    isFetching,
  } = useQuery({
    queryKey: ['page-search', debouncedQuery, collections.map((c) => c.name).join(',')],
    queryFn: async () => {
      if (!hasQuery || collections.length === 0) return []

      const results: SearchResult[] = []
      const searchPromises = collections.map(async (col) => {
        try {
          const response = await apiClient.get(`/api/${col.name}`, {
            params: {
              'page[size]': '10',
              'filter[_search]': debouncedQuery,
            },
          })
          const unwrapped = unwrapCollection(response)
          if (unwrapped?.data) {
            for (const record of unwrapped.data) {
              const typedRecord = record as Record<string, unknown>
              results.push({
                id: String(typedRecord.id),
                collectionName: col.name,
                collectionLabel: col.label,
                displayValue: extractDisplayValue(typedRecord),
                subtitle: extractSubtitle(typedRecord),
              })
            }
          }
        } catch {
          // Skip collections that fail
        }
      })

      await Promise.all(searchPromises)
      return results
    },
    enabled: hasQuery,
    staleTime: 10 * 1000,
  })

  // Group results by collection
  const groupedResults = useMemo(() => {
    const groups = new Map<string, SearchResult[]>()
    for (const result of searchResults) {
      const existing = groups.get(result.collectionName) || []
      existing.push(result)
      groups.set(result.collectionName, existing)
    }
    return groups
  }, [searchResults])

  const totalResults = searchResults.length

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      {/* Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/home`}>Home</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Search</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Search input */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search across all collections..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="pl-10 text-base"
          autoFocus
        />
        {isFetching && (
          <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-muted-foreground" />
        )}
      </div>

      {/* Results header */}
      {hasQuery && !isLoading && (
        <p className="text-sm text-muted-foreground">
          {totalResults === 0
            ? `No results for "${debouncedQuery}"`
            : `${totalResults} result${totalResults !== 1 ? 's' : ''} for "${debouncedQuery}"`}
          {groupedResults.size > 0 &&
            ` across ${groupedResults.size} collection${groupedResults.size !== 1 ? 's' : ''}`}
        </p>
      )}

      {/* Loading state */}
      {isLoading && hasQuery && (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      )}

      {/* No query state */}
      {!hasQuery && (
        <Card>
          <CardContent className="py-12">
            <div className="flex flex-col items-center justify-center text-center">
              <Search className="mb-4 h-12 w-12 text-muted-foreground/30" />
              <p className="text-sm text-muted-foreground">
                Enter at least 2 characters to search across all collections.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* No results state */}
      {hasQuery && !isLoading && totalResults === 0 && (
        <Card>
          <CardContent className="py-12">
            <div className="flex flex-col items-center justify-center text-center">
              <Search className="mb-4 h-12 w-12 text-muted-foreground/30" />
              <p className="text-sm font-medium text-foreground">No results found</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Try adjusting your search terms or check for typos.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Results grouped by collection */}
      {Array.from(groupedResults.entries()).map(([collectionName, results]) => (
        <Card key={collectionName}>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Database className="h-4 w-4 text-muted-foreground" />
              {results[0]?.collectionLabel || collectionName}
              <Badge variant="secondary" className="text-xs">
                {results.length}
              </Badge>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-1">
              {results.map((result) => (
                <button
                  key={`${result.collectionName}-${result.id}`}
                  onClick={() => navigate(`${basePath}/o/${result.collectionName}/${result.id}`)}
                  className="flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-sm transition-colors hover:bg-accent"
                >
                  <FileText className="h-4 w-4 flex-shrink-0 text-muted-foreground" />
                  <div className="flex-1 overflow-hidden">
                    <span className="block truncate font-medium text-foreground">
                      {result.displayValue}
                    </span>
                    {result.subtitle && (
                      <span className="block truncate text-xs text-muted-foreground">
                        {result.subtitle}
                      </span>
                    )}
                  </div>
                  <ArrowRight className="h-3.5 w-3.5 flex-shrink-0 text-muted-foreground" />
                </button>
              ))}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
