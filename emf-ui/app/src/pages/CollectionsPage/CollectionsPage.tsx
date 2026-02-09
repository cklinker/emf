/**
 * CollectionsPage Component
 *
 * Displays a paginated list of collections with filtering, sorting, and CRUD actions.
 * Uses DataTable from @emf/components for the list display.
 *
 * Requirements:
 * - 3.1: Display a paginated list of all collections
 * - 3.2: Support filtering collections by name and status
 * - 3.3: Support sorting collections by name, creation date, and modification date
 * - 3.4: Create collection action
 * - 3.10: Display confirmation dialog before deletion
 * - 3.11: Soft-delete collection and remove from list
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './CollectionsPage.module.css'

/**
 * Collection interface matching the API response
 */
export interface Collection {
  id: string
  name: string
  displayName: string
  description?: string
  storageMode: 'PHYSICAL_TABLE' | 'JSONB'
  active: boolean
  currentVersion: number
  createdAt: string
  updatedAt: string
}

/**
 * Filter state for collections
 */
interface CollectionFilters {
  name: string
  status: 'all' | 'active' | 'inactive'
}

/**
 * Sort state for collections
 */
interface CollectionSort {
  field: 'name' | 'createdAt' | 'updatedAt'
  direction: 'asc' | 'desc'
}

/**
 * Props for CollectionsPage component
 */
export interface CollectionsPageProps {
  /** Optional test ID for testing */
  testId?: string
}

// API response type for paginated collections
interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

/**
 * CollectionsPage Component
 *
 * Main page for managing collections in the EMF Admin UI.
 * Provides listing, filtering, sorting, and CRUD operations.
 */
