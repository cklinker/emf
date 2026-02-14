/**
 * ResourceDetailPage Component
 *
 * Displays a single resource record with all field values formatted
 * according to their field types. Provides edit and delete actions.
 *
 * Requirements:
 * - 11.7: Resource browser displays resource detail view
 * - 11.8: Resource browser allows viewing all field values
 * - 11.10: Resource browser allows deleting resources with confirmation
 */

import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'
import { useApi } from '../../context/ApiContext'
import { useAuth } from '../../context/AuthContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { useRecentRecords } from '../../hooks/useRecentRecords'
import { useFavorites } from '../../hooks/useFavorites'
import { RecordHeader } from '../../components/RecordHeader/RecordHeader'
import { RecordActionsBar } from '../../components/RecordActionsBar/RecordActionsBar'
import { RelatedRecordsSection } from '../../components/RelatedRecordsSection/RelatedRecordsSection'
import { ActivityTimeline } from '../../components/ActivityTimeline/ActivityTimeline'
import { NotesSection } from '../../components/NotesSection/NotesSection'
import { AttachmentsSection } from '../../components/AttachmentsSection/AttachmentsSection'
import { unwrapResource } from '../../utils/jsonapi'
import type { ApiClient } from '../../services/apiClient'
import styles from './ResourceDetailPage.module.css'

/**
 * Field definition interface for collection schema
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference'
  required: boolean
  referenceTarget?: string
}

/**
 * Collection schema interface
 */
export interface CollectionSchema {
  id: string
  name: string
  displayName: string
  fields: FieldDefinition[]
}

/**
 * Resource record interface
 */
export interface Resource {
  id: string
  [key: string]: unknown
}

export interface RecordShare {
  id: string
  tenantId: string
  collectionId: string
  recordId: string
  sharedWithId: string
  sharedWithType: string
  accessLevel: string
  reason: string
  createdBy: string
  createdAt: string
}

