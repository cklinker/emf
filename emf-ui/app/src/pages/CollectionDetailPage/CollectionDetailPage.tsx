/**
 * CollectionDetailPage Component
 *
 * Displays detailed information about a single collection including:
 * - Collection metadata (name, displayName, description, storageMode, status)
 * - Fields list with field details
 * - Authorization configuration (route and field policies)
 * - Version history
 *
 * Requirements:
 * - 3.7: Navigate to collection detail page when clicking on a collection
 * - 3.8: Display collection metadata and list of fields
 * - 3.12: Display collection version history with ability to view previous versions
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import {
  FieldEditor,
  type FieldDefinition as FieldEditorDefinition,
} from '../../components/FieldEditor'
import type { Collection, FieldDefinition, CollectionVersion } from '../../types/collections'
import styles from './CollectionDetailPage.module.css'

/**
 * Props for CollectionDetailPage component
 */
export interface CollectionDetailPageProps {
  /** Collection ID/name from route params (optional, can be from useParams) */
  collectionId?: string
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Get display text for field type
 */
function getFieldTypeDisplay(type: FieldDefinition['type'], t: (key: string) => string): string {
  return t(`fields.types.${type}`)
}

/**
 * Get display text for storage mode
 */
function getStorageModeDisplay(mode: Collection['storageMode']): string {
  switch (mode) {
    case 'PHYSICAL_TABLE':
      return 'Physical Table'
    case 'JSONB':
      return 'JSONB'
    default:
      return mode
  }
}

/**
 * CollectionDetailPage Component
 *
 * Main page for viewing and managing a single collection.
 * Provides metadata display, fields list, authorization config, and version history.
 */
export function CollectionDetailPage({
  collectionId: propCollectionId,
  testId = 'collection-detail-page',
}: CollectionDetailPageProps): React.ReactElement {
  const params = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { showToast } = useToast()
  const { apiClient } = useApi()

  // Get collection ID from props or route params
  const collectionId = propCollectionId || params.id || ''

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)

  // Field editor modal state
  const [fieldEditorOpen, setFieldEditorOpen] = useState(false)
  const [editingField, setEditingField] = useState<FieldDefinition | undefined>(undefined)

  // Active tab state for sections
  const [activeTab, setActiveTab] = useState<'fields' | 'authorization' | 'versions'>('fields')

  // Fetch collection data
  const {
    data: collection,
    isLoading: isLoadingCollection,
    error: collectionError,
    refetch: refetchCollection,
  } = useQuery({
    queryKey: ['collection', collectionId],
    queryFn: async () => {
      const response = await apiClient.get<Collection>(`/control/collections/${collectionId}`)
      return response
    },
    enabled: !!collectionId,
  })

  // Fetch version history
  const {
    data: versions = [],
    isLoading: isLoadingVersions,
    error: versionsError,
  } = useQuery({
    queryKey: ['collection-versions', collectionId],
    queryFn: async () => {
      const response = await apiClient.get<CollectionVersion[]>(
        `/control/collections/${collectionId}/versions`
      )
      return response
    },
    enabled: !!collectionId && activeTab === 'versions',
  })

  // Fetch all collections for reference field dropdown
  const { data: collectionsPage } = useQuery({
    queryKey: ['collections'],
    queryFn: async () => {
      const response = await apiClient.get<{ content: Collection[] }>(
        '/control/collections?size=1000'
      )
      return response
    },
    enabled: fieldEditorOpen,
  })

