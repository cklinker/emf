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
import { getTenantSlug } from '../../context/TenantContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import {
  FieldEditor,
  type FieldDefinition as FieldEditorDefinition,
} from '../../components/FieldEditor'
import { ValidationRuleEditor } from '../../components/ValidationRuleEditor'
import { RecordTypeEditor } from '../../components/RecordTypeEditor'
import { PicklistDependencyEditor } from '../../components/PicklistDependencyEditor'
import { AuthorizationPanel } from '../../components/AuthorizationPanel'
import type { RoutePolicyConfig, FieldPolicyConfig } from '../../components/AuthorizationPanel'
import type {
  Collection,
  FieldDefinition,
  CollectionVersion,
  CollectionValidationRule,
  RecordType,
  PicklistDependency,
  SetupAuditTrailEntry,
  PolicySummary,
  FieldHistoryEntry,
} from '../../types/collections'
import type { FieldType } from '../../types/collections'
import styles from './CollectionDetailPage.module.css'

/**
 * Reverse mapping from backend canonical types (uppercase) to UI types (lowercase).
 * The backend stores "DOUBLE" for what the UI calls "number", and "JSON" for "object".
 * Most types simply lowercase (e.g., "STRING" → "string", "PICKLIST" → "picklist").
 */
const BACKEND_TYPE_TO_UI: Record<string, FieldType> = {
  DOUBLE: 'number',
  INTEGER: 'number',
  LONG: 'number',
  JSON: 'json',
  ARRAY: 'json',
}