interface ShareFormProps {
  onSubmit: (data: { sharedWithId: string; sharedWithType: string; accessLevel: string }) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ShareForm({ onSubmit, onCancel, isSubmitting }: ShareFormProps): React.ReactElement {
  const [sharedWithId, setSharedWithId] = useState('')
  const [sharedWithType, setSharedWithType] = useState('USER')
  const [accessLevel, setAccessLevel] = useState('READ')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (sharedWithId.trim()) {
        onSubmit({ sharedWithId: sharedWithId.trim(), sharedWithType, accessLevel })
      }
    },
    [sharedWithId, sharedWithType, accessLevel, onSubmit]
  )

  return (
    <div
      className={styles.shareModalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={(e) => e.key === 'Escape' && onCancel()}
      role="presentation"
    >
      <div
        className={styles.shareModal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-form-title"
      >
        <div className={styles.shareModalHeader}>
          <h3 id="share-form-title" className={styles.shareModalTitle}>
            Share Record
          </h3>
          <button
            type="button"
            className={styles.shareModalClose}
            onClick={onCancel}
            aria-label="Close"
          >
            &times;
          </button>
        </div>
        <form className={styles.shareModalBody} onSubmit={handleSubmit}>
          <div className={styles.shareFormGroup}>
            <label htmlFor="share-with-id" className={styles.shareFormLabel}>
              Shared With ID
            </label>
            <input
              ref={inputRef}
              id="share-with-id"
              type="text"
              className={styles.shareFormInput}
              value={sharedWithId}
              onChange={(e) => setSharedWithId(e.target.value)}
              placeholder="Enter user, group, or role ID"
              required
              disabled={isSubmitting}
            />
          </div>
          <div className={styles.shareFormGroup}>
            <label htmlFor="share-type" className={styles.shareFormLabel}>
              Type
            </label>
            <select
              id="share-type"
              className={styles.shareFormInput}
              value={sharedWithType}
              onChange={(e) => setSharedWithType(e.target.value)}
              disabled={isSubmitting}
            >
              <option value="USER">User</option>
              <option value="GROUP">Group</option>
              <option value="ROLE">Role</option>
            </select>
          </div>
          <div className={styles.shareFormGroup}>
            <label htmlFor="share-access" className={styles.shareFormLabel}>
              Access Level
            </label>
            <select
              id="share-access"
              className={styles.shareFormInput}
              value={accessLevel}
              onChange={(e) => setAccessLevel(e.target.value)}
              disabled={isSubmitting}
            >
              <option value="READ">Read</option>
              <option value="READ_WRITE">Read/Write</option>
            </select>
          </div>
          <div className={styles.shareFormActions}>
            <button
              type="button"
              className={styles.shareFormCancel}
              onClick={onCancel}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button type="submit" className={styles.shareFormSubmit} disabled={isSubmitting}>
              {isSubmitting ? 'Sharing...' : 'Share'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/**
 * Props for ResourceDetailPage component
 */
export interface ResourceDetailPageProps {
  /** Collection name from route params (optional, can be from useParams) */
  collectionName?: string
  /** Resource ID from route params (optional, can be from useParams) */
  resourceId?: string
  /** Optional test ID for testing */
  testId?: string
}

// API functions using apiClient
async function fetchCollectionSchema(
  apiClient: ApiClient,
  collectionName: string
): Promise<CollectionSchema> {
  return apiClient.get(`/control/collections/${collectionName}`)
}

async function fetchResource(
  apiClient: ApiClient,
  collectionName: string,
  resourceId: string
): Promise<Resource> {
  const response = await apiClient.get(`/api/${collectionName}/${resourceId}`)
  return unwrapResource<Resource>(response)
}

async function deleteResource(
  apiClient: ApiClient,
  collectionName: string,
  resourceId: string
): Promise<void> {
  return apiClient.delete(`/api/${collectionName}/${resourceId}`)
}

/**
 * ResourceDetailPage Component
 *
 * Main page for viewing a single resource record.
 * Displays all field values with appropriate formatting and provides
 * edit and delete actions.
 */
export function ResourceDetailPage({
  collectionName: propCollectionName,
  resourceId: propResourceId,
  testId = 'resource-detail-page',
}: ResourceDetailPageProps): React.ReactElement {
  const { collection: routeCollection, id: routeResourceId } = useParams<{
    collection: string
    id: string
  }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t, formatDate, formatNumber } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const { user } = useAuth()
  const userId = user?.id ?? 'anonymous'
  const { addRecentRecord } = useRecentRecords(userId)
  const { isFavorite, addFavorite, removeFavorite } = useFavorites(userId)

  // Get collection name and resource ID from props or route params
  const collectionName = propCollectionName || routeCollection || ''
  const resourceId = propResourceId || routeResourceId || ''

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)

  // Share modal state
  const [shareModalOpen, setShareModalOpen] = useState(false)

  // Fetch collection schema
  const {
    data: schema,
    isLoading: schemaLoading,
    error: schemaError,
  } = useQuery({
    queryKey: ['collection-schema', collectionName],
    queryFn: () => fetchCollectionSchema(apiClient, collectionName),
    enabled: !!collectionName,
  })

  // Fetch resource data
  const {
    data: resource,
    isLoading: resourceLoading,
    error: resourceError,
    refetch: refetchResource,
  } = useQuery({
    queryKey: ['resource', collectionName, resourceId],
    queryFn: () => fetchResource(apiClient, collectionName, resourceId),
    enabled: !!collectionName && !!resourceId,
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => deleteResource(apiClient, collectionName, resourceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['resources', collectionName] })
      showToast(t('success.deleted', { item: t('resources.record') }), 'success')
      navigate(`/${getTenantSlug()}/resources/${collectionName}`)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Sharing: fetch shares for this record
  const { data: shares } = useQuery({
    queryKey: ['record-shares', schema?.id, resourceId],
    queryFn: () =>
      apiClient.get<RecordShare[]>(`/control/sharing/records/${schema!.id}/${resourceId}`),
    enabled: !!schema?.id && !!resourceId,
  })

  const sharesList: RecordShare[] = Array.isArray(shares) ? shares : []

  // Sharing: create share
  const createShareMutation = useMutation({
    mutationFn: (data: { sharedWithId: string; sharedWithType: string; accessLevel: string }) =>
      apiClient.post(`/control/sharing/records/${schema!.id}`, {
        recordId: resourceId,
        ...data,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['record-shares', schema?.id, resourceId] })
      showToast(t('sharing.shareCreated'), 'success')
      setShareModalOpen(false)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  // Sharing: delete share
  const deleteShareMutation = useMutation({
    mutationFn: (shareId: string) => apiClient.delete(`/control/sharing/records/shares/${shareId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['record-shares', schema?.id, resourceId] })
      showToast(t('sharing.shareDeleted'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  // Sort fields by order
  const sortedFields = useMemo(() => {
    if (!schema?.fields) return []
    return [...schema.fields].sort((a, b) => {
      // Fields may not have order property, so default to 0
      const orderA = (a as FieldDefinition & { order?: number }).order ?? 0
      const orderB = (b as FieldDefinition & { order?: number }).order ?? 0
      return orderA - orderB
    })
  }, [schema])

  // Track recent record view
  useEffect(() => {
    if (resource && schema && collectionName && resourceId) {
      const firstStringField = schema.fields?.find((f) => f.type === 'string')
      const displayValue = firstStringField
        ? String(resource[firstStringField.name] ?? resource.id)
        : String(resource.id)
      addRecentRecord({
        id: resourceId,
        collectionName,
        collectionDisplayName: schema.displayName || collectionName,
        displayValue,
      })
    }
    // Only track once when resource loads
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resource?.id, schema?.id])

  const recordIsFavorite = isFavorite(resourceId, 'record')

  const handleToggleFavorite = useCallback(() => {
    if (recordIsFavorite) {
      removeFavorite(resourceId, 'record')
    } else {
      const firstStringField = schema?.fields?.find((f) => f.type === 'string')
      const displayValue = firstStringField
        ? String(resource?.[firstStringField.name] ?? resourceId)
        : String(resourceId)
      addFavorite({
        id: resourceId,
        type: 'record',
        collectionName,
        collectionDisplayName: schema?.displayName || collectionName,
        displayValue,
      })
    }
  }, [recordIsFavorite, resourceId, collectionName, schema, resource, addFavorite, removeFavorite])

  // Handle back navigation
  const handleBack = useCallback(() => {
    navigate(`/${getTenantSlug()}/resources/${collectionName}`)
  }, [navigate, collectionName])

  // Handle edit action
  const handleEdit = useCallback(() => {
    navigate(`/${getTenantSlug()}/resources/${collectionName}/${resourceId}/edit`)
  }, [navigate, collectionName, resourceId])

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback(() => {
    setDeleteDialogOpen(true)
  }, [])

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    deleteMutation.mutate()
  }, [deleteMutation])

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
  }, [])

  /**
   * Format field value based on field type
   * Requirement 11.8: Display all field values with appropriate formatting
   */
  const formatFieldValue = useCallback(
    (value: unknown, field: FieldDefinition): React.ReactNode => {
      if (value === null || value === undefined) {
        return <span className={styles.emptyValue}>-</span>
      }

      switch (field.type) {
        case 'boolean':
          return (
            <span className={value ? styles.booleanTrue : styles.booleanFalse}>
              {value ? t('common.yes') : t('common.no')}
            </span>
          )

        case 'number':
          return <span className={styles.numberValue}>{formatNumber(value as number)}</span>

        case 'date':
          try {
            return formatDate(new Date(value as string), {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })
          } catch {
            return String(value)
          }

        case 'datetime':
          try {
            return formatDate(new Date(value as string), {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            })
          } catch {
            return String(value)
          }

        case 'json':
          if (typeof value === 'object') {
            return (
              <pre className={styles.jsonValue}>
                <code>{JSON.stringify(value, null, 2)}</code>
              </pre>
            )
          }
          return String(value)

        case 'reference':
          // For reference fields, display the ID with a link if possible
          return (
            <span className={styles.referenceValue}>
              {field.referenceTarget ? (
                <Link
                  to={`/${getTenantSlug()}/resources/${field.referenceTarget}/${value}`}
                  className={styles.referenceLink}
                >
                  {String(value)}
                </Link>
              ) : (
                String(value)
              )}
            </span>
          )

        case 'string':
        default: {
          const stringValue = String(value)
          if (stringValue.length > 500) {
            return <div className={styles.longTextValue}>{stringValue}</div>
          }
          return stringValue
        }
      }
    },
    [t, formatDate, formatNumber]
  )

  /**
   * Get field type display label
   */
  const getFieldTypeLabel = useCallback(
    (type: FieldDefinition['type']): string => {
      return t(`fields.types.${type.toLowerCase()}`)
    },
    [t]
  )

  // Loading state
  const isLoading = schemaLoading || resourceLoading

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Error state - schema error
  if (schemaError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={schemaError instanceof Error ? schemaError : new Error(t('errors.generic'))}
          onRetry={() =>
            queryClient.invalidateQueries({ queryKey: ['collection-schema', collectionName] })
          }
        />
      </div>
    )
  }

  // Error state - resource error
  if (resourceError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={resourceError instanceof Error ? resourceError : new Error(t('errors.generic'))}
          onRetry={() => refetchResource()}
        />
      </div>
    )
  }

  // Not found state
  if (!schema || !resource) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage error={new Error(t('errors.notFound'))} />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Breadcrumb Navigation */}
      <nav className={styles.breadcrumb} aria-label="Breadcrumb">
        <Link to={`/${getTenantSlug()}/resources`} className={styles.breadcrumbLink}>
          {t('resources.title')}
        </Link>
        <span className={styles.breadcrumbSeparator} aria-hidden="true">
          /
        </span>
        <Link
          to={`/${getTenantSlug()}/resources/${collectionName}`}
          className={styles.breadcrumbLink}
        >
          {schema.displayName}
        </Link>
        <span className={styles.breadcrumbSeparator} aria-hidden="true">
          /
        </span>
        <span className={styles.breadcrumbCurrent}>{resource.id}</span>
      </nav>

      {/* Record Header (T9) */}
      <RecordHeader
        record={
          resource as Record<string, unknown> & {
            id: string
            createdAt?: string
            updatedAt?: string
          }
        }
        schema={schema}
        collectionName={collectionName}
      />

      {/* Record Actions Bar (T8) */}
      <RecordActionsBar
        collectionName={collectionName}
        recordId={resourceId}
        onEdit={handleEdit}
        onDelete={handleDeleteClick}
        onBack={handleBack}
        isFavorite={recordIsFavorite}
        onToggleFavorite={handleToggleFavorite}
        apiClient={apiClient}
      />

      {/* Field Values Section */}
      <section className={styles.fieldsSection} aria-labelledby="fields-heading">
        <h2 id="fields-heading" className={styles.sectionTitle}>
          {t('collections.fields')}
        </h2>

        {sortedFields.length === 0 ? (
          <div className={styles.emptyState} data-testid="no-fields">
            <p>{t('common.noData')}</p>
          </div>
        ) : (
          <div className={styles.fieldsGrid} data-testid="fields-grid">
            {sortedFields.map((field, index) => (
              <div key={field.id} className={styles.fieldItem} data-testid={`field-item-${index}`}>
                <div className={styles.fieldHeader}>
                  <span className={styles.fieldName}>{field.displayName || field.name}</span>
                  <span className={styles.fieldType}>{getFieldTypeLabel(field.type)}</span>
                </div>
                <div className={styles.fieldValue} data-testid={`field-value-${field.name}`}>
                  {formatFieldValue(resource[field.name], field)}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Metadata Section */}
      <section className={styles.metadataSection} aria-labelledby="metadata-heading">
        <h2 id="metadata-heading" className={styles.sectionTitle}>
          Metadata
        </h2>
        <div className={styles.metadataGrid}>
          {(resource.created_at || resource.createdAt) && (
            <div className={styles.metadataItem}>
              <span className={styles.metadataLabel}>{t('collections.created')}</span>
              <span className={styles.metadataValue} data-testid="created-at">
                {formatDate(new Date((resource.created_at || resource.createdAt) as string), {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          )}
          {(resource.updated_at || resource.updatedAt) && (
            <div className={styles.metadataItem}>
              <span className={styles.metadataLabel}>{t('collections.updated')}</span>
              <span className={styles.metadataValue} data-testid="updated-at">
                {formatDate(new Date((resource.updated_at || resource.updatedAt) as string), {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          )}
          {resource.created_by && (
            <div className={styles.metadataItem}>
              <span className={styles.metadataLabel}>Created by</span>
              <span className={styles.metadataValue} data-testid="created-by">
                {String(resource.created_by)}
              </span>
            </div>
          )}
          {resource.updated_by && (
            <div className={styles.metadataItem}>
              <span className={styles.metadataLabel}>Last modified by</span>
              <span className={styles.metadataValue} data-testid="updated-by">
                {String(resource.updated_by)}
              </span>
            </div>
          )}
        </div>
      </section>

      {/* Sharing Section */}
      <section className={styles.sharingSection} aria-labelledby="sharing-heading">
        <div className={styles.sharingSectionHeader}>
          <h2 id="sharing-heading" className={styles.sectionTitle}>
            Sharing
          </h2>
          <button
            type="button"
            className={styles.shareButton}
            onClick={() => setShareModalOpen(true)}
            data-testid="share-button"
          >
            {t('sharing.shareRecord')}
          </button>
        </div>
        {sharesList.length === 0 ? (
          <div className={styles.emptyState} data-testid="no-shares">
            <p>{t('sharing.noShares')}</p>
          </div>
        ) : (
          <div className={styles.sharingTableContainer}>
            <table className={styles.sharingTable} role="grid" aria-label="Record shares">
              <thead>
                <tr>
                  <th>{t('sharing.sharedWith')}</th>
                  <th>{t('sharing.sharedWithType')}</th>
                  <th>{t('sharing.accessLevel')}</th>
                  <th>Reason</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {sharesList.map((share) => (
                  <tr key={share.id}>
                    <td className={styles.monoCell}>{share.sharedWithId}</td>
                    <td>
                      <span className={styles.shareBadge}>{share.sharedWithType}</span>
                    </td>
                    <td>
                      <span className={styles.shareBadge}>{share.accessLevel}</span>
                    </td>
                    <td>{share.reason || '-'}</td>
                    <td>
                      <button
                        type="button"
                        className={styles.removeButton}
                        onClick={() => deleteShareMutation.mutate(share.id)}
                        disabled={deleteShareMutation.isPending}
                        aria-label={`Remove share for ${share.sharedWithId}`}
                      >
                        {t('sharing.removeShare')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Related Records Section (T6) */}
      <RelatedRecordsSection
        collectionName={collectionName}
        recordId={resourceId}
        apiClient={apiClient}
      />

      {/* Notes Section (T20) */}
      <NotesSection collectionId={schema.id} recordId={resourceId} apiClient={apiClient} />

      {/* Attachments Section (T20) */}
      <AttachmentsSection collectionId={schema.id} recordId={resourceId} apiClient={apiClient} />

      {/* Activity Timeline (T7) */}
      <ActivityTimeline
        collectionId={schema.id}
        collectionName={collectionName}
        recordId={resourceId}
        recordCreatedAt={(resource.created_at || resource.createdAt) as string | undefined}
        recordUpdatedAt={(resource.updated_at || resource.updatedAt) as string | undefined}
        apiClient={apiClient}
      />

      {/* Share Modal */}
      {shareModalOpen && (
        <ShareForm
          onSubmit={(data) => createShareMutation.mutate(data)}
          onCancel={() => setShareModalOpen(false)}
          isSubmitting={createShareMutation.isPending}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('resources.deleteRecord')}
        message={t('resources.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ResourceDetailPage
