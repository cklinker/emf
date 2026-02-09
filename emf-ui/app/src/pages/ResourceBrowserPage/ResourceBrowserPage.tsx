/**
 * ResourceBrowserPage Component
 *
 * Displays a list of available collections for browsing data.
 * Users can select a collection to navigate to its data view.
 *
 * Requirements:
 * - 11.1: Resource browser allows selecting a collection
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import styles from './ResourceBrowserPage.module.css'

/**
 * Collection summary interface for the resource browser
 */
export interface CollectionSummary {
  id: string
  name: string
  displayName: string
  description?: string
  active: boolean
  fieldCount: number
}

/**
 * Props for ResourceBrowserPage component
 */
export interface ResourceBrowserPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * ResourceBrowserPage Component
 *
 * Main page for browsing resources in collections.
 * Displays a list of available collections that users can select
 * to view and manage data records.
 */
export function ResourceBrowserPage({
  testId = 'resource-browser-page',
}: ResourceBrowserPageProps): React.ReactElement {
  const navigate = useNavigate()
  const { t } = useI18n()

  // Search filter state
  const [searchQuery, setSearchQuery] = useState('')

  const { apiClient } = useApi()

  // Fetch collections query
  const {
    data: collections = [],
    isLoading,
    error,
    refetch,
  } = useQuery<CollectionSummary[]>({
    queryKey: ['collections-for-browser'],
    queryFn: async () => {
      const response = await apiClient.get<{ content: CollectionSummary[] }>('/control/collections')
      console.log('[ResourceBrowserPage] API response:', response)
      // Extract content array from paginated response
      return Array.isArray(response?.content) ? response.content : []
    },
  })

  // Filter to only show active collections
  const activeCollections = useMemo(() => {
    if (!Array.isArray(collections)) {
      console.log('[ResourceBrowserPage] Collections is not an array:', collections)
      return []
    }
    console.log('[ResourceBrowserPage] Total collections:', collections.length)
    const filtered = collections.filter((collection) => collection?.active)
    console.log('[ResourceBrowserPage] Active collections:', filtered.length, filtered)
    return filtered
  }, [collections])

  // Filter collections by search query
  const filteredCollections = useMemo(() => {
    if (!searchQuery.trim()) {
      return activeCollections
    }
    const query = searchQuery.toLowerCase()
    return activeCollections.filter(
      (collection) =>
        collection?.name?.toLowerCase().includes(query) ||
        collection?.displayName?.toLowerCase().includes(query) ||
        (collection?.description && collection.description.toLowerCase().includes(query))
    )
  }, [activeCollections, searchQuery])

  // Handle search input change
  const handleSearchChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(event.target.value)
  }, [])

  // Handle collection selection - navigate to collection data view
  const handleCollectionSelect = useCallback(
    (collection: CollectionSummary) => {
      navigate(`/resources/${collection.name}`)
    },
    [navigate]
  )

  // Handle keyboard navigation on collection cards
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent, collection: CollectionSummary) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        handleCollectionSelect(collection)
      }
    },
    [handleCollectionSelect]
  )

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <h1 className={styles.title}>{t('resources.title')}</h1>
        <p className={styles.subtitle}>{t('resources.selectCollection')}</p>
      </header>

      {/* Search Filter */}
      <div className={styles.searchContainer} role="search" aria-label={t('common.search')}>
        <input
          type="text"
          className={styles.searchInput}
          placeholder={t('common.search')}
          value={searchQuery}
          onChange={handleSearchChange}
          aria-label={t('common.search')}
          data-testid="collection-search"
        />
        {searchQuery && (
          <button
            type="button"
            className={styles.clearButton}
            onClick={() => setSearchQuery('')}
            aria-label={t('common.clear')}
            data-testid="clear-search"
          >
            ×
          </button>
        )}
      </div>

      {/* Collections Grid */}
      {filteredCollections.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          {activeCollections.length === 0 ? (
            <p>{t('common.noData')}</p>
          ) : (
            <p>{t('common.noResults')}</p>
          )}
        </div>
      ) : (
        <div
          className={styles.collectionsGrid}
          role="list"
          aria-label={t('resources.title')}
          data-testid="collections-grid"
        >
          {/* eslint-disable jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/no-noninteractive-tabindex */}
          {filteredCollections.map((collection, index) => (
            <div
              key={collection.id}
              className={styles.collectionCard}
              role="listitem"
              tabIndex={0}
              onClick={() => handleCollectionSelect(collection)}
              onKeyDown={(e) => handleKeyDown(e, collection)}
              aria-label={`${collection.displayName}: ${collection.description || t('common.noData')}`}
              data-testid={`collection-card-${index}`}
            >
              <div className={styles.cardHeader}>
                <h2 className={styles.collectionName}>{collection.displayName}</h2>
                <span className={styles.collectionSlug}>{collection.name}</span>
              </div>
              {collection.description && (
                <p className={styles.collectionDescription}>{collection.description}</p>
              )}
              <div className={styles.cardFooter}>
                <span className={styles.fieldCount}>
                  {collection.fieldCount} {collection.fieldCount === 1 ? 'field' : 'fields'}
                </span>
              </div>
            </div>
          ))}
          {/* eslint-enable jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/no-noninteractive-tabindex */}
        </div>
      )}

      {/* Results count */}
      {filteredCollections.length > 0 && (
        <div className={styles.resultsCount} aria-live="polite" data-testid="results-count">
          {filteredCollections.length}{' '}
          {filteredCollections.length === 1 ? 'collection' : 'collections'}
          {searchQuery && ` matching "${searchQuery}"`}
        </div>
      )}
    </div>
  )
}

export default ResourceBrowserPage