function normalizeFieldType(backendType: string): FieldType {
  // Check explicit mapping first, then just lowercase
  const upper = backendType.toUpperCase()
  if (upper in BACKEND_TYPE_TO_UI) {
    return BACKEND_TYPE_TO_UI[upper]
  }
  return backendType.toLowerCase() as FieldType
}

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

  // Validation rule editor state
  const [validationRuleEditorOpen, setValidationRuleEditorOpen] = useState(false)
  const [editingValidationRule, setEditingValidationRule] = useState<
    CollectionValidationRule | undefined
  >(undefined)
  const [deleteValidationRuleDialogOpen, setDeleteValidationRuleDialogOpen] = useState(false)
  const [validationRuleToDelete, setValidationRuleToDelete] = useState<
    CollectionValidationRule | undefined
  >(undefined)

  // Record type editor state
  const [recordTypeEditorOpen, setRecordTypeEditorOpen] = useState(false)
  const [editingRecordType, setEditingRecordType] = useState<RecordType | undefined>(undefined)
  const [deleteRecordTypeDialogOpen, setDeleteRecordTypeDialogOpen] = useState(false)
  const [recordTypeToDelete, setRecordTypeToDelete] = useState<RecordType | undefined>(undefined)

  // Picklist dependency editor state
  const [dependencyEditorOpen, setDependencyEditorOpen] = useState(false)
  const [editingDependency, setEditingDependency] = useState<PicklistDependency | undefined>(
    undefined
  )
  const [deleteDependencyDialogOpen, setDeleteDependencyDialogOpen] = useState(false)
  const [dependencyToDelete, setDependencyToDelete] = useState<PicklistDependency | undefined>(
    undefined
  )

  // Active tab state for sections
  const [activeTab, setActiveTab] = useState<
    | 'fields'
    | 'authorization'
    | 'validationRules'
    | 'recordTypes'
    | 'picklistDependencies'
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
      // Normalize field types from backend canonical form to UI form.
      // The backend stores types as uppercase enums (e.g., "STRING", "DOUBLE", "PICKLIST")
      // but the UI uses lowercase aliases (e.g., "string", "number", "picklist").
      if (response.fields) {
        response.fields = response.fields.map((f) => ({
          ...f,
          type: normalizeFieldType(f.type),
        }))
      }
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

  // Fetch picklist dependencies (one query per picklist field, combined)
  const picklistFieldIds = useMemo(() => {
    if (!collection?.fields) return []
    return collection.fields
      .filter((f) => f.type === 'picklist' || f.type === 'multi_picklist')
      .map((f) => f.id)
  }, [collection])

  const { data: allDependencies = [], isLoading: isLoadingDependencies } = useQuery({
    queryKey: ['picklist-dependencies', collectionId, picklistFieldIds],
    queryFn: async () => {
      if (picklistFieldIds.length === 0) return []
      const results = await Promise.all(
        picklistFieldIds.map((fieldId) =>
          apiClient
            .get<PicklistDependency[]>(`/control/picklists/fields/${fieldId}/dependencies`)
            .catch(() => [] as PicklistDependency[])
        )
      )
      // Deduplicate by id
      const seen = new Set<string>()
      const deduped: PicklistDependency[] = []
      for (const deps of results) {
        for (const dep of deps) {
          if (!seen.has(dep.id)) {
            seen.add(dep.id)
            deduped.push(dep)
          }
        }
      }
      return deduped
    },
    enabled: !!collectionId && activeTab === 'picklistDependencies' && picklistFieldIds.length > 0,
  })

  // Fetch available policies for authorization panel
  const { data: policies = [], isLoading: isLoadingPolicies } = useQuery({
    queryKey: ['policies'],
    queryFn: async () => {
      const response = await apiClient.get<PolicySummary[]>('/control/policies')
      return response
    },
    enabled: !!collectionId && activeTab === 'authorization',
  })

  // Fetch setup audit trail (filtered to this collection)
  const { data: setupAuditPage, isLoading: isLoadingAudit } = useQuery({
    queryKey: ['setup-audit', collectionId],
    queryFn: async () => {
      const response = await apiClient.get<{
        content: SetupAuditTrailEntry[]
        totalElements: number
      }>(`/control/audit/entity/Collection/${collectionId}?size=50`)
      return response
    },
    enabled: activeTab === 'setupAudit' && !!collectionId,
  })

  // Fetch field history for this collection (recent changes across all fields)
  const { data: fieldHistoryPage, isLoading: isLoadingFieldHistory } = useQuery({
    queryKey: ['field-history', collectionId],
    queryFn: async () => {
      // Fetch history for each tracked field
      const trackedFields = collection?.fields?.filter((f) => f.trackHistory) ?? []
      if (trackedFields.length === 0) return { content: [] as FieldHistoryEntry[] }
      const results = await Promise.all(
        trackedFields.map((field) =>
          apiClient
            .get<{
              content: FieldHistoryEntry[]
            }>(`/control/collections/${collectionId}/field-history/${field.name}?size=20`)
            .catch(() => ({ content: [] as FieldHistoryEntry[] }))
        )
      )
      // Merge and sort by changedAt descending
      const allEntries = results.flatMap((r) => r.content || [])
      allEntries.sort((a, b) => new Date(b.changedAt).getTime() - new Date(a.changedAt).getTime())
      return { content: allEntries.slice(0, 50) }
    },
    enabled: activeTab === 'fieldHistory' && !!collectionId,
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

  // Fetch global picklists for picklist field dropdown
  const { data: globalPicklists = [] } = useQuery({
    queryKey: ['global-picklists'],
    queryFn: async () => {
      const response = await apiClient.get<Array<{ id: string; name: string }>>(
        '/control/picklists/global?tenantId=default'
      )
      return response
    },
    enabled: fieldEditorOpen,
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete(`/control/collections/${collectionId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collections'] })
      showToast(t('success.deleted', { item: t('collections.title') }), 'success')
      navigate(`/${getTenantSlug()}/collections`)
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

  // --- Validation Rule mutations ---
  const createValidationRuleMutation = useMutation({
    mutationFn: async (data: Record<string, unknown>) => {
      await apiClient.post(`/control/collections/${collectionId}/validation-rules`, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['validation-rules', collectionId] })
      showToast(t('success.created', { item: t('collections.validationRules') }), 'success')
      setValidationRuleEditorOpen(false)
      setEditingValidationRule(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const updateValidationRuleMutation = useMutation({
    mutationFn: async ({ ruleId, data }: { ruleId: string; data: Record<string, unknown> }) => {
      await apiClient.put(`/control/collections/${collectionId}/validation-rules/${ruleId}`, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['validation-rules', collectionId] })
      showToast(t('success.updated', { item: t('collections.validationRules') }), 'success')
      setValidationRuleEditorOpen(false)
      setEditingValidationRule(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const deleteValidationRuleMutation = useMutation({
    mutationFn: async (ruleId: string) => {
      await apiClient.delete(`/control/collections/${collectionId}/validation-rules/${ruleId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['validation-rules', collectionId] })
      showToast(t('success.deleted', { item: t('collections.validationRules') }), 'success')
      setDeleteValidationRuleDialogOpen(false)
      setValidationRuleToDelete(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const activateValidationRuleMutation = useMutation({
    mutationFn: async (ruleId: string) => {
      await apiClient.post(
        `/control/collections/${collectionId}/validation-rules/${ruleId}/activate`,
        {}
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['validation-rules', collectionId] })
      showToast(t('success.activated'), 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const deactivateValidationRuleMutation = useMutation({
    mutationFn: async (ruleId: string) => {
      await apiClient.post(
        `/control/collections/${collectionId}/validation-rules/${ruleId}/deactivate`,
        {}
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['validation-rules', collectionId] })
      showToast(t('success.deactivated'), 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // --- Record Type mutations ---
  const createRecordTypeMutation = useMutation({
    mutationFn: async (data: Record<string, unknown>) => {
      await apiClient.post(`/control/collections/${collectionId}/record-types`, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['record-types', collectionId] })
      showToast(t('success.created', { item: t('collections.recordTypes') }), 'success')
      setRecordTypeEditorOpen(false)
      setEditingRecordType(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const updateRecordTypeMutation = useMutation({
    mutationFn: async ({ rtId, data }: { rtId: string; data: Record<string, unknown> }) => {
      await apiClient.put(`/control/collections/${collectionId}/record-types/${rtId}`, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['record-types', collectionId] })
      showToast(t('success.updated', { item: t('collections.recordTypes') }), 'success')
      setRecordTypeEditorOpen(false)
      setEditingRecordType(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const deleteRecordTypeMutation = useMutation({
    mutationFn: async (rtId: string) => {
      await apiClient.delete(`/control/collections/${collectionId}/record-types/${rtId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['record-types', collectionId] })
      showToast(t('success.deleted', { item: t('collections.recordTypes') }), 'success')
      setDeleteRecordTypeDialogOpen(false)
      setRecordTypeToDelete(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // --- Picklist Dependency mutations ---
  const saveDependencyMutation = useMutation({
    mutationFn: async (data: {
      controllingFieldId: string
      dependentFieldId: string
      mapping: Record<string, string[]>
    }) => {
      await apiClient.put('/control/picklists/dependencies', data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['picklist-dependencies', collectionId],
      })
      showToast(t('picklistDependencies.saved'), 'success')
      setDependencyEditorOpen(false)
      setEditingDependency(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  const deleteDependencyMutation = useMutation({
    mutationFn: async (dep: PicklistDependency) => {
      await apiClient.delete(
        `/control/picklists/dependencies/${dep.controllingFieldId}/${dep.dependentFieldId}`
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['picklist-dependencies', collectionId],
      })
      showToast(t('picklistDependencies.deleted'), 'success')
      setDeleteDependencyDialogOpen(false)
      setDependencyToDelete(undefined)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // --- Authorization mutation ---
  const updateAuthzMutation = useMutation({
    mutationFn: async (data: {
      routePolicies: Array<{ operation: string; policyId: string }>
      fieldPolicies: Array<{ fieldId: string; operation: string; policyId: string }>
    }) => {
      await apiClient.put(`/control/collections/${collectionId}/authz`, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collection', collectionId] })
      showToast(t('success.updated', { item: t('authorization.title') }), 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Handle route authorization change
  const handleRouteAuthzChange = useCallback(
    (routePolicies: RoutePolicyConfig[]) => {
      // Map field policies to backend format (fieldName → fieldId)
      const currentFieldPolicies = (collection?.authz?.fieldPolicies ?? [])
        .filter((p) => p.policyId)
        .map((p) => {
          const field = collection?.fields?.find((f) => f.name === p.fieldName)
          return { fieldId: field?.id ?? '', operation: p.operation, policyId: p.policyId }
        })
        .filter((p) => p.fieldId)
      updateAuthzMutation.mutate({
        routePolicies: routePolicies
          .filter((p) => p.policyId)
          .map((p) => ({ operation: p.operation, policyId: p.policyId! })),
        fieldPolicies: currentFieldPolicies,
      })
    },
    [collection, updateAuthzMutation]
  )

  // Handle field authorization change
  const handleFieldAuthzChange = useCallback(
    (fieldPolicies: FieldPolicyConfig[]) => {
      // Map field policies to backend format (fieldName → fieldId)
      const mappedFieldPolicies = fieldPolicies
        .filter((p) => p.policyId)
        .map((p) => {
          const field = collection?.fields?.find((f) => f.name === p.fieldName)
          return { fieldId: field?.id ?? '', operation: p.operation, policyId: p.policyId! }
        })
        .filter((p) => p.fieldId)
      const currentRoutePolicies = (collection?.authz?.routePolicies ?? [])
        .filter((p) => p.policyId)
        .map((p) => ({ operation: p.operation, policyId: p.policyId }))
      updateAuthzMutation.mutate({
        routePolicies: currentRoutePolicies,
        fieldPolicies: mappedFieldPolicies,
      })
    },
    [collection, updateAuthzMutation]
  )

  // Handle edit action
  const handleEdit = useCallback(() => {
    navigate(`/${getTenantSlug()}/collections/${collectionId}/edit`)
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
    navigate(`/${getTenantSlug()}/collections`)
  }, [navigate])

  // Handle tab change
  const handleTabChange = useCallback(
    (
      tab:
        | 'fields'
        | 'authorization'
        | 'validationRules'
        | 'recordTypes'
        | 'picklistDependencies'
        | 'fieldHistory'
        | 'setupAudit'
        | 'versions'
    ) => {
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
            fieldTypeConfig: fieldData.fieldTypeConfig,
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
          fieldTypeConfig: fieldData.fieldTypeConfig,
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
      navigate(`/${getTenantSlug()}/collections/${collectionId}/versions/${version.version}`)
    },
    [navigate, collectionId]
  )

  // --- Validation Rule handlers ---
  const handleAddValidationRule = useCallback(() => {
    setEditingValidationRule(undefined)
    setValidationRuleEditorOpen(true)
  }, [])

  const handleEditValidationRule = useCallback((rule: CollectionValidationRule) => {
    setEditingValidationRule(rule)
    setValidationRuleEditorOpen(true)
  }, [])

  const handleValidationRuleSave = useCallback(
    async (data: Record<string, unknown>) => {
      if (editingValidationRule) {
        await updateValidationRuleMutation.mutateAsync({
          ruleId: editingValidationRule.id,
          data,
        })
      } else {
        await createValidationRuleMutation.mutateAsync(data)
      }
    },
    [editingValidationRule, createValidationRuleMutation, updateValidationRuleMutation]
  )

  const handleValidationRuleCancel = useCallback(() => {
    setValidationRuleEditorOpen(false)
    setEditingValidationRule(undefined)
  }, [])

  const handleDeleteValidationRuleClick = useCallback((rule: CollectionValidationRule) => {
    setValidationRuleToDelete(rule)
    setDeleteValidationRuleDialogOpen(true)
  }, [])

  const handleDeleteValidationRuleConfirm = useCallback(() => {
    if (validationRuleToDelete) {
      deleteValidationRuleMutation.mutate(validationRuleToDelete.id)
    }
  }, [validationRuleToDelete, deleteValidationRuleMutation])

  const handleDeleteValidationRuleCancel = useCallback(() => {
    setDeleteValidationRuleDialogOpen(false)
    setValidationRuleToDelete(undefined)
  }, [])

  const handleActivateValidationRule = useCallback(
    (ruleId: string) => {
      activateValidationRuleMutation.mutate(ruleId)
    },
    [activateValidationRuleMutation]
  )

  const handleDeactivateValidationRule = useCallback(
    (ruleId: string) => {
      deactivateValidationRuleMutation.mutate(ruleId)
    },
    [deactivateValidationRuleMutation]
  )

  // --- Record Type handlers ---
  const handleAddRecordType = useCallback(() => {
    setEditingRecordType(undefined)
    setRecordTypeEditorOpen(true)
  }, [])

  const handleEditRecordType = useCallback((rt: RecordType) => {
    setEditingRecordType(rt)
    setRecordTypeEditorOpen(true)
  }, [])

  const handleRecordTypeSave = useCallback(
    async (data: Record<string, unknown>) => {
      if (editingRecordType) {
        await updateRecordTypeMutation.mutateAsync({ rtId: editingRecordType.id, data })
      } else {
        await createRecordTypeMutation.mutateAsync(data)
      }
    },
    [editingRecordType, createRecordTypeMutation, updateRecordTypeMutation]
  )

  const handleRecordTypeCancel = useCallback(() => {
    setRecordTypeEditorOpen(false)
    setEditingRecordType(undefined)
  }, [])

  const handleDeleteRecordTypeClick = useCallback((rt: RecordType) => {
    setRecordTypeToDelete(rt)
    setDeleteRecordTypeDialogOpen(true)
  }, [])

  const handleDeleteRecordTypeConfirm = useCallback(() => {
    if (recordTypeToDelete) {
      deleteRecordTypeMutation.mutate(recordTypeToDelete.id)
    }
  }, [recordTypeToDelete, deleteRecordTypeMutation])

  const handleDeleteRecordTypeCancel = useCallback(() => {
    setDeleteRecordTypeDialogOpen(false)
    setRecordTypeToDelete(undefined)
  }, [])

  // --- Picklist Dependency handlers ---
  const handleAddDependency = useCallback(() => {
    setEditingDependency(undefined)
    setDependencyEditorOpen(true)
  }, [])

  const handleEditDependency = useCallback((dep: PicklistDependency) => {
    setEditingDependency(dep)
    setDependencyEditorOpen(true)
  }, [])

  const handleDependencySave = useCallback(
    async (data: {
      controllingFieldId: string
      dependentFieldId: string
      mapping: Record<string, string[]>
    }) => {
      await saveDependencyMutation.mutateAsync(data)
    },
    [saveDependencyMutation]
  )

  const handleDependencyCancel = useCallback(() => {
    setDependencyEditorOpen(false)
    setEditingDependency(undefined)
  }, [])

  const handleDeleteDependencyClick = useCallback((dep: PicklistDependency) => {
    setDependencyToDelete(dep)
    setDeleteDependencyDialogOpen(true)
  }, [])

  const handleDeleteDependencyConfirm = useCallback(() => {
    if (dependencyToDelete) {
      deleteDependencyMutation.mutate(dependencyToDelete)
    }
  }, [dependencyToDelete, deleteDependencyMutation])

  const handleDeleteDependencyCancel = useCallback(() => {
    setDeleteDependencyDialogOpen(false)
    setDependencyToDelete(undefined)
  }, [])

  // Helper to resolve field name from ID
  const getFieldName = useCallback(
    (fieldIdOrName: string): string => {
      const field =
        collection?.fields?.find((f) => f.id === fieldIdOrName) ||
        collection?.fields?.find((f) => f.name === fieldIdOrName)
      return field?.displayName || field?.name || fieldIdOrName
    },
    [collection]
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
          className={`${styles.tab} ${activeTab === 'picklistDependencies' ? styles.tabActive : ''}`}
          onClick={() => handleTabChange('picklistDependencies')}
          aria-selected={activeTab === 'picklistDependencies'}
          aria-controls="picklist-dependencies-panel"
          id="picklist-dependencies-tab"
          data-testid="picklist-dependencies-tab"
        >
          {t('picklistDependencies.title')}
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
          <AuthorizationPanel
            collectionId={collectionId}
            collectionName={collection.name}
            fields={(collection.fields ?? []).map((f) => ({
              id: f.id,
              name: f.name,
              displayName: f.displayName,
            }))}
            policies={policies}
            authz={collection.authz}
            onRouteAuthzChange={handleRouteAuthzChange}
            onFieldAuthzChange={handleFieldAuthzChange}
            isLoading={isLoadingPolicies}
            isSaving={updateAuthzMutation.isPending}
          />
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
            <button
              type="button"
              className={styles.addButton}
              onClick={handleAddValidationRule}
              aria-label={t('validationRules.addRule')}
              data-testid="add-validation-rule-button"
            >
              {t('validationRules.addRule')}
            </button>
          </div>
          {isLoadingRules ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : validationRules.length === 0 ? (
            <div className={styles.emptyState} data-testid="validation-rules-empty">
              <p>{t('common.noData')}</p>
              <button type="button" className={styles.addButton} onClick={handleAddValidationRule}>
                {t('validationRules.addRule')}
              </button>
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
                    <th scope="col">{t('common.actions')}</th>
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
                      <td className={styles.actionsCell}>
                        <div className={styles.actionButtons}>
                          <button
                            type="button"
                            className={styles.actionButton}
                            onClick={() => handleEditValidationRule(rule)}
                            aria-label={`${t('common.edit')} ${rule.name}`}
                            data-testid={`edit-validation-rule-${index}`}
                          >
                            {t('common.edit')}
                          </button>
                          {rule.active ? (
                            <button
                              type="button"
                              className={styles.actionButton}
                              onClick={() => handleDeactivateValidationRule(rule.id)}
                              aria-label={`${t('common.deactivate')} ${rule.name}`}
                              data-testid={`deactivate-validation-rule-${index}`}
                            >
                              {t('common.deactivate')}
                            </button>
                          ) : (
                            <button
                              type="button"
                              className={styles.actionButton}
                              onClick={() => handleActivateValidationRule(rule.id)}
                              aria-label={`${t('common.activate')} ${rule.name}`}
                              data-testid={`activate-validation-rule-${index}`}
                            >
                              {t('common.activate')}
                            </button>
                          )}
                          <button
                            type="button"
                            className={`${styles.actionButton} ${styles.actionButtonDanger}`}
                            onClick={() => handleDeleteValidationRuleClick(rule)}
                            aria-label={`${t('common.delete')} ${rule.name}`}
                            data-testid={`delete-validation-rule-${index}`}
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
            <button
              type="button"
              className={styles.addButton}
              onClick={handleAddRecordType}
              aria-label={t('recordTypes.addRecordType')}
              data-testid="add-record-type-button"
            >
              {t('recordTypes.addRecordType')}
            </button>
          </div>
          {isLoadingRecordTypes ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : recordTypes.length === 0 ? (
            <div className={styles.emptyState} data-testid="record-types-empty">
              <p>{t('common.noData')}</p>
              <button type="button" className={styles.addButton} onClick={handleAddRecordType}>
                {t('recordTypes.addRecordType')}
              </button>
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
                    <th scope="col">{t('common.actions')}</th>
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
                      <td className={styles.actionsCell}>
                        <div className={styles.actionButtons}>
                          <button
                            type="button"
                            className={styles.actionButton}
                            onClick={() => handleEditRecordType(rt)}
                            aria-label={`${t('common.edit')} ${rt.name}`}
                            data-testid={`edit-record-type-${index}`}
                          >
                            {t('common.edit')}
                          </button>
                          <button
                            type="button"
                            className={`${styles.actionButton} ${styles.actionButtonDanger}`}
                            onClick={() => handleDeleteRecordTypeClick(rt)}
                            aria-label={`${t('common.delete')} ${rt.name}`}
                            data-testid={`delete-record-type-${index}`}
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
          )}
        </section>
      )}

      {/* Picklist Dependencies Panel */}
      {activeTab === 'picklistDependencies' && (
        <section
          id="picklist-dependencies-panel"
          role="tabpanel"
          aria-labelledby="picklist-dependencies-tab"
          className={styles.tabPanel}
          data-testid="picklist-dependencies-panel"
        >
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>{t('picklistDependencies.title')}</h2>
            {picklistFieldIds.length >= 2 && (
              <button
                type="button"
                className={styles.addButton}
                onClick={handleAddDependency}
                aria-label={t('picklistDependencies.addDependency')}
                data-testid="add-dependency-button"
              >
                {t('picklistDependencies.addDependency')}
              </button>
            )}
          </div>
          {picklistFieldIds.length < 2 ? (
            <div className={styles.emptyState} data-testid="dependencies-no-fields">
              <p>{t('picklistDependencies.noPicklistFields')}</p>
            </div>
          ) : isLoadingDependencies ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : allDependencies.length === 0 ? (
            <div className={styles.emptyState} data-testid="dependencies-empty">
              <p>{t('picklistDependencies.noDependencies')}</p>
              <button type="button" className={styles.addButton} onClick={handleAddDependency}>
                {t('picklistDependencies.addDependency')}
              </button>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table
                className={styles.table}
                aria-label={t('picklistDependencies.title')}
                data-testid="dependencies-table"
              >
                <thead>
                  <tr>
                    <th scope="col">{t('picklistDependencies.controllingField')}</th>
                    <th scope="col">{t('picklistDependencies.dependentField')}</th>
                    <th scope="col">{t('picklistDependencies.mapping')}</th>
                    <th scope="col">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {allDependencies.map((dep, index) => (
                    <tr key={dep.id} data-testid={`dependency-row-${index}`}>
                      <td className={styles.fieldNameCell}>
                        {getFieldName(dep.controllingFieldId)}
                      </td>
                      <td className={styles.fieldNameCell}>{getFieldName(dep.dependentFieldId)}</td>
                      <td>
                        {Object.keys(dep.mapping).length} mapping
                        {Object.keys(dep.mapping).length !== 1 ? 's' : ''}
                      </td>
                      <td className={styles.actionsCell}>
                        <div className={styles.actionButtons}>
                          <button
                            type="button"
                            className={styles.actionButton}
                            onClick={() => handleEditDependency(dep)}
                            aria-label={`${t('common.edit')} dependency`}
                            data-testid={`edit-dependency-${index}`}
                          >
                            {t('common.edit')}
                          </button>
                          <button
                            type="button"
                            className={`${styles.actionButton} ${styles.actionButtonDanger}`}
                            onClick={() => handleDeleteDependencyClick(dep)}
                            aria-label={`${t('common.delete')} dependency`}
                            data-testid={`delete-dependency-${index}`}
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
                      <li key={f.id}>{f.displayName || f.name}</li>
                    ))}
                  {sortedFields.filter((f: FieldDefinition) => f.trackHistory).length === 0 && (
                    <li className={styles.emptyNote}>{t('fieldHistory.noTrackedFields')}</li>
                  )}
                </ul>
              </div>
            )}
          </div>

          {/* Recent Field Changes */}
          {isLoadingFieldHistory ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : fieldHistoryPage?.content && fieldHistoryPage.content.length > 0 ? (
            <>
              <h3 className={styles.panelSubtitle}>{t('fieldHistory.recentChanges')}</h3>
              <div className={styles.tableContainer}>
                <table
                  className={styles.table}
                  aria-label={t('fieldHistory.recentChanges')}
                  data-testid="field-history-table"
                >
                  <thead>
                    <tr>
                      <th scope="col">{t('collections.fieldName')}</th>
                      <th scope="col">{t('fieldHistory.recordId')}</th>
                      <th scope="col">{t('fieldHistory.oldValue')}</th>
                      <th scope="col">{t('fieldHistory.newValue')}</th>
                      <th scope="col">{t('fieldHistory.changedBy')}</th>
                      <th scope="col">{t('fieldHistory.changedAt')}</th>
                      <th scope="col">{t('fieldHistory.source')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {fieldHistoryPage.content.map((entry: FieldHistoryEntry) => (
                      <tr key={entry.id}>
                        <td className={styles.fieldNameCell}>
                          {getFieldName(entry.fieldName) || entry.fieldName}
                        </td>
                        <td>
                          <code className={styles.recordIdCode}>
                            {entry.recordId.substring(0, 8)}...
                          </code>
                        </td>
                        <td>{entry.oldValue != null ? String(entry.oldValue) : '-'}</td>
                        <td>{entry.newValue != null ? String(entry.newValue) : '-'}</td>
                        <td>{entry.changedBy || '-'}</td>
                        <td>{new Date(entry.changedAt).toLocaleString()}</td>
                        <td>
                          <span className={styles.sourceTag}>{entry.changeSource}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : (
            sortedFields.some((f: FieldDefinition) => f.trackHistory) && (
              <div className={styles.emptyState} data-testid="field-history-empty">
                <p>{t('fieldHistory.noChanges')}</p>
              </div>
            )
          )}
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

      {/* Validation Rule Delete Confirmation */}
      <ConfirmDialog
        open={deleteValidationRuleDialogOpen}
        title={t('validationRules.deleteRule')}
        message={t('validationRules.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteValidationRuleConfirm}
        onCancel={handleDeleteValidationRuleCancel}
        variant="danger"
      />

      {/* Record Type Delete Confirmation */}
      <ConfirmDialog
        open={deleteRecordTypeDialogOpen}
        title={t('recordTypes.deleteRecordType')}
        message={t('recordTypes.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteRecordTypeConfirm}
        onCancel={handleDeleteRecordTypeCancel}
        variant="danger"
      />

      {/* Field Editor Modal */}
      {fieldEditorOpen && (
        <div className={styles.modalOverlay} role="presentation" onMouseDown={handleFieldCancel}>
          {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
          <div
            className={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <FieldEditor
              collectionId={collectionId}
              field={editingField}
              collections={allCollections.map((c) => ({
                id: c.id,
                name: c.name,
                displayName: c.displayName || c.name,
              }))}
              picklists={globalPicklists.map((p) => ({
                id: p.id,
                name: p.name,
              }))}
              onSave={handleFieldSave}
              onCancel={handleFieldCancel}
              isSubmitting={addFieldMutation.isPending || updateFieldMutation.isPending}
            />
          </div>
        </div>
      )}

      {/* Validation Rule Editor Modal */}
      {validationRuleEditorOpen && (
        <div
          className={styles.modalOverlay}
          role="presentation"
          onMouseDown={handleValidationRuleCancel}
        >
          {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
          <div
            className={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <ValidationRuleEditor
              collectionId={collectionId}
              rule={editingValidationRule}
              onSave={handleValidationRuleSave}
              onCancel={handleValidationRuleCancel}
              isSubmitting={
                createValidationRuleMutation.isPending || updateValidationRuleMutation.isPending
              }
            />
          </div>
        </div>
      )}

      {/* Picklist Dependency Delete Confirmation */}
      <ConfirmDialog
        open={deleteDependencyDialogOpen}
        title={t('picklistDependencies.deleteDependency')}
        message={t('picklistDependencies.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteDependencyConfirm}
        onCancel={handleDeleteDependencyCancel}
        variant="danger"
      />

      {/* Picklist Dependency Editor Modal */}
      {dependencyEditorOpen && (
        <PicklistDependencyEditor
          collectionId={collectionId}
          fields={sortedFields}
          dependency={editingDependency}
          onSave={handleDependencySave}
          onCancel={handleDependencyCancel}
          isSubmitting={saveDependencyMutation.isPending}
        />
      )}

      {/* Record Type Editor Modal */}
      {recordTypeEditorOpen && (
        <div
          className={styles.modalOverlay}
          role="presentation"
          onMouseDown={handleRecordTypeCancel}
        >
          {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
          <div
            className={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <RecordTypeEditor
              collectionId={collectionId}
              recordType={editingRecordType}
              onSave={handleRecordTypeSave}
              onCancel={handleRecordTypeCancel}
              isSubmitting={
                createRecordTypeMutation.isPending || updateRecordTypeMutation.isPending
              }
            />
          </div>
        </div>
      )}
    </div>
  )
}

export default CollectionDetailPage