export function CollectionsPage({
  testId = 'collections-page',
}: CollectionsPageProps): React.ReactElement {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Filter state
  const [filters, setFilters] = useState<CollectionFilters>({
    name: '',
    status: 'all',
  })

  // Sort state
  const [sort, setSort] = useState<CollectionSort>({
    field: 'name',
    direction: 'asc',
  })

  // Pagination state
  const [page, setPage] = useState(1)
  const pageSize = 10

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [collectionToDelete, setCollectionToDelete] = useState<Collection | null>(null)

  // Fetch collections query
  const {
    data: collectionsPage,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['collections'],
    queryFn: () => apiClient.get<PageResponse<Collection>>('/control/collections?size=1000'),
  })

  // Extract collections from paginated response
  const collections = useMemo(() => {
    return collectionsPage?.content || []
  }, [collectionsPage])

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/collections/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collections'] })
      showToast(t('success.deleted', { item: t('collections.title') }), 'success')
      setDeleteDialogOpen(false)
      setCollectionToDelete(null)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Filter collections
  const filteredCollections = useMemo(() => {
    return collections.filter((collection) => {
      // Filter by name
      if (
        filters.name &&
        !collection.name.toLowerCase().includes(filters.name.toLowerCase()) &&
        !collection.displayName.toLowerCase().includes(filters.name.toLowerCase())
      ) {
        return false
      }

      // Filter by status
      if (filters.status === 'active' && !collection.active) {
        return false
      }
      if (filters.status === 'inactive' && collection.active) {
        return false
      }

      return true
    })
  }, [collections, filters])

  // Sort collections
  const sortedCollections = useMemo(() => {
    return [...filteredCollections].sort((a, b) => {
      let comparison = 0

      switch (sort.field) {
        case 'name':
          comparison = a.name.localeCompare(b.name)
          break
        case 'createdAt':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          break
        case 'updatedAt':
          comparison = new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime()
          break
      }

      return sort.direction === 'asc' ? comparison : -comparison
    })
  }, [filteredCollections, sort])

  // Paginate collections
  const paginatedCollections = useMemo(() => {
    const startIndex = (page - 1) * pageSize
    return sortedCollections.slice(startIndex, startIndex + pageSize)
  }, [sortedCollections, page, pageSize])

  // Calculate total pages
  const totalPages = Math.ceil(sortedCollections.length / pageSize)

  // Handle filter change
  const handleNameFilterChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    setFilters((prev) => ({ ...prev, name: event.target.value }))
    setPage(1) // Reset to first page when filtering
  }, [])

  const handleStatusFilterChange = useCallback((event: React.ChangeEvent<HTMLSelectElement>) => {
    setFilters((prev) => ({ ...prev, status: event.target.value as CollectionFilters['status'] }))
    setPage(1) // Reset to first page when filtering
  }, [])

  // Handle sort change
  const handleSortChange = useCallback((field: CollectionSort['field']) => {
    setSort((prev) => ({
      field,
      direction: prev.field === field && prev.direction === 'asc' ? 'desc' : 'asc',
    }))
  }, [])

  // Handle create action
  const handleCreate = useCallback(() => {
    navigate('/collections/new')
  }, [navigate])

  // Handle edit action
  const handleEdit = useCallback(
    (collection: Collection) => {
      navigate(`/collections/${collection.id}/edit`)
    },
    [navigate]
  )

  // Handle view/detail action
  const handleView = useCallback(
    (collection: Collection) => {
      navigate(`/collections/${collection.id}`)
    },
    [navigate]
  )

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback((collection: Collection) => {
    setCollectionToDelete(collection)
    setDeleteDialogOpen(true)
  }, [])

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (collectionToDelete) {
      deleteMutation.mutate(collectionToDelete.id)
    }
  }, [collectionToDelete, deleteMutation])

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setCollectionToDelete(null)
  }, [])

  // Handle page change
  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage)
  }, [])

  // Get sort indicator
  const getSortIndicator = useCallback(
    (field: CollectionSort['field']) => {
      if (sort.field !== field) return null
      return sort.direction === 'asc' ? ' ↑' : ' ↓'
    },
    [sort]
  )

  // Get aria-sort value
  const getAriaSort = useCallback(
    (field: CollectionSort['field']): 'ascending' | 'descending' | 'none' => {
      if (sort.field !== field) return 'none'
      return sort.direction === 'asc' ? 'ascending' : 'descending'
    },
    [sort]
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
        <h1 className={styles.title}>{t('collections.title')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label={t('collections.createCollection')}
          data-testid="create-collection-button"
        >
          {t('collections.createCollection')}
        </button>
      </header>

      {/* Filters */}
      <div className={styles.filters} role="search" aria-label={t('common.filter')}>
        <div className={styles.filterGroup}>
          <label htmlFor="name-filter" className={styles.filterLabel}>
            {t('collections.collectionName')}
          </label>
          <input
            id="name-filter"
            type="text"
            className={styles.filterInput}
            placeholder={t('common.search')}
            value={filters.name}
            onChange={handleNameFilterChange}
            aria-label={t('collections.collectionName')}
            data-testid="name-filter"
          />
        </div>
        <div className={styles.filterGroup}>
          <label htmlFor="status-filter" className={styles.filterLabel}>
            {t('collections.status')}
          </label>
          <select
            id="status-filter"
            className={styles.filterSelect}
            value={filters.status}
            onChange={handleStatusFilterChange}
            aria-label={t('collections.status')}
            data-testid="status-filter"
          >
            <option value="all">{t('common.selectAll')}</option>
            <option value="active">{t('collections.active')}</option>
            <option value="inactive">{t('collections.inactive')}</option>
          </select>
        </div>
      </div>

      {/* Collections Table */}
      {paginatedCollections.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <>
          <div className={styles.tableContainer}>
            <table
              className={styles.table}
              role="grid"
              aria-label={t('collections.title')}
              data-testid="collections-table"
            >
              <thead>
                <tr role="row">
                  <th
                    role="columnheader"
                    scope="col"
                    className={styles.sortableHeader}
                    onClick={() => handleSortChange('name')}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        handleSortChange('name')
                      }
                    }}
                    tabIndex={0}
                    aria-sort={getAriaSort('name')}
                    data-testid="header-name"
                  >
                    {t('collections.collectionName')}
                    <span className={styles.sortIndicator} aria-hidden="true">
                      {getSortIndicator('name')}
                    </span>
                  </th>
                  <th role="columnheader" scope="col">
                    {t('collections.displayName')}
                  </th>
                  <th role="columnheader" scope="col">
                    {t('collections.status')}
                  </th>
                  <th
                    role="columnheader"
                    scope="col"
                    className={styles.sortableHeader}
                    onClick={() => handleSortChange('createdAt')}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        handleSortChange('createdAt')
                      }
                    }}
                    tabIndex={0}
                    aria-sort={getAriaSort('createdAt')}
                    data-testid="header-created"
                  >
                    {t('common.create')}d
                    <span className={styles.sortIndicator} aria-hidden="true">
                      {getSortIndicator('createdAt')}
                    </span>
                  </th>
                  <th
                    role="columnheader"
                    scope="col"
                    className={styles.sortableHeader}
                    onClick={() => handleSortChange('updatedAt')}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        handleSortChange('updatedAt')
                      }
                    }}
                    tabIndex={0}
                    aria-sort={getAriaSort('updatedAt')}
                    data-testid="header-updated"
                  >
                    {t('common.edit')}ed
                    <span className={styles.sortIndicator} aria-hidden="true">
                      {getSortIndicator('updatedAt')}
                    </span>
                  </th>
                  <th role="columnheader" scope="col">
                    {t('common.actions')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {paginatedCollections.map((collection, index) => (
                  <tr
                    key={collection.id}
                    role="row"
                    className={styles.tableRow}
                    onClick={() => handleView(collection)}
                    data-testid={`collection-row-${index}`}
                  >
                    <td role="gridcell" className={styles.nameCell}>
                      {collection.name}
                    </td>
                    <td role="gridcell">{collection.displayName}</td>
                    <td role="gridcell">
                      <span
                        className={`${styles.statusBadge} ${
                          collection.active ? styles.statusActive : styles.statusInactive
                        }`}
                        data-testid={`status-badge-${index}`}
                      >
                        {collection.active ? t('collections.active') : t('collections.inactive')}
                      </span>
                    </td>
                    <td role="gridcell">
                      {formatDate(new Date(collection.createdAt), {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </td>
                    <td role="gridcell">
                      {formatDate(new Date(collection.updatedAt), {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </td>
                    <td role="gridcell" className={styles.actionsCell}>
                      <div
                        className={styles.actions}
                        onClick={(e) => e.stopPropagation()}
                        onKeyDown={(e) => e.stopPropagation()}
                        role="toolbar"
                      >
                        <button
                          type="button"
                          className={styles.actionButton}
                          onClick={() => handleEdit(collection)}
                          aria-label={`${t('common.edit')} ${collection.name}`}
                          data-testid={`edit-button-${index}`}
                        >
                          {t('common.edit')}
                        </button>
                        <button
                          type="button"
                          className={`${styles.actionButton} ${styles.deleteButton}`}
                          onClick={() => handleDeleteClick(collection)}
                          aria-label={`${t('common.delete')} ${collection.name}`}
                          data-testid={`delete-button-${index}`}
                        >
                          {t('common.delete')}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <nav
              className={styles.pagination}
              role="navigation"
              aria-label="Table pagination"
              data-testid="pagination"
            >
              <button
                type="button"
                className={styles.paginationButton}
                disabled={page <= 1}
                onClick={() => handlePageChange(page - 1)}
                aria-label={t('common.previous')}
              >
                {t('common.previous')}
              </button>
              <span className={styles.paginationInfo} aria-live="polite">
                Page {page} of {totalPages}
                <span className={styles.paginationTotal}> ({sortedCollections.length} total)</span>
              </span>
              <button
                type="button"
                className={styles.paginationButton}
                disabled={page >= totalPages}
                onClick={() => handlePageChange(page + 1)}
                aria-label={t('common.next')}
              >
                {t('common.next')}
              </button>
            </nav>
          )}
        </>
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('collections.deleteCollection')}
        message={t('collections.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default CollectionsPage
