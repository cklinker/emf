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
import type {
  Collection,
  FieldDefinition,
  CollectionVersion,
  CollectionValidationRule,
  RecordType,
  SetupAuditTrailEntry,
} from '../../types/collections'
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
  return t(`fields.types.${type.toLowerCase()}`)
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
  const [activeTab, setActiveTab] = useState<
    | 'fields'
    | 'authorization'
    | 'validationRules'
    | 'recordTypes'
    | 'fieldHistory'
    | 'setupAudit'
    | 'versions'
  >('fields')

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

  // Fetch validation rules
  const { data: validationRules = [], isLoading: isLoadingRules } = useQuery({
    queryKey: ['validation-rules', collectionId],
    queryFn: async () => {
      const response = await apiClient.get<CollectionValidationRule[]>(
        `/control/collections/${collectionId}/validation-rules`
      )
      return response
    },
    enabled: !!collectionId && activeTab === 'validationRules',
  })

  // Fetch record types
  const { data: recordTypes = [], isLoading: isLoadingRecordTypes } = useQuery({
    queryKey: ['record-types', collectionId],
    queryFn: async () => {
      const response = await apiClient.get<RecordType[]>(
        `/control/collections/${collectionId}/record-types`
      )
      return response
    },
    enabled: !!collectionId && activeTab === 'recordTypes',
  })

  // Fetch setup audit trail
  const { data: setupAuditPage, isLoading: isLoadingAudit } = useQuery({
    queryKey: ['setup-audit', collectionId],
    queryFn: async () => {
      const response = await apiClient.get<{
        content: SetupAuditTrailEntry[]
        totalElements: number
      }>('/control/audit?size=50')
      return response
    },
    enabled: activeTab === 'setupAudit',
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
  const handleTabChange = useCallback(
    (tab: 'fields' | 'authorization' | 'validationRules' | 'recordTypes' | 'versions') => {
      setActiveTab(tab)
    },
    []
  )

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
          className={`${styles.tab} ${activeTab === 'validationRules' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('validationRules')}
          aria-selected={activeTab === 'validationRules'}
          aria-controls="validation-rules-panel"
          id="validation-rules-tab"
          data-testid="validation-rules-tab"
        >
          {t('collections.validationRules')}
        </button>
        <button
          type="button"
          role="tab"
          className={`${styles.tab} ${activeTab === 'recordTypes' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('recordTypes')}
          aria-selected={activeTab === 'recordTypes'}
          aria-controls="record-types-panel"
          id="record-types-tab"
          data-testid="record-types-tab"
        >
          {t('collections.recordTypes')}
        </button>
        <button
          type="button"
          role="tab"
          className={`${styles.tab} ${activeTab === 'fieldHistory' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('fieldHistory')}
          aria-selected={activeTab === 'fieldHistory'}
          aria-controls="field-history-panel"
          id="field-history-tab"
          data-testid="field-history-tab"
        >
          {t('collections.fieldHistory')}
        </button>
        <button
          type="button"
          role="tab"
          className={`${styles.tab} ${activeTab === 'setupAudit' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('setupAudit')}
          aria-selected={activeTab === 'setupAudit'}
          aria-controls="setup-audit-panel"
          id="setup-audit-tab"
          data-testid="setup-audit-tab"
        >
          {t('collections.setupAudit')}
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

      {/* Validation Rules Panel */}
      {activeTab === 'validationRules' && (
        <section
          id="validation-rules-panel"
          role="tabpanel"
          aria-labelledby="validation-rules-tab"
          className={styles.tabPanel}
          data-testid="validation-rules-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('collections.validationRules')}</h2>
          </div>
          {isLoadingRules ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : validationRules.length === 0 ? (
            <div className={styles.emptyState} data-testid="validation-rules-empty">
              <p>{t('common.noData')}</p>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table
                className={styles.table}
                aria-label={t('collections.validationRules')}
                data-testid="validation-rules-table"
              >
                <thead>
                  <tr>
                    <th scope="col">{t('common.name')}</th>
                    <th scope="col">{t('validationRules.formula')}</th>
                    <th scope="col">{t('validationRules.errorMessage')}</th>
                    <th scope="col">{t('validationRules.evaluateOn')}</th>
                    <th scope="col">{t('collections.status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {validationRules.map((rule, index) => (
                    <tr key={rule.id} data-testid={`validation-rule-row-${index}`}>
                      <td className={styles.fieldNameCell}>{rule.name}</td>
                      <td>
                        <code className={styles.formulaCode}>{rule.errorConditionFormula}</code>
                      </td>
                      <td>{rule.errorMessage}</td>
                      <td>
                        <span className={styles.evaluateOnBadge}>{rule.evaluateOn}</span>
                      </td>
                      <td>
                        <span
                          className={`${styles.statusBadge} ${rule.active ? styles.statusActive : styles.statusInactive}`}
                        >
                          {rule.active ? t('collections.active') : t('collections.inactive')}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Record Types Panel */}
      {activeTab === 'recordTypes' && (
        <section
          id="record-types-panel"
          role="tabpanel"
          aria-labelledby="record-types-tab"
          className={styles.tabPanel}
          data-testid="record-types-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('collections.recordTypes')}</h2>
          </div>
          {isLoadingRecordTypes ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : recordTypes.length === 0 ? (
            <div className={styles.emptyState} data-testid="record-types-empty">
              <p>{t('common.noData')}</p>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table
                className={styles.table}
                aria-label={t('collections.recordTypes')}
                data-testid="record-types-table"
              >
                <thead>
                  <tr>
                    <th scope="col">{t('common.name')}</th>
                    <th scope="col">{t('collections.description')}</th>
                    <th scope="col">{t('recordTypes.default')}</th>
                    <th scope="col">{t('collections.status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {recordTypes.map((rt, index) => (
                    <tr key={rt.id} data-testid={`record-type-row-${index}`}>
                      <td className={styles.fieldNameCell}>{rt.name}</td>
                      <td>{rt.description || '-'}</td>
                      <td>
                        {rt.isDefault && (
                          <span className={styles.defaultBadge}>{t('recordTypes.default')}</span>
                        )}
                      </td>
                      <td>
                        <span
                          className={`${styles.statusBadge} ${rt.active ? styles.statusActive : styles.statusInactive}`}
                        >
                          {rt.active ? t('collections.active') : t('collections.inactive')}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Field History Panel */}
      {activeTab === 'fieldHistory' && (
        <section
          id="field-history-panel"
          role="tabpanel"
          aria-labelledby="field-history-tab"
          className={styles.tabPanel}
          data-testid="field-history-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('collections.fieldHistory')}</h2>
          </div>
          <div className={styles.infoMessage}>
            <p>{t('fieldHistory.description')}</p>
            <p className={styles.trackedFieldsNote}>{t('fieldHistory.trackedFieldsNote')}</p>
            {sortedFields.length > 0 && (
              <div className={styles.trackedFieldsList}>
                <h3>{t('fieldHistory.trackedFields')}</h3>
                <ul>
                  {sortedFields
                    .filter((f: FieldDefinition) => f.trackHistory)
                    .map((f: FieldDefinition) => (
                      <li key={f.id}>{f.name}</li>
                    ))}
                  {sortedFields.filter((f: FieldDefinition) => f.trackHistory).length === 0 && (
                    <li className={styles.emptyNote}>{t('fieldHistory.noTrackedFields')}</li>
                  )}
                </ul>
              </div>
            )}
          </div>
        </section>
      )}

      {/* Setup Audit Panel */}
      {activeTab === 'setupAudit' && (
        <section
          id="setup-audit-panel"
          role="tabpanel"
          aria-labelledby="setup-audit-tab"
          className={styles.tabPanel}
          data-testid="setup-audit-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('collections.setupAudit')}</h2>
          </div>
          {isLoadingAudit ? (
            <LoadingSpinner />
          ) : setupAuditPage?.content && setupAuditPage.content.length > 0 ? (
            <div className={styles.tableContainer}>
              <table className={styles.table} aria-label={t('collections.setupAudit')}>
                <thead>
                  <tr>
                    <th>{t('setupAudit.action')}</th>
                    <th>{t('setupAudit.section')}</th>
                    <th>{t('setupAudit.entityType')}</th>
                    <th>{t('setupAudit.entityName')}</th>
                    <th>{t('setupAudit.performedAt')}</th>
                  </tr>
                </thead>
                <tbody>
                  {setupAuditPage.content.map((entry: SetupAuditTrailEntry) => (
                    <tr key={entry.id}>
                      <td>
                        <span className={styles.actionBadge} data-action={entry.action}>
                          {entry.action}
                        </span>
                      </td>
                      <td>{entry.section}</td>
                      <td>{entry.entityType}</td>
                      <td>{entry.entityName || '-'}</td>
                      <td>{new Date(entry.timestamp).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className={styles.emptyState}>
              <p>{t('setupAudit.empty')}</p>
            </div>
          )}
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
