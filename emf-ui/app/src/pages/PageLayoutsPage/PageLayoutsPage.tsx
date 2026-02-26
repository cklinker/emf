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
import { useCollectionSummaries, type CollectionSummary } from '../../hooks/useCollectionSummaries'
import { cn } from '@/lib/utils'

import {
  LayoutEditorProvider,
  useLayoutEditor,
  LayoutToolbar,
  FieldPalette,
  PropertyPanel,
  MobilePreview,
  RelatedListPanel,
} from './components'
import type {
  EditorSection,
  EditorFieldPlacement,
  EditorRelatedList,
  AvailableField,
} from './components/LayoutEditorContext'

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

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
  relationshipFieldId: string
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
    relationshipFieldId: r.relationshipFieldId,
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
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="layout-form-overlay"
      role="presentation"
    >
      <div
        className="max-h-[90vh] w-full max-w-[600px] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="layout-form-title"
        data-testid="layout-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="layout-form-title" className="m-0 text-xl font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="cursor-pointer rounded border-none bg-transparent p-2 text-2xl leading-none text-muted-foreground transition-all duration-200 hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="layout-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="layout-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="layout-name"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.name && errors.name && 'border-destructive'
                )}
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
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="layout-description" className="text-sm font-medium text-foreground">
                Description
              </label>
              <textarea
                id="layout-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3.5 py-2.5 font-[inherit] text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter layout description"
                disabled={isSubmitting}
                rows={3}
                data-testid="layout-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="layout-type" className="text-sm font-medium text-foreground">
                Layout Type
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="layout-type"
                className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
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

            <div className="flex flex-col gap-2">
              <label htmlFor="layout-collection-id" className="text-sm font-medium text-foreground">
                Collection
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="layout-collection-id"
                className={cn(
                  'rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.collectionId && errors.collectionId && 'border-destructive'
                )}
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
                <span className="text-xs text-destructive" role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="layout-is-default"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.isDefault}
                onChange={(e) => handleChange('isDefault', e.target.checked)}
                disabled={isSubmitting}
                data-testid="layout-is-default-input"
              />
              <label htmlFor="layout-is-default" className="text-sm font-medium text-foreground">
                Default Layout
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <button
                type="button"
                className="cursor-pointer rounded-md border border-border bg-background px-5 py-2.5 text-sm font-medium text-foreground transition-all duration-200 hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="layout-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="cursor-pointer rounded-md border-none bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
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

  // Fetch layout detail with all children in a single request using
  // ?include=layout-sections,layout-fields,layout-related-lists.
  // The backend resolves layout-fields transitively via layout-sections.
  const { data: layoutDetail, isLoading: isLoadingLayout } = useQuery({
    queryKey: ['pageLayout', layoutId],
    queryFn: async () => {
      type JsonApiResource = {
        type: string
        id: string
        attributes: Record<string, unknown>
        relationships?: Record<string, { data?: { type: string; id: string } | null }>
      }
      const raw = await apiClient.get<{
        data: JsonApiResource
        included?: JsonApiResource[]
      }>(`/api/page-layouts/${layoutId}?include=layout-sections,layout-fields,layout-related-lists`)

      // Helper: flatten a JSON:API resource into a flat object by merging
      // attributes and extracting relationship IDs (rel.data.id → key).
      const flatten = (r: JsonApiResource): Record<string, unknown> => {
        const obj: Record<string, unknown> = { id: r.id, ...r.attributes }
        if (r.relationships) {
          for (const [key, rel] of Object.entries(r.relationships)) {
            obj[key] = rel?.data?.id ?? null
          }
        }
        return obj
      }

      // Build the layout from the primary data
      const flatLayout = flatten(raw.data)
      const layout: PageLayoutDetail = {
        ...(flatLayout as unknown as Omit<PageLayoutDetail, 'sections' | 'relatedLists'>),
        sections: [],
        relatedLists: [],
      }

      if (!raw.included || raw.included.length === 0) {
        return layout
      }

      // Extract sections, field placements, and related lists from included
      const sectionResources = raw.included
        .filter((r) => r.type === 'layout-sections')
        .map((r) => flatten(r) as unknown as ApiSection & { sortOrder: number })
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

      const fieldResources = raw.included
        .filter((r) => r.type === 'layout-fields')
        .map(
          (r) =>
            flatten(r) as unknown as ApiFieldPlacement & {
              sectionId: string
              sortOrder: number
            }
        )
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

      const relatedListResources = raw.included
        .filter((r) => r.type === 'layout-related-lists')
        .map((r) => flatten(r) as unknown as ApiRelatedList & { sortOrder: number })
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

      // Group field placements by sectionId
      const fieldsBySection = new Map<string, ApiFieldPlacement[]>()
      for (const fp of fieldResources) {
        const list = fieldsBySection.get(fp.sectionId) ?? []
        list.push(fp)
        fieldsBySection.set(fp.sectionId, list)
      }

      // Assemble sections with their fields
      layout.sections = sectionResources.map((s) => ({
        ...s,
        fields: fieldsBySection.get(s.id) ?? [],
      }))
      layout.relatedLists = relatedListResources

      return layout
    },
  })

  // Fetch collection fields when we know the collectionId.
  // Fields are separate records fetched via ?include=fields.
  const collectionId = layoutDetail?.collectionId ?? null
  const { data: collectionDetail } = useQuery({
    queryKey: ['collection-detail', collectionId],
    queryFn: async () => {
      type JsonApiResource = {
        type: string
        id: string
        attributes: Record<string, unknown>
        relationships?: Record<string, { data?: { type: string; id: string } | null }>
      }
      const raw = await apiClient.get<{
        data: JsonApiResource
        included?: JsonApiResource[]
      }>(`/api/collections/${collectionId}?include=fields`)

      const flattenRes = (r: JsonApiResource): Record<string, unknown> => {
        const obj: Record<string, unknown> = { id: r.id, ...r.attributes }
        if (r.relationships) {
          for (const [key, rel] of Object.entries(r.relationships)) {
            obj[key] = rel?.data?.id ?? null
          }
        }
        return obj
      }

      const flat = flattenRes(raw.data)
      const result: CollectionDetail = {
        id: flat.id as string,
        name: flat.name as string,
        displayName: flat.displayName as string,
        fields: [],
      }

      if (raw.included && raw.included.length > 0) {
        result.fields = raw.included
          .filter((r) => r.type === 'fields')
          .map((r) => flattenRes(r) as unknown as CollectionField)
      }

      return result
    },
    enabled: !!collectionId,
  })

  // Populate editor state when both layout and collection fields are loaded.
  // We need the collection fields to resolve fieldId UUIDs on each field
  // placement into human-readable fieldName / fieldDisplayName / fieldType.
  useEffect(() => {
    if (layoutDetail && collectionDetail?.fields) {
      // Build a lookup map: fieldId → CollectionField
      const fieldMap = new Map<string, CollectionField>()
      for (const f of collectionDetail.fields) {
        fieldMap.set(f.id, f)
      }

      // Map sections and resolve field names on each placement
      const sections = (layoutDetail.sections ?? []).map((s) => {
        const editorSection = apiSectionToEditor(s)
        editorSection.fields = editorSection.fields.map((fp) => {
          const field = fieldMap.get(fp.fieldId)
          if (field) {
            return {
              ...fp,
              fieldName: field.name,
              fieldDisplayName: field.displayName || field.name,
              fieldType: field.type,
            }
          }
          return fp
        })
        return editorSection
      })

      const relatedLists = (layoutDetail.relatedLists ?? []).map(apiRelatedListToEditor)
      setLayout(layoutDetail.collectionId, sections, relatedLists)

      // Also populate the available fields palette
      const fields: AvailableField[] = collectionDetail.fields.map((f) => ({
        id: f.id,
        name: f.name,
        displayName: f.displayName || f.name,
        type: f.type,
        required: f.required,
      }))
      setAvailableFields(fields)
    }
  }, [layoutDetail, collectionDetail, setLayout, setAvailableFields])

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
      return apiClient.putResource(`/api/page-layouts/${layoutId}`, payload)
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
      <div className="flex h-screen items-center justify-center">
        <LoadingSpinner size="large" label="Loading layout..." />
      </div>
    )
  }

  return (
    <div className="flex h-screen flex-col overflow-hidden" data-testid="layout-editor">
      <LayoutToolbar
        onBack={handleBack}
        layoutName={layoutDetail?.name ?? 'Layout'}
        onSave={handleSave}
        isSaving={saveMutation.isPending}
      />
      <div className="flex flex-1 min-h-0 overflow-hidden">
        <FieldPalette />
        <MobilePreview />
        <div className="flex w-[300px] flex-col overflow-y-auto border-l border-border bg-background max-md:w-full max-md:max-h-[300px] max-md:border-l-0 max-md:border-t">
          <PropertyPanel />
          <div className="border-t border-border">
            <RelatedListPanel />
          </div>
        </div>
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
    queryFn: () => apiClient.getList<PageLayoutSummary>(`/api/page-layouts`),
    enabled: viewMode === 'list',
  })

  const { summaries: collections } = useCollectionSummaries()

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
      apiClient.postResource<PageLayoutSummary>(
        `/api/page-layouts?collectionId=${encodeURIComponent(data.collectionId)}`,
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
      apiClient.putResource<PageLayoutSummary>(`/api/page-layouts/${id}`, data),
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
    mutationFn: (id: string) => apiClient.delete(`/api/page-layouts/${id}`),
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
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading layouts..." />
        </div>
      </div>
    )
  }

  // List mode: error
  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
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
    <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
      <header className="mb-8 flex items-center justify-between max-md:mb-4 max-md:flex-col max-md:items-start max-md:gap-4">
        <h1 className="m-0 text-3xl font-semibold text-foreground">Page Layouts</h1>
        <button
          type="button"
          className="cursor-pointer rounded-md border-none bg-primary px-6 py-3 text-base font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 max-md:w-full"
          onClick={handleCreate}
          aria-label="Create Layout"
          data-testid="add-layout-button"
        >
          Create Layout
        </button>
      </header>

      {layoutList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-background px-8 py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No page layouts found. Create one to get started.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-background max-md:overflow-x-auto">
          <table
            className="w-full border-collapse max-md:min-w-[700px]"
            role="grid"
            aria-label="Page Layouts"
            data-testid="page-layouts-table"
          >
            <thead className="bg-muted">
              <tr role="row">
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-sm font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Name
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-sm font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Collection
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-sm font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Type
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-sm font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Default
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-sm font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Created
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-sm font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {layoutList.map((layout, index) => (
                <tr
                  key={layout.id}
                  role="row"
                  className="border-b border-border transition-colors duration-150 last:border-b-0 hover:bg-muted/50"
                  data-testid={`layout-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-4 text-sm text-foreground">
                    {layout.name}
                  </td>
                  <td role="gridcell" className="px-4 py-4 text-sm text-foreground">
                    {getCollectionName(layout.collectionId)}
                  </td>
                  <td role="gridcell" className="px-4 py-4 text-sm">
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                      {layout.layoutType}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-4 text-sm">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        layout.isDefault
                          ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {layout.isDefault ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-4 text-sm text-foreground">
                    {formatDate(new Date(layout.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="px-4 py-4 text-right text-sm">
                    <div className="flex justify-end gap-2 max-md:flex-col">
                      <button
                        type="button"
                        className="cursor-pointer rounded-md border border-primary bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-all duration-200 hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 max-md:w-full"
                        onClick={() => handleDesign(layout)}
                        aria-label={`Design ${layout.name}`}
                        data-testid={`design-button-${index}`}
                      >
                        Design
                      </button>
                      <button
                        type="button"
                        className="cursor-pointer rounded-md border border-border bg-transparent px-4 py-2 text-sm font-medium text-primary transition-all duration-200 hover:border-primary hover:bg-muted focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 max-md:w-full"
                        onClick={() => handleEditMetadata(layout)}
                        aria-label={`Edit ${layout.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="cursor-pointer rounded-md border border-border bg-transparent px-4 py-2 text-sm font-medium text-destructive transition-all duration-200 hover:border-destructive hover:bg-destructive/10 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 max-md:w-full"
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
