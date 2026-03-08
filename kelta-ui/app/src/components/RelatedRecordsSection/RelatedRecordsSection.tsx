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
import { cn } from '@/lib/utils'
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from '@/components/ui/table'
import type { ApiClient } from '../../services/apiClient'

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
const REFERENCE_FIELD_TYPES = new Set(['master_detail'])

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
    queryFn: () => apiClient.getList<CollectionSchema>('/api/collections'),
    enabled: !!collectionName && !!recordId,
  })

  // Discover related collections: collections that have fields referencing the current collection
  const relatedCollections = useMemo<RelatedCollection[]>(() => {
    if (!collectionsResponse || collectionsResponse.length === 0) return []

    const related: RelatedCollection[] = []

    for (const collection of collectionsResponse) {
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
        className="p-6 bg-background border border-border rounded-md max-md:p-4"
        aria-labelledby="related-records-heading"
        data-testid="related-records-section"
      >
        <div className="flex justify-between items-center mb-4">
          <h2 id="related-records-heading" className="m-0 text-lg font-semibold text-foreground">
            {t('relatedRecords.title')}
          </h2>
        </div>
        <div
          className="flex justify-center items-center py-8 min-h-[120px]"
          data-testid="related-records-loading"
        >
          <LoadingSpinner size="medium" label={t('common.loading')} />
        </div>
      </section>
    )
  }

  // Error state
  if (collectionsError) {
    return (
      <section
        className="p-6 bg-background border border-border rounded-md max-md:p-4"
        aria-labelledby="related-records-heading"
        data-testid="related-records-section"
      >
        <div className="flex justify-between items-center mb-4">
          <h2 id="related-records-heading" className="m-0 text-lg font-semibold text-foreground">
            {t('relatedRecords.title')}
          </h2>
        </div>
        <div
          className="flex flex-col items-center justify-center py-8 text-center text-muted-foreground bg-muted/50 rounded"
          data-testid="related-records-error"
        >
          <p className="m-0 text-sm">{t('relatedRecords.errorLoading')}</p>
        </div>
      </section>
    )
  }

  // No relationships found
  if (relatedCollections.length === 0) {
    return (
      <section
        className="p-6 bg-background border border-border rounded-md max-md:p-4"
        aria-labelledby="related-records-heading"
        data-testid="related-records-section"
      >
        <div className="flex justify-between items-center mb-4">
          <h2 id="related-records-heading" className="m-0 text-lg font-semibold text-foreground">
            {t('relatedRecords.title')}
          </h2>
        </div>
        <div
          className="flex flex-col items-center justify-center py-8 text-center text-muted-foreground bg-muted/50 rounded"
          data-testid="related-records-empty"
        >
          <p className="m-0 text-sm">{t('relatedRecords.noRelatedRecords')}</p>
        </div>
      </section>
    )
  }

  const records = relatedRecordsResponse?.data ?? []
  const totalCount = relatedRecordsResponse?.total ?? 0

  return (
    <section
      className="p-6 bg-background border border-border rounded-md max-md:p-4"
      aria-labelledby="related-records-heading"
      data-testid="related-records-section"
    >
      {/* Section Header */}
      <div className="flex justify-between items-center mb-4">
        <h2 id="related-records-heading" className="m-0 text-lg font-semibold text-foreground">
          {t('relatedRecords.title')}
        </h2>
      </div>

      {/* Tab Bar */}
      <div
        className="flex gap-1 overflow-x-auto border-b border-border mb-4 scrollbar-thin"
        role="tablist"
        aria-label={t('relatedRecords.tabsLabel')}
      >
        {relatedCollections.map((related, index) => (
          <button
            key={`${related.collection.name}-${related.referenceField.name}`}
            type="button"
            role="tab"
            id={`related-tab-${index}`}
            aria-selected={index === safeActiveTab}
            aria-controls={`related-tabpanel-${index}`}
            className={cn(
              'inline-flex items-center gap-1 px-4 py-2 text-sm font-medium whitespace-nowrap',
              'bg-transparent border-0 border-b-2 border-transparent cursor-pointer',
              'transition-colors motion-reduce:transition-none',
              'hover:text-foreground hover:bg-muted/50',
              'focus:outline-2 focus:outline-primary focus:-outline-offset-2 focus:rounded-t',
              'focus:not(:focus-visible):outline-none',
              'max-md:px-2 max-md:py-1 max-md:text-xs',
              index === safeActiveTab
                ? 'text-primary border-b-primary hover:text-primary'
                : 'text-muted-foreground'
            )}
            onClick={() => setActiveTab(index)}
            data-testid={`related-tab-${related.collection.name}`}
          >
            {related.collection.displayName || related.collection.name}
            {index === safeActiveTab && !recordsLoading && (
              <span
                className={cn(
                  'inline-flex items-center justify-center min-w-[20px] h-5 px-1',
                  'text-xs font-semibold rounded-full leading-none',
                  index === safeActiveTab
                    ? 'text-primary-foreground bg-primary'
                    : 'text-muted-foreground bg-muted'
                )}
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
          <div
            className="flex justify-center items-center py-6"
            data-testid="related-records-tab-loading"
          >
            <LoadingSpinner size="small" label={t('common.loading')} />
          </div>
        ) : records.length === 0 ? (
          <div
            className="flex items-center justify-center py-6 text-center text-muted-foreground text-sm"
            data-testid="related-records-tab-empty"
          >
            {t('relatedRecords.noRecordsInCollection', {
              collection: activeRelated?.collection.displayName || '',
            })}
          </div>
        ) : (
          <>
            <Table
              role="grid"
              aria-label={t('relatedRecords.tableLabel', {
                collection: activeRelated?.collection.displayName || '',
              })}
              data-testid="related-records-table"
            >
              <TableHeader>
                <TableRow>
                  {displayFields.map((field) => (
                    <TableHead key={field.name} scope="col">
                      {field.displayName || field.name}
                    </TableHead>
                  ))}
                  <TableHead scope="col">{t('relatedRecords.created')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {records.map((record) => (
                  <TableRow
                    key={record.id}
                    className="cursor-pointer"
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
                      <TableCell
                        key={field.name}
                        className="max-w-[200px] overflow-hidden text-ellipsis"
                      >
                        {formatCellValue(record[field.name])}
                      </TableCell>
                    ))}
                    <TableCell>{formatCreatedDate(record.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {/* View All Link */}
            <Link
              to={`/${getTenantSlug()}/resources/${activeRelated!.collection.name}`}
              className={cn(
                'inline-flex items-center gap-1 mt-4 text-sm font-medium',
                'text-primary no-underline',
                'transition-colors motion-reduce:transition-none',
                'hover:text-primary/80 hover:underline',
                'focus:outline-2 focus:outline-primary focus:outline-offset-2 focus:rounded'
              )}
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