  const allCollections = collectionsPage?.content || []

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete(`/control/collections/${collectionId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collections'] })
      showToast(t('success.deleted', { item: t('collections.title') }), 'success')
      navigate('/collections')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Add field mutation
  const addFieldMutation = useMutation({
    mutationFn: async (fieldData: Omit<FieldEditorDefinition, 'id' | 'order'>) => {
      await apiClient.post(`/control/collections/${collectionId}/fields`, fieldData)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collection', collectionId] })
      showToast(t('success.created', { item: t('collections.field') }), 'success')
      setFieldEditorOpen(false)
      setEditingField(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Update field mutation
  const updateFieldMutation = useMutation({
    mutationFn: async ({
      fieldId,
      fieldData,
    }: {
      fieldId: string
      fieldData: Partial<FieldEditorDefinition>
    }) => {
      await apiClient.put(`/control/collections/${collectionId}/fields/${fieldId}`, fieldData)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collection', collectionId] })
      showToast(t('success.updated', { item: t('collections.field') }), 'success')
      setFieldEditorOpen(false)
      setEditingField(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Handle edit action
  const handleEdit = useCallback(() => {
    navigate(`/collections/${collectionId}/edit`)
  }, [navigate, collectionId])

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

  // Handle back navigation
  const handleBack = useCallback(() => {
    navigate('/collections')
  }, [navigate])

  // Handle tab change
  const handleTabChange = useCallback((tab: 'fields' | 'authorization' | 'versions') => {
    setActiveTab(tab)
  }, [])

  // Handle add field action
  const handleAddField = useCallback(() => {
    setEditingField(undefined)
    setFieldEditorOpen(true)
  }, [])

  // Handle edit field action
  const handleEditField = useCallback((field: FieldDefinition) => {
    setEditingField(field)
    setFieldEditorOpen(true)
  }, [])

  // Handle field editor save
  const handleFieldSave = useCallback(
    async (fieldData: FieldEditorDefinition) => {
      if (editingField) {
        // Update existing field
        await updateFieldMutation.mutateAsync({
          fieldId: editingField.id,
          fieldData: {
            name: fieldData.name,
            displayName: fieldData.displayName,
            type: fieldData.type,
            required: fieldData.required,
            unique: fieldData.unique,
            indexed: fieldData.indexed,
            defaultValue: fieldData.defaultValue,
            referenceTarget: fieldData.referenceTarget,
            validation: fieldData.validation,
          },
        })
      } else {
        // Create new field
        await addFieldMutation.mutateAsync({
          name: fieldData.name,
          displayName: fieldData.displayName,
          type: fieldData.type,
          required: fieldData.required,
          unique: fieldData.unique,
          indexed: fieldData.indexed,
          defaultValue: fieldData.defaultValue,
          referenceTarget: fieldData.referenceTarget,
          validation: fieldData.validation,
        })
      }
    },
    [editingField, addFieldMutation, updateFieldMutation]
  )

  // Handle field editor cancel
  const handleFieldCancel = useCallback(() => {
    setFieldEditorOpen(false)
    setEditingField(undefined)
  }, [])

  // Handle view version action
  const handleViewVersion = useCallback(
    (version: CollectionVersion) => {
      navigate(`/collections/${collectionId}/versions/${version.version}`)
    },
    [navigate, collectionId]
  )

  // Sort fields by order
  const sortedFields = useMemo(() => {
    if (!collection?.fields) return []
    return [...collection.fields].sort((a, b) => a.order - b.order)
  }, [collection])

  // Render loading state
  if (isLoadingCollection) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
  if (collectionError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={
            collectionError instanceof Error ? collectionError : new Error(t('errors.generic'))
          }
          onRetry={() => refetchCollection()}
        />
      </div>
    )
  }

  // Render not found state
  if (!collection) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage error={new Error(t('errors.notFound'))} type="notFound" />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <button
            type="button"
            className={styles.backButton}
            onClick={handleBack}
            aria-label={t('common.back')}
            data-testid="back-button"
          >
            ← {t('common.back')}
          </button>
          <div className={styles.titleSection}>
            <h1 className={styles.title} data-testid="collection-title">
              {collection.displayName || collection.name}
            </h1>
            <span
              className={`${styles.statusBadge} ${
                collection.active ? styles.statusActive : styles.statusInactive
              }`}
              data-testid="collection-status"
            >
              {collection.active ? t('collections.active') : t('collections.inactive')}
            </span>
          </div>
        </div>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.editButton}
            onClick={handleEdit}
            aria-label={t('collections.editCollection')}
            data-testid="edit-button"
          >
            {t('common.edit')}
          </button>
          <button
            type="button"
            className={styles.deleteButton}
            onClick={handleDeleteClick}
            aria-label={t('collections.deleteCollection')}
            data-testid="delete-button"
          >
            {t('common.delete')}
          </button>
        </div>
      </header>

      {/* Collection Metadata */}
      <section className={styles.metadataSection} aria-labelledby="metadata-heading">
        <h2 id="metadata-heading" className={styles.sectionTitle}>
          {t('collections.collectionName')}
        </h2>
        <div className={styles.metadataGrid}>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>{t('collections.collectionName')}</span>
            <span className={styles.metadataValue} data-testid="collection-name">
              {collection.name}
            </span>
          </div>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>{t('collections.displayName')}</span>
            <span className={styles.metadataValue} data-testid="collection-display-name">
              {collection.displayName || '-'}
            </span>
          </div>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>{t('collections.storageMode')}</span>
            <span className={styles.metadataValue} data-testid="collection-storage-mode">
              {getStorageModeDisplay(collection.storageMode)}
            </span>
          </div>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>{t('collections.status')}</span>
            <span className={styles.metadataValue} data-testid="collection-status-value">
              {collection.active ? t('collections.active') : t('collections.inactive')}
            </span>
          </div>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>Version</span>
            <span className={styles.metadataValue} data-testid="collection-version">
              {collection.currentVersion}
            </span>
          </div>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>Created</span>
            <span className={styles.metadataValue} data-testid="collection-created">
              {formatDate(new Date(collection.createdAt), {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
          </div>
          <div className={styles.metadataItem}>
            <span className={styles.metadataLabel}>Updated</span>
            <span className={styles.metadataValue} data-testid="collection-updated">
              {formatDate(new Date(collection.updatedAt), {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
          </div>
          {collection.description && (
            <div className={`${styles.metadataItem} ${styles.metadataItemFull}`}>
              <span className={styles.metadataLabel}>{t('collections.description')}</span>
              <span className={styles.metadataValue} data-testid="collection-description">
                {collection.description}
              </span>
            </div>
          )}
        </div>
      </section>

      {/* Tabs Navigation */}
      <div className={styles.tabsNav} role="tablist" aria-label="Collection sections">
        <button
          type="button"
          role="tab"
          className={`${styles.tab} ${activeTab === 'fields' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('fields')}
          aria-selected={activeTab === 'fields'}
          aria-controls="fields-panel"
          id="fields-tab"
          data-testid="fields-tab"
        >
          {t('collections.fields')} ({sortedFields.length})
        </button>
        <button
          type="button"
          role="tab"
          className={`${styles.tab} ${activeTab === 'authorization' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('authorization')}
          aria-selected={activeTab === 'authorization'}
          aria-controls="authorization-panel"
          id="authorization-tab"
          data-testid="authorization-tab"
        >
          {t('authorization.title')}
        </button>
        <button
          type="button"
          role="tab"
          className={`${styles.tab} ${activeTab === 'versions' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('versions')}
          aria-selected={activeTab === 'versions'}
          aria-controls="versions-panel"
          id="versions-tab"
          data-testid="versions-tab"
        >
          {t('collections.versionHistory')}
        </button>
      </div>

      {/* Fields Panel */}
      {activeTab === 'fields' && (
        <section
          id="fields-panel"
          role="tabpanel"
          aria-labelledby="fields-tab"
          className={styles.tabPanel}
          data-testid="fields-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('collections.fields')}</h2>
            <button
              type="button"
              className={styles.addButton}
              onClick={handleAddField}
              aria-label={t('collections.addField')}
              data-testid="add-field-button"
            >
              {t('collections.addField')}
            </button>
          </div>
          {sortedFields.length === 0 ? (
            <div className={styles.emptyState} data-testid="fields-empty-state">
              <p>{t('common.noData')}</p>
              <button type="button" className={styles.addButton} onClick={handleAddField}>
                {t('collections.addField')}
              </button>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table
                className={styles.table}
                aria-label={t('collections.fields')}
                data-testid="fields-table"
              >
                <thead>
                  <tr>
                    <th scope="col">#</th>
                    <th scope="col">{t('collections.fieldName')}</th>
                    <th scope="col">{t('collections.displayName')}</th>
                    <th scope="col">{t('collections.fieldType')}</th>
                    <th scope="col">{t('fields.validation.required')}</th>
                    <th scope="col">{t('fields.validation.unique')}</th>
                    <th scope="col">{t('fields.validation.indexed')}</th>
                    <th scope="col">{t('fields.relationship')}</th>
                    <th scope="col">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedFields.map((field, index) => (
                    <tr
                      key={field.id}
                      className={styles.tableRow}
                      data-testid={`field-row-${index}`}
                    >
                      <td>{field.order}</td>
                      <td className={styles.fieldNameCell}>{field.name}</td>
                      <td>{field.displayName || '-'}</td>
                      <td>
                        <span className={styles.fieldTypeBadge}>
                          {getFieldTypeDisplay(field.type, t)}
                        </span>
                      </td>
                      <td>
                        <span className={field.required ? styles.checkMark : styles.crossMark}>
                          {field.required ? '✓' : '✕'}
                        </span>
                      </td>
                      <td>
                        <span className={field.unique ? styles.checkMark : styles.crossMark}>
                          {field.unique ? '✓' : '✕'}
                        </span>
                      </td>
                      <td>
                        <span className={field.indexed ? styles.checkMark : styles.crossMark}>
                          {field.indexed ? '✓' : '✕'}
                        </span>
                      </td>
                      <td>
                        {field.relationshipType ? (
                          <span
                            className={styles.relationshipBadge}
                            title={field.referenceTarget || ''}
                          >
                            {field.relationshipType === 'MASTER_DETAIL'
                              ? 'Master-Detail'
                              : 'Lookup'}
                            {field.relationshipName ? ` → ${field.relationshipName}` : ''}
                          </span>
                        ) : (
                          '-'
                        )}
                      </td>
                      <td className={styles.actionsCell}>
                        <button
                          type="button"
                          className={styles.actionButton}
                          onClick={() => handleEditField(field)}
                          aria-label={`${t('common.edit')} ${field.name}`}
                          data-testid={`edit-field-button-${index}`}
                        >
                          {t('common.edit')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Authorization Panel */}
      {activeTab === 'authorization' && (
        <section
          id="authorization-panel"
          role="tabpanel"
          aria-labelledby="authorization-tab"
          className={styles.tabPanel}
          data-testid="authorization-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('authorization.title')}</h2>
          </div>

          {/* Route Authorization */}
          <div className={styles.authzSection}>
            <h3 className={styles.authzSectionTitle}>{t('authorization.routeAuthorization')}</h3>
            {collection.authz?.routePolicies && collection.authz.routePolicies.length > 0 ? (
              <div className={styles.tableContainer}>
                <table
                  className={styles.table}
                  aria-label={t('authorization.routeAuthorization')}
                  data-testid="route-policies-table"
                >
                  <thead>
                    <tr>
                      <th scope="col">Operation</th>
                      <th scope="col">Policy</th>
                    </tr>
                  </thead>
                  <tbody>
                    {collection.authz.routePolicies.map((policy, index) => (
                      <tr
                        key={`${policy.operation}-${policy.policyId}`}
                        data-testid={`route-policy-row-${index}`}
                      >
                        <td>
                          <span className={styles.operationBadge}>
                            {t(`authorization.operations.${policy.operation}`)}
                          </span>
                        </td>
                        <td>{policy.policyId}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className={styles.emptyState} data-testid="route-policies-empty">
                <p>{t('common.noData')}</p>
              </div>
            )}
          </div>

          {/* Field Authorization */}
          <div className={styles.authzSection}>
            <h3 className={styles.authzSectionTitle}>{t('authorization.fieldAuthorization')}</h3>
            {collection.authz?.fieldPolicies && collection.authz.fieldPolicies.length > 0 ? (
              <div className={styles.tableContainer}>
                <table
                  className={styles.table}
                  aria-label={t('authorization.fieldAuthorization')}
                  data-testid="field-policies-table"
                >
                  <thead>
                    <tr>
                      <th scope="col">{t('collections.fieldName')}</th>
                      <th scope="col">Operation</th>
                      <th scope="col">Policy</th>
                    </tr>
                  </thead>
                  <tbody>
                    {collection.authz.fieldPolicies.map((policy, index) => (
                      <tr
                        key={`${policy.fieldName}-${policy.operation}-${policy.policyId}`}
                        data-testid={`field-policy-row-${index}`}
                      >
                        <td>{policy.fieldName}</td>
                        <td>
                          <span className={styles.operationBadge}>
                            {policy.operation === 'read'
                              ? t('authorization.operations.read')
                              : 'Write'}
                          </span>
                        </td>
                        <td>{policy.policyId}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className={styles.emptyState} data-testid="field-policies-empty">
                <p>{t('common.noData')}</p>
              </div>
            )}
          </div>
        </section>
      )}

      {/* Version History Panel */}
      {activeTab === 'versions' && (
        <section
          id="versions-panel"
          role="tabpanel"
          aria-labelledby="versions-tab"
          className={styles.tabPanel}
          data-testid="versions-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('collections.versionHistory')}</h2>
          </div>
          {isLoadingVersions ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : versionsError ? (
            <ErrorMessage
              error={
                versionsError instanceof Error ? versionsError : new Error(t('errors.generic'))
              }
              variant="compact"
            />
          ) : versions.length === 0 ? (
            <div className={styles.emptyState} data-testid="versions-empty-state">
              <p>{t('common.noData')}</p>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table
                className={styles.table}
                aria-label={t('collections.versionHistory')}
                data-testid="versions-table"
              >
                <thead>
                  <tr>
                    <th scope="col">Version</th>
                    <th scope="col">Created</th>
                    <th scope="col">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {versions.map((version, index) => (
                    <tr
                      key={version.id}
                      className={`${styles.tableRow} ${
                        version.version === collection.currentVersion ? styles.currentVersion : ''
                      }`}
                      data-testid={`version-row-${index}`}
                    >
                      <td>
                        <span className={styles.versionNumber}>
                          v{version.version}
                          {version.version === collection.currentVersion && (
                            <span className={styles.currentBadge}>(Current)</span>
                          )}
                        </span>
                      </td>
                      <td>
                        {formatDate(new Date(version.createdAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                      <td className={styles.actionsCell}>
                        <button
                          type="button"
                          className={styles.actionButton}
                          onClick={() => handleViewVersion(version)}
                          aria-label={`View version ${version.version}`}
                          data-testid={`view-version-button-${index}`}
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
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

      {/* Field Editor Modal */}
      {fieldEditorOpen && (
        <div className={styles.modalOverlay} onClick={handleFieldCancel}>
          <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
            <FieldEditor
              collectionId={collectionId}
              field={editingField}
              collections={allCollections.map((c) => ({
                id: c.id,
                name: c.name,
                displayName: c.displayName || c.name,
              }))}
              onSave={handleFieldSave}
              onCancel={handleFieldCancel}
              isSubmitting={addFieldMutation.isPending || updateFieldMutation.isPending}
            />
          </div>
        </div>
      )}
    </div>
  )
}

export default CollectionDetailPage
