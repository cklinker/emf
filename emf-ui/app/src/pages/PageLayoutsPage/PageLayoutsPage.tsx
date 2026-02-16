/**
 * PageLayoutsPage Component
 *
 * Visual page layout editor for creating and editing collection layouts.
 * Provides a list of all layouts with CRUD, and a WYSIWYG drag-and-drop
 * editor for composing layout sections, field placements, and related lists.
 *
 * Modes:
 * - list: Table of existing layouts with create/edit/delete
 * - editor: Three-panel drag-and-drop layout builder
 */

import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { getTenantId } from '../../hooks'
import {
  LayoutEditorProvider,
  useLayoutEditor,
  LayoutToolbar,
  FieldPalette,
  PropertyPanel,
  MobilePreview,
} from './components'
import type {
  EditorSection,
  EditorFieldPlacement,
  EditorRelatedList,
  AvailableField,
} from './components/LayoutEditorContext'
import styles from './PageLayoutsPage.module.css'

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

interface CollectionSummary {
  id: string
  name: string
  displayName: string
}

interface CollectionDetail {
  id: string
  name: string
  displayName: string
  fields: CollectionField[]
}

interface CollectionField {
  id: string
  name: string
  displayName: string
  type: string
  required: boolean
}

interface PageLayoutSummary {
  id: string
  name: string
  description: string | null
  layoutType: string
  collectionId: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

interface PageLayoutDetail {
  id: string
  name: string
  description: string | null
  layoutType: string
  collectionId: string
  isDefault: boolean
  sections: ApiSection[]
  relatedLists: ApiRelatedList[]
  createdAt: string
  updatedAt: string
}

interface ApiSection {
  id: string
  heading: string
  columns: number
  sortOrder: number
  collapsed: boolean
  style: string
  sectionType: string
  tabGroup?: string
  tabLabel?: string
  visibilityRule?: string
  fields: ApiFieldPlacement[]
}

interface ApiFieldPlacement {
  id: string
  fieldId: string
  fieldName?: string
  fieldType?: string
  fieldDisplayName?: string
  columnNumber: number
  columnSpan?: number
  sortOrder: number
  requiredOnLayout: boolean
  readOnlyOnLayout: boolean
  labelOverride?: string
  helpTextOverride?: string
  visibilityRule?: string
}

interface ApiRelatedList {
  id: string
  relatedCollectionId: string
  relationshipField: string
  displayColumns: string
  sortField?: string
  sortDirection: string
  rowLimit: number
  sortOrder: number
}

interface PageLayoutFormData {
  name: string
  description: string
  layoutType: string
  collectionId: string
  isDefault: boolean
}

interface FormErrors {
  name?: string
  description?: string
  collectionId?: string
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

export interface PageLayoutsPageProps {
  testId?: string
}

const LAYOUT_TYPES = ['DETAIL', 'EDIT', 'MINI', 'LIST'] as const

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

function validateForm(data: PageLayoutFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (!data.collectionId) {
    errors.collectionId = 'Collection is required'
  }
  return errors
}

// ---------------------------------------------------------------------------
// Mapping helpers: API → Editor state
// ---------------------------------------------------------------------------

function apiSectionToEditor(s: ApiSection): EditorSection {
  return {
    id: s.id,
    heading: s.heading,
    columns: s.columns,
    sortOrder: s.sortOrder,
    collapsed: s.collapsed,
    style: s.style?.toLowerCase() ?? 'default',
    sectionType: s.sectionType?.toLowerCase() ?? 'fields',
    tabGroup: s.tabGroup,
    tabLabel: s.tabLabel,
    visibilityRule: s.visibilityRule,
    fields: (s.fields ?? []).map(apiFieldToEditor),
  }
}

function apiFieldToEditor(f: ApiFieldPlacement): EditorFieldPlacement {
  return {
    id: f.id,
    fieldId: f.fieldId,
    fieldName: f.fieldName,
    fieldType: f.fieldType,
    fieldDisplayName: f.fieldDisplayName,
    columnNumber: f.columnNumber,
    columnSpan: f.columnSpan,
    sortOrder: f.sortOrder,
    requiredOnLayout: f.requiredOnLayout,
    readOnlyOnLayout: f.readOnlyOnLayout,
    labelOverride: f.labelOverride,
    helpTextOverride: f.helpTextOverride,
    visibilityRule: f.visibilityRule,
  }
}

function apiRelatedListToEditor(r: ApiRelatedList): EditorRelatedList {
  return {
    id: r.id,
    relatedCollectionId: r.relatedCollectionId,
    relationshipFieldId: r.relationshipField,
    displayColumns: r.displayColumns,
    sortField: r.sortField,
    sortDirection: r.sortDirection,
    rowLimit: r.rowLimit,
    sortOrder: r.sortOrder,
  }
}

// ---------------------------------------------------------------------------
// Mapping helpers: Editor state → API request
// ---------------------------------------------------------------------------

function editorSectionsToApi(sections: EditorSection[]): {
  heading: string
  columns: number
  sortOrder: number
  collapsed: boolean
  style: string
  sectionType: string
  tabGroup?: string
  tabLabel?: string
  visibilityRule?: string
  fields: {
    fieldId: string
    columnNumber: number
    columnSpan?: number
    sortOrder: number
    requiredOnLayout: boolean
    readOnlyOnLayout: boolean
    labelOverride?: string
    helpTextOverride?: string
    visibilityRule?: string
  }[]
}[] {
  return sections
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((s) => ({
      heading: s.heading,
      columns: s.columns,
      sortOrder: s.sortOrder,
      collapsed: s.collapsed,
      style: s.style?.toUpperCase() ?? 'DEFAULT',
      sectionType: s.sectionType?.toUpperCase() ?? 'STANDARD',
      tabGroup: s.tabGroup || undefined,
      tabLabel: s.tabLabel || undefined,
      visibilityRule: s.visibilityRule || undefined,
      fields: s.fields
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((f) => ({
          fieldId: f.fieldId,
          columnNumber: f.columnNumber,
          columnSpan: f.columnSpan && f.columnSpan > 1 ? f.columnSpan : undefined,
          sortOrder: f.sortOrder,
          requiredOnLayout: f.requiredOnLayout,
          readOnlyOnLayout: f.readOnlyOnLayout,
          labelOverride: f.labelOverride || undefined,
          helpTextOverride: f.helpTextOverride || undefined,
          visibilityRule: f.visibilityRule || undefined,
        })),
    }))
}

function editorRelatedListsToApi(lists: EditorRelatedList[]): {
  relatedCollectionId: string
  relationshipFieldId: string
  displayColumns: string
  sortField?: string
  sortDirection: string
  rowLimit: number
  sortOrder: number
}[] {
  return lists
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((r) => ({
      relatedCollectionId: r.relatedCollectionId,
      relationshipFieldId: r.relationshipFieldId,
      displayColumns: r.displayColumns,
      sortField: r.sortField || undefined,
      sortDirection: r.sortDirection,
      rowLimit: r.rowLimit,
      sortOrder: r.sortOrder,
    }))
}

// ---------------------------------------------------------------------------
// PageLayoutForm (modal for create/edit metadata)
// ---------------------------------------------------------------------------

interface PageLayoutFormProps {
  layout?: PageLayoutSummary
  collections: CollectionSummary[]
  onSubmit: (data: PageLayoutFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function PageLayoutForm({
  layout,
  collections,
  onSubmit,
  onCancel,
  isSubmitting,
}: PageLayoutFormProps): React.ReactElement {
  const isEditing = !!layout
  const [formData, setFormData] = useState<PageLayoutFormData>({
    name: layout?.name ?? '',
    description: layout?.description ?? '',
    layoutType: layout?.layoutType ?? 'DETAIL',
    collectionId: layout?.collectionId ?? '',
    isDefault: layout?.isDefault ?? false,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof PageLayoutFormData, value: string | boolean) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (typeof value === 'string' && errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof FormErrors) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData)
      if (validationErrors[field]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }))
      }
    },
    [formData]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData)
      setErrors(validationErrors)
      setTouched({ name: true, description: true, collectionId: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? 'Edit Layout' : 'Create Layout'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="layout-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="layout-form-title"
        data-testid="layout-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="layout-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="layout-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="layout-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="layout-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter layout name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="layout-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="layout-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="layout-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter layout description"
                disabled={isSubmitting}
                rows={3}
                data-testid="layout-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="layout-type" className={styles.formLabel}>
                Layout Type
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="layout-type"
                className={styles.formInput}
                value={formData.layoutType}
                onChange={(e) => handleChange('layoutType', e.target.value)}
                disabled={isSubmitting}
                data-testid="layout-type-select"
              >
                {LAYOUT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="layout-collection-id" className={styles.formLabel}>
                Collection
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="layout-collection-id"
                className={`${styles.formInput} ${touched.collectionId && errors.collectionId ? styles.hasError : ''}`}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                aria-required="true"
                aria-invalid={touched.collectionId && !!errors.collectionId}
                disabled={isSubmitting || isEditing}
                data-testid="layout-collection-id-input"
              >
                <option value="">Select a collection</option>
                {collections.map((col) => (
                  <option key={col.id} value={col.id}>
                    {col.displayName || col.name}
                  </option>
                ))}
              </select>
              {touched.collectionId && errors.collectionId && (
                <span className={styles.formError} role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="layout-is-default"
                type="checkbox"
                checked={formData.isDefault}
                onChange={(e) => handleChange('isDefault', e.target.checked)}
                disabled={isSubmitting}
                data-testid="layout-is-default-input"
              />
              <label htmlFor="layout-is-default" className={styles.formLabel}>
                Default Layout
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="layout-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="layout-form-submit"
              >
                {isSubmitting ? 'Saving...' : 'Save'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// LayoutEditorView (editor mode, wrapped in LayoutEditorProvider)
// ---------------------------------------------------------------------------

interface LayoutEditorViewProps {
  layoutId: string
  onBack: () => void
}

function LayoutEditorViewInner({ layoutId, onBack }: LayoutEditorViewProps): React.ReactElement {
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const { state, setLayout, setAvailableFields, markSaved } = useLayoutEditor()

  // Fetch layout detail
  const { data: layoutDetail, isLoading: isLoadingLayout } = useQuery({
    queryKey: ['pageLayout', layoutId],
    queryFn: () => apiClient.get<PageLayoutDetail>(`/control/layouts/${layoutId}`),
  })

  // Fetch collection fields when we know the collectionId
  const collectionId = layoutDetail?.collectionId ?? null
  const { data: collectionDetail } = useQuery({
    queryKey: ['collection-detail', collectionId],
    queryFn: () => apiClient.get<CollectionDetail>(`/control/collections/${collectionId}`),
    enabled: !!collectionId,
  })

  // Populate editor state when layout loads
  useEffect(() => {
    if (layoutDetail) {
      const sections = (layoutDetail.sections ?? []).map(apiSectionToEditor)
      const relatedLists = (layoutDetail.relatedLists ?? []).map(apiRelatedListToEditor)
      setLayout(layoutDetail.collectionId, sections, relatedLists)
    }
  }, [layoutDetail, setLayout])

  // Populate available fields when collection loads
  useEffect(() => {
    if (collectionDetail?.fields) {
      const fields: AvailableField[] = collectionDetail.fields.map((f) => ({
        id: f.id,
        name: f.name,
        displayName: f.displayName || f.name,
        type: f.type,
        required: f.required,
      }))
      setAvailableFields(fields)
    }
  }, [collectionDetail, setAvailableFields])

  // Save mutation
  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        name: layoutDetail!.name,
        description: layoutDetail!.description ?? '',
        layoutType: layoutDetail!.layoutType,
        isDefault: layoutDetail!.isDefault,
        sections: editorSectionsToApi(state.sections),
        relatedLists: editorRelatedListsToApi(state.relatedLists),
      }
      return apiClient.put(`/control/layouts/${layoutId}`, payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      queryClient.invalidateQueries({ queryKey: ['pageLayout', layoutId] })
      markSaved()
      showToast('Layout saved successfully', 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to save layout', 'error')
    },
  })

  const handleSave = useCallback(() => {
    saveMutation.mutate()
  }, [saveMutation])

  const handleBack = useCallback(() => {
    if (state.isDirty) {
      const confirmed = window.confirm('You have unsaved changes. Are you sure you want to leave?')
      if (!confirmed) return
    }
    onBack()
  }, [state.isDirty, onBack])

  if (isLoadingLayout) {
    return (
      <div className={styles.editorLoading}>
        <LoadingSpinner size="large" label="Loading layout..." />
      </div>
    )
  }

  return (
    <div className={styles.editorContainer} data-testid="layout-editor">
      <LayoutToolbar
        onBack={handleBack}
        layoutName={layoutDetail?.name ?? 'Layout'}
        onSave={handleSave}
        isSaving={saveMutation.isPending}
      />
      <div className={styles.editorBody}>
        <FieldPalette />
        <MobilePreview />
        <PropertyPanel />
      </div>
    </div>
  )
}

function LayoutEditorView({ layoutId, onBack }: LayoutEditorViewProps): React.ReactElement {
  return (
    <LayoutEditorProvider>
      <LayoutEditorViewInner layoutId={layoutId} onBack={onBack} />
    </LayoutEditorProvider>
  )
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

export function PageLayoutsPage({
  testId = 'page-layouts-page',
}: PageLayoutsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // View mode: list or editor
  const [viewMode, setViewMode] = useState<'list' | 'editor'>('list')
  const [editingLayoutId, setEditingLayoutId] = useState<string | null>(null)

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingLayout, setEditingLayout] = useState<PageLayoutSummary | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [layoutToDelete, setLayoutToDelete] = useState<PageLayoutSummary | null>(null)

  const {
    data: layouts,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['pageLayouts'],
    queryFn: () => apiClient.get<PageLayoutSummary[]>(`/control/layouts?tenantId=${getTenantId()}`),
    enabled: viewMode === 'list',
  })

  const { data: collectionsData } = useQuery({
    queryKey: ['collections-for-layouts'],
    queryFn: () =>
      apiClient.get<{ content: CollectionSummary[] }>('/control/collections?size=1000'),
  })

  const collections = useMemo<CollectionSummary[]>(
    () => collectionsData?.content ?? [],
    [collectionsData]
  )

  const collectionMap = useMemo(() => {
    const map = new Map<string, CollectionSummary>()
    for (const col of collections) {
      map.set(col.id, col)
    }
    return map
  }, [collections])

  const layoutList = layouts ?? []

  const createMutation = useMutation({
    mutationFn: (data: PageLayoutFormData) =>
      apiClient.post<PageLayoutSummary>(
        `/control/layouts?tenantId=${getTenantId()}&collectionId=${encodeURIComponent(data.collectionId)}`,
        data
      ),
    onSuccess: (newLayout) => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout created successfully', 'success')
      handleCloseForm()
      // Open the editor for the new layout
      setEditingLayoutId(newLayout.id)
      setViewMode('editor')
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: PageLayoutFormData }) =>
      apiClient.put<PageLayoutSummary>(`/control/layouts/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/layouts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setLayoutToDelete(null)
      if (editingLayoutId === layoutToDelete?.id) {
        setViewMode('list')
        setEditingLayoutId(null)
      }
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingLayout(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEditMetadata = useCallback((layout: PageLayoutSummary) => {
    setEditingLayout(layout)
    setIsFormOpen(true)
  }, [])

  const handleDesign = useCallback((layout: PageLayoutSummary) => {
    setEditingLayoutId(layout.id)
    setViewMode('editor')
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingLayout(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: PageLayoutFormData) => {
      if (editingLayout) {
        updateMutation.mutate({ id: editingLayout.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingLayout, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((layout: PageLayoutSummary) => {
    setLayoutToDelete(layout)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (layoutToDelete) {
      deleteMutation.mutate(layoutToDelete.id)
    }
  }, [layoutToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setLayoutToDelete(null)
  }, [])

  const handleBackFromEditor = useCallback(() => {
    setViewMode('list')
    setEditingLayoutId(null)
  }, [])

  const getCollectionName = useCallback(
    (collectionId: string): string => {
      const col = collectionMap.get(collectionId)
      return col ? col.displayName || col.name : collectionId
    },
    [collectionMap]
  )

  // Editor mode
  if (viewMode === 'editor' && editingLayoutId) {
    return <LayoutEditorView layoutId={editingLayoutId} onBack={handleBackFromEditor} />
  }

  // List mode: loading
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading layouts..." />
        </div>
      </div>
    )
  }

  // List mode: error
  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  // List mode
  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>Page Layouts</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Layout"
          data-testid="add-layout-button"
        >
          Create Layout
        </button>
      </header>

      {layoutList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No page layouts found. Create one to get started.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Page Layouts"
            data-testid="page-layouts-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Collection
                </th>
                <th role="columnheader" scope="col">
                  Type
                </th>
                <th role="columnheader" scope="col">
                  Default
                </th>
                <th role="columnheader" scope="col">
                  Created
                </th>
                <th role="columnheader" scope="col">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {layoutList.map((layout, index) => (
                <tr
                  key={layout.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`layout-row-${index}`}
                >
                  <td role="gridcell">{layout.name}</td>
                  <td role="gridcell">{getCollectionName(layout.collectionId)}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{layout.layoutType}</span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${layout.isDefault ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {layout.isDefault ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(layout.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.designButton}`}
                        onClick={() => handleDesign(layout)}
                        aria-label={`Design ${layout.name}`}
                        data-testid={`design-button-${index}`}
                      >
                        Design
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEditMetadata(layout)}
                        aria-label={`Edit ${layout.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(layout)}
                        aria-label={`Delete ${layout.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <PageLayoutForm
          layout={editingLayout}
          collections={collections}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Layout"
        message="Are you sure you want to delete this layout? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default PageLayoutsPage
