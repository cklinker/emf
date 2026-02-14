/**
 * RelatedRecordsSection Component
 *
 * Displays records from other collections that reference the current record.
 * Discovers relationships by inspecting collection schemas for reference,
 * lookup, and master_detail fields pointing to the current collection.
 *
 * Features:
 * - Automatic relationship discovery from collection schemas
 * - Tabbed interface with one tab per related collection
 * - Compact data table per tab showing first 5 records
 * - Clickable rows to navigate to related records
 * - "View All" link per tab
 * - Loading and empty states
 */

import React, { useState, useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'
import { LoadingSpinner } from '../LoadingSpinner'
import type { ApiClient } from '../../services/apiClient'
import styles from './RelatedRecordsSection.module.css'

/**
 * Field definition from a collection schema
 */
interface CollectionField {
  id: string
  name: string
  displayName?: string
  type: string
  required: boolean
  referenceTarget?: string
}

/**
 * Collection schema from the control API
 */
interface CollectionSchema {
  id: string
  name: string
  displayName: string
  fields: CollectionField[]
}

/**
 * A discovered relationship: a collection + the field that references the current collection
 */
interface RelatedCollection {
  collection: CollectionSchema
  referenceField: CollectionField
}

/**
 * Resource record
 */
interface Resource {
  id: string
  [key: string]: unknown
}

/**
 * Paginated response from the data API
 */
interface PaginatedResponse {
  data: Resource[]
  total: number
}

/**
 * Props for RelatedRecordsSection
 */
export interface RelatedRecordsSectionProps {
  /** The current collection name (the collection being viewed) */
  collectionName: string
  /** The current record ID */
  recordId: string
  /** API client instance for making authenticated requests */
  apiClient: ApiClient
}

/** Reference field types that indicate a relationship */
const REFERENCE_FIELD_TYPES = new Set(['reference', 'lookup', 'master_detail'])

/** Maximum number of records to display per tab */
const MAX_DISPLAY_RECORDS = 5

/** Maximum number of field columns to show in the compact table */
const MAX_DISPLAY_COLUMNS = 4

/**
 * RelatedRecordsSection Component
 *
 * Discovers and displays related records from other collections
 * that reference the current record.
 *
 * @example
 * ```tsx
 * <RelatedRecordsSection
 *   collectionName="accounts"
 *   recordId="abc-123"
 *   apiClient={apiClient}
 * />
 * ```
 */
export function RelatedRecordsSection({
  collectionName,
  recordId,
  apiClient,
}: RelatedRecordsSectionProps): React.ReactElement {
  const { t, formatDate } = useI18n()
  const navigate = useNavigate()

  // Active tab index
  const [activeTab, setActiveTab] = useState(0)

  // Fetch all collection schemas to discover relationships
  const {
    data: collectionsResponse,
    isLoading: collectionsLoading,
    error: collectionsError,
  } = useQuery({
    queryKey: ['related-records-collections', collectionName],
    queryFn: () => apiClient.get<{ content: CollectionSchema[] }>('/control/collections'),
    enabled: !!collectionName && !!recordId,
  })

  // Discover related collections: collections that have fields referencing the current collection
  const relatedCollections = useMemo<RelatedCollection[]>(() => {
    if (!collectionsResponse?.content) return []

    const related: RelatedCollection[] = []

    for (const collection of collectionsResponse.content) {
      // Skip the current collection itself
      if (collection.name === collectionName) continue

      const collectionFields = Array.isArray(collection.fields) ? collection.fields : []
      for (const field of collectionFields) {
        if (REFERENCE_FIELD_TYPES.has(field.type) && field.referenceTarget === collectionName) {
          related.push({ collection, referenceField: field })
          // Only add the collection once per reference field
          // (a collection could have multiple reference fields to the same target)
        }
      }
    }

    return related
  }, [collectionsResponse, collectionName])

  // Clamp active tab index if it goes out of bounds
  const safeActiveTab = Math.min(activeTab, Math.max(relatedCollections.length - 1, 0))

  // Get the currently selected related collection
  const activeRelated = relatedCollections[safeActiveTab]

  // Fetch related records for the active tab
  const { data: relatedRecordsResponse, isLoading: recordsLoading } = useQuery({
    queryKey: [
      'related-records',
      activeRelated?.collection.name,
      activeRelated?.referenceField.name,
      recordId,
    ],
    queryFn: () => {
      const params = new URLSearchParams()
      params.set(`filter[${activeRelated!.referenceField.name}][eq]`, recordId)
      params.set('page[size]', String(MAX_DISPLAY_RECORDS))
      return apiClient.get<PaginatedResponse>(
        `/api/${activeRelated!.collection.name}?${params.toString()}`
      )
    },
    enabled: !!activeRelated && !!recordId,
  })

  /**
   * Get the display columns for a collection schema.
   * Returns the first few fields plus a created date column indicator.
   */
  const displayFields = useMemo(() => {
    if (!activeRelated) return []
    // Exclude the reference field that points back to the current record
    const collectionFields = Array.isArray(activeRelated.collection.fields)
      ? activeRelated.collection.fields
      : []
    const fields = collectionFields.filter((f) => f.name !== activeRelated.referenceField.name)
    return fields.slice(0, MAX_DISPLAY_COLUMNS)
  }, [activeRelated])

  /**
   * Format a cell value for compact display
   */
  const formatCellValue = (value: unknown): string => {
    if (value === null || value === undefined) return '-'
    if (typeof value === 'boolean') return value ? t('common.yes') : t('common.no')
    if (typeof value === 'object') return JSON.stringify(value)
    const str = String(value)
    return str.length > 50 ? str.slice(0, 50) + '...' : str
  }

  /**
   * Format a date string for compact display
   */
  const formatCreatedDate = (dateStr: unknown): string => {
    if (!dateStr || typeof dateStr !== 'string') return '-'
    try {
      return formatDate(new Date(dateStr), {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    } catch {
      return String(dateStr)
    }
  }

  // Loading state while discovering relationships
  if (collectionsLoading) {
    return (
      <section
        className={styles.section}
        aria-labelledby="related-records-heading"
        data-testid="related-records-section"
      >
        <div className={styles.sectionHeader}>
          <h2 id="related-records-heading" className={styles.sectionTitle}>
            {t('relatedRecords.title')}
          </h2>
        </div>
        <div className={styles.loadingState} data-testid="related-records-loading">
          <LoadingSpinner size="medium" label={t('common.loading')} />
        </div>
      </section>
    )
  }

  // Error state
  if (collectionsError) {
    return (
      <section
        className={styles.section}
        aria-labelledby="related-records-heading"
        data-testid="related-records-section"
      >
        <div className={styles.sectionHeader}>
          <h2 id="related-records-heading" className={styles.sectionTitle}>
            {t('relatedRecords.title')}
          </h2>
        </div>
        <div className={styles.emptyState} data-testid="related-records-error">
          <p>{t('relatedRecords.errorLoading')}</p>
        </div>
      </section>
    )
  }

  // No relationships found
  if (relatedCollections.length === 0) {
    return (
      <section
        className={styles.section}
        aria-labelledby="related-records-heading"
        data-testid="related-records-section"
      >
        <div className={styles.sectionHeader}>
          <h2 id="related-records-heading" className={styles.sectionTitle}>
            {t('relatedRecords.title')}
          </h2>
        </div>
        <div className={styles.emptyState} data-testid="related-records-empty">
          <p>{t('relatedRecords.noRelatedRecords')}</p>
        </div>
      </section>
    )
  }

  const records = relatedRecordsResponse?.data ?? []
  const totalCount = relatedRecordsResponse?.total ?? 0

  return (
    <section
      className={styles.section}
      aria-labelledby="related-records-heading"
      data-testid="related-records-section"
    >
      {/* Section Header */}
      <div className={styles.sectionHeader}>
        <h2 id="related-records-heading" className={styles.sectionTitle}>
          {t('relatedRecords.title')}
        </h2>
      </div>

      {/* Tab Bar */}
      <div className={styles.tabs} role="tablist" aria-label={t('relatedRecords.tabsLabel')}>
        {relatedCollections.map((related, index) => (
          <button
            key={`${related.collection.name}-${related.referenceField.name}`}
            type="button"
            role="tab"
            id={`related-tab-${index}`}
            aria-selected={index === safeActiveTab}
            aria-controls={`related-tabpanel-${index}`}
            className={`${styles.tab} ${index === safeActiveTab ? styles.tabActive : ''}`}
            onClick={() => setActiveTab(index)}
            data-testid={`related-tab-${related.collection.name}`}
          >
            {related.collection.displayName || related.collection.name}
            {index === safeActiveTab && !recordsLoading && (
              <span
                className={styles.tabBadge}
                data-testid={`related-tab-badge-${related.collection.name}`}
              >
                {totalCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Tab Panel */}
      <div
        role="tabpanel"
        id={`related-tabpanel-${safeActiveTab}`}
        aria-labelledby={`related-tab-${safeActiveTab}`}
        data-testid="related-records-tabpanel"
      >
        {recordsLoading ? (
          <div className={styles.tabLoadingState} data-testid="related-records-tab-loading">
            <LoadingSpinner size="small" label={t('common.loading')} />
          </div>
        ) : records.length === 0 ? (
          <div className={styles.tabEmptyState} data-testid="related-records-tab-empty">
            {t('relatedRecords.noRecordsInCollection', {
              collection: activeRelated?.collection.displayName || '',
            })}
          </div>
        ) : (
          <>
            <div className={styles.tableContainer}>
              <table
                className={styles.compactTable}
                role="grid"
                aria-label={t('relatedRecords.tableLabel', {
                  collection: activeRelated?.collection.displayName || '',
                })}
                data-testid="related-records-table"
              >
                <thead>
                  <tr>
                    {displayFields.map((field) => (
                      <th key={field.name} scope="col">
                        {field.displayName || field.name}
                      </th>
                    ))}
                    <th scope="col">{t('relatedRecords.created')}</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((record) => (
                    <tr
                      key={record.id}
                      onClick={() =>
                        navigate(
                          `/${getTenantSlug()}/resources/${activeRelated!.collection.name}/${record.id}`
                        )
                      }
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault()
                          navigate(
                            `/${getTenantSlug()}/resources/${activeRelated!.collection.name}/${record.id}`
                          )
                        }
                      }}
                      data-testid={`related-record-row-${record.id}`}
                    >
                      {displayFields.map((field) => (
                        <td key={field.name}>{formatCellValue(record[field.name])}</td>
                      ))}
                      <td>{formatCreatedDate(record.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* View All Link */}
            <Link
              to={`/${getTenantSlug()}/resources/${activeRelated!.collection.name}`}
              className={styles.viewAllLink}
              data-testid="related-records-view-all"
            >
              {t('relatedRecords.viewAll', {
                count: totalCount,
                collection: activeRelated?.collection.displayName || '',
              })}
              &rarr;
            </Link>
          </>
        )}
      </div>
    </section>
  )
}

export default RelatedRecordsSection
